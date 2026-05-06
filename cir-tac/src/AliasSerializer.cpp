#include "cir-tac/AliasSerializer.h"

#include <map>
#include <optional>
#include <utility>
#include <vector>

#include "seadsa/AllocWrapInfo.hh"
#include "seadsa/DsaLibFuncInfo.hh"
#include "seadsa/Graph.hh"
#include "seadsa/SeaDsaAliasAnalysis.hh"

#include "llvm/ADT/STLExtras.h"
#include "llvm/Analysis/TargetLibraryInfo.h"
#include "llvm/IR/Argument.h"
#include "llvm/IR/Function.h"
#include "llvm/IR/Instructions.h"
#include "llvm/IR/Metadata.h"
#include "llvm/IR/Module.h"

#include "mlir/IR/Block.h"
#include "mlir/IR/Value.h"

using namespace protocir;

namespace {

static constexpr llvm::StringLiteral kSeqraOpIdAttr = "cir.seqra.op_id";

/// Operand 0: i64 op id (matches serialized MLIROpID / cir.seqra.op_id).
std::optional<uint64_t>
seqraOpIdFromInstruction(const llvm::Instruction *I) {
  if (!I)
    return std::nullopt;
  llvm::MDNode *md = I->getMetadata("seqra.op");
  if (!md || md->getNumOperands() < 1)
    return std::nullopt;
  if (auto *ci =
          llvm::mdconst::dyn_extract<llvm::ConstantInt>(md->getOperand(0)))
    return ci->getZExtValue();
  return std::nullopt;
}

MLIRValue makeOpResultValue(MLIROpID opId, uint64_t resultNumber,
                            MLIRTypeID typeId) {
  MLIRValue v;
  *v.mutable_type() = std::move(typeId);
  MLIROpResult opResult;
  *opResult.mutable_owner() = std::move(opId);
  opResult.set_result_number(resultNumber);
  *v.mutable_op_result() = opResult;
  return v;
}

MLIRValue makeBlockArgValue(MLIRBlockID blockId, uint64_t argNumber,
                            MLIRTypeID typeId) {
  MLIRValue v;
  *v.mutable_type() = std::move(typeId);
  MLIRBlockArgument ba;
  *ba.mutable_owner() = std::move(blockId);
  ba.set_arg_number(argNumber);
  *v.mutable_block_argument() = ba;
  return v;
}

} // namespace

void AliasSerializer::stampAndBuildContext(cir::FuncOp func, OpCache &opCache,
                                          BlockCache &blockCache,
                                          TypeCache &typeCache,
                                          FunctionAliasContext &out) {
  mlir::Builder b(func.getContext());
  func.walk([&](mlir::Operation *op) {
    if (op->getNumResults() == 0)
      return;
    if (!opCache.contains(op))
      return;
    MLIROpID oid = opCache.getMLIROpID(op);
    uint64_t rawId = oid.id();
    op->setAttr(kSeqraOpIdAttr, b.getI64IntegerAttr(static_cast<int64_t>(rawId)));
    out.opIdToResult0[rawId] =
        makeOpResultValue(std::move(oid), 0u,
                          typeCache.getMLIRTypeID(op->getResult(0).getType()));
  });

  if (!func.getFunctionBody().empty()) {
    mlir::Block &entry = func.getFunctionBody().front();
    MLIRBlockID blockId = blockCache.getMLIRBlockID(&entry);
    for (mlir::BlockArgument arg : entry.getArguments()) {
      out.entryBlockArgs.push_back(makeBlockArgValue(
          blockId, arg.getArgNumber(),
          typeCache.getMLIRTypeID(arg.getType())));
    }
  }
}

CIRModuleAliasData AliasSerializer::serializeModule(
    llvm::Module &llvmModule,
    const llvm::StringMap<FunctionAliasContext> &funcContexts) {
  CIRModuleAliasData out;
  *out.mutable_module_id() = moduleID;

  llvm::TargetLibraryInfoWrapperPass tliWrapper;
  seadsa::AllocWrapInfo awi(&tliWrapper);
  seadsa::DsaLibFuncInfo dlfi;
  dlfi.initialize(llvmModule);

  seadsa::SeaDsaAAResult aa(tliWrapper, awi, dlfi);
  aa.runOnModule(llvmModule);

  for (llvm::Function &F : llvmModule) {
    if (F.isDeclaration())
      continue;
    auto ctxIt = funcContexts.find(F.getName());
    if (ctxIt == funcContexts.end())
      continue;
    const FunctionAliasContext &fctx = ctxIt->second;

    seadsa::Graph *G = aa.getGraph(F);
    if (!G)
      continue;
    G->compress();

    using Node = seadsa::Node;
    std::map<std::pair<const Node *, unsigned>, std::vector<MLIRValue>> buckets;

    auto addToBucket = [&](MLIRValue v, const seadsa::Cell &cell) {
      if (cell.isNull() || !cell.getNode())
        return;
      auto key = std::make_pair(cell.getNode(), cell.getOffset());
      buckets[key].push_back(std::move(v));
    };

    for (llvm::Argument &A : F.args()) {
      if (!A.getType()->isPointerTy())
        continue;
      if (A.getArgNo() >= fctx.entryBlockArgs.size())
        continue;
      if (!G->hasCell(A))
        continue;
      addToBucket(fctx.entryBlockArgs[A.getArgNo()], G->getCell(A));
    }
    for (llvm::BasicBlock &BB : F) {
      for (llvm::Instruction &I : BB) {
        if (!I.getType()->isPointerTy())
          continue;
        auto oid = seqraOpIdFromInstruction(&I);
        if (!oid)
          continue;
        auto it = fctx.opIdToResult0.find(*oid);
        if (it == fctx.opIdToResult0.end())
          continue;
        if (!G->hasCell(I))
          continue;
        addToBucket(it->second, G->getCell(I));
      }
    }

    CIRFunctionAliasData fnData;
    CIRFunctionID fid;
    *fid.mutable_module_id() = moduleID;
    *fid.mutable_id() = F.getName().str();
    *fnData.mutable_function() = fid;

    for (const auto &be : buckets) {
      const auto &vals = be.second;
      if (vals.size() < 2)
        continue;
      CIRAliasGroup group;
      for (const MLIRValue &v : vals) {
        *group.add_members() = v;
      }
      *fnData.add_alias_groups() = std::move(group);
    }

    if (fnData.alias_groups_size() > 0)
      *out.add_functions() = std::move(fnData);
  }

  return out;
}
