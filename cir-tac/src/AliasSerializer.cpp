#include "cir-tac/AliasSerializer.h"

#include <clang/CIR/Dialect/IR/CIRDialect.h>
#include <clang/CIR/Dialect/IR/CIRTypes.h>
#include <llvm/ADT/DenseMap.h>
#include <llvm/Support/Casting.h>
#include <mlir/IR/Block.h>
#include <mlir/IR/Operation.h>
#include <mlir/IR/Value.h>

#include <unordered_map>
#include <vector>

using namespace protocir;

// ---- Union-Find (path-compressed, map-backed) ----

mlir::Value AliasSerializer::find(ValueMap &parent, mlir::Value v) {
  auto it = parent.find(v);
  if (it == parent.end() || it->second == v) return v;
  // Path compression
  it->second = find(parent, it->second);
  return it->second;
}

void AliasSerializer::unite(ValueMap &parent, mlir::Value a, mlir::Value b) {
  a = find(parent, a);
  b = find(parent, b);
  if (a == b) return;
  parent[a] = b;
}

// ---- Type helpers ----

bool AliasSerializer::isPointerType(mlir::Type type) {
  return mlir::isa<cir::PointerType>(type);
}

// ---- Value serialization helpers ----

MLIRValue AliasSerializer::makeOpResultValue(const OpCache &opCache,
                                              TypeCache &typeCache,
                                              mlir::OpResult result) {
  MLIRValue pValue;
  *pValue.mutable_type() = typeCache.getMLIRTypeID(result.getType());
  MLIROpResult opResult;
  *opResult.mutable_owner() = const_cast<OpCache &>(opCache).getMLIROpID(result.getOwner());
  opResult.set_result_number(result.getResultNumber());
  *pValue.mutable_op_result() = opResult;
  return pValue;
}

MLIRValue AliasSerializer::makeBlockArgValue(const BlockCache &blockCache,
                                              TypeCache &typeCache,
                                              mlir::BlockArgument arg) {
  MLIRValue pValue;
  *pValue.mutable_type() = typeCache.getMLIRTypeID(arg.getType());
  MLIRBlockArgument blockArg;
  *blockArg.mutable_owner() = const_cast<BlockCache &>(blockCache).getMLIRBlockID(arg.getOwner());
  blockArg.set_arg_number(arg.getArgNumber());
  *pValue.mutable_block_argument() = blockArg;
  return pValue;
}

// ---- Main analysis ----

CIRFunctionAliasData AliasSerializer::serializeFunction(cir::FuncOp func,
                                                         const OpCache &opCache,
                                                         const BlockCache &blockCache,
                                                         TypeCache &typeCache) {
  CIRFunctionAliasData result;
  CIRFunctionID funcID;
  *funcID.mutable_module_id() = moduleID;
  *funcID.mutable_id() = func.getSymName().str();
  *result.mutable_function() = funcID;

  ValueMap parent;

  // Collect all pointer-typed values and register them in union-find
  auto registerValue = [&](mlir::Value v) {
    if (isPointerType(v.getType())) {
      if (!parent.count(v)) parent[v] = v;
    }
  };

  for (auto &block : func.getFunctionBody()) {
    // Block arguments (function parameters in the entry block, or block params)
    for (auto arg : block.getArguments()) {
      registerValue(arg);
    }
    // Op results
    for (auto &op : block) {
      for (auto result : op.getResults()) {
        registerValue(result);
      }

      // Detect alias relationships:
      // cir.cast with pointer input and pointer output → result aliases operand
      if (auto castOp = llvm::dyn_cast<cir::CastOp>(op)) {
        auto src = castOp.getSrc();
        auto dst = castOp.getResult();
        if (isPointerType(src.getType()) && isPointerType(dst.getType())) {
          // Ensure both are in the parent map
          if (!parent.count(src)) parent[src] = src;
          unite(parent, src, dst);
        }
      }
    }
  }

  // Build groups: representative → list of members
  llvm::DenseMap<mlir::Value, std::vector<mlir::Value>> groups;
  for (auto &kv : parent) {
    mlir::Value rep = find(parent, kv.first);
    groups[rep].push_back(kv.first);
  }

  // Emit groups with >= 2 members as CIRAliasGroup
  for (auto &kv : groups) {
    if (kv.second.size() < 2) continue;

    CIRAliasGroup group;
    for (mlir::Value v : kv.second) {
      MLIRValue pValue;
      if (auto blockArg = mlir::dyn_cast<mlir::BlockArgument>(v)) {
        pValue = makeBlockArgValue(blockCache, typeCache, blockArg);
      } else if (auto opResult = mlir::dyn_cast<mlir::OpResult>(v)) {
        pValue = makeOpResultValue(opCache, typeCache, opResult);
      } else {
        continue;
      }
      *group.add_members() = pValue;
    }
    if (group.members_size() >= 2) {
      *result.add_alias_groups() = group;
    }
  }

  return result;
}
