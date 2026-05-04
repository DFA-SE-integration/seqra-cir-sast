#include "cir-tac/AliasSerializer.h"

#include <map>
#include <optional>
#include <utility>
#include <vector>

#include "seadsa/AllocWrapInfo.hh"
#include "seadsa/DsaLibFuncInfo.hh"
#include "seadsa/Graph.hh"
#include "seadsa/SeaDsaAliasAnalysis.hh"

#include "llvm/Analysis/TargetLibraryInfo.h"
#include "llvm/IR/Argument.h"
#include "llvm/IR/Function.h"
#include "llvm/IR/Instructions.h"
#include "llvm/IR/Metadata.h"
#include "llvm/IR/Module.h"

#include "mlir/IR/BuiltinOps.h"
#include "mlir/IR/Operation.h"
#include "mlir/IR/Value.h"

using namespace protocir;

bool AliasSerializer::cirOpProducesPointerResult(mlir::Operation *op) {
  return llvm::any_of(op->getResults(), [](mlir::Value v) {
    return mlir::isa<cir::PointerType>(v.getType());
  });
}

void AliasSerializer::buildFunctionContexts(
    mlir::ModuleOp module, llvm::StringMap<FunctionAliasContext> &out) {
  for (mlir::Operation &top : module.getBodyRegion().getOps()) {
    auto func = llvm::dyn_cast<cir::FuncOp>(top);
    if (!func)
      continue;

    FunctionAliasContext ctx;
    for (auto &block : func.getFunctionBody()) {
      ctx.blockCache.getMLIRBlockID(&block);
      for (mlir::Operation &inst : block) {
        ctx.opCache.getMLIROpID(&inst);
      }
    }
    mlir::Block &entry = func.getFunctionBody().front();
    ctx.entryBlock = &entry;
    ctx.entryBlockId = ctx.blockCache.getMLIRBlockID(&entry);

    uint64_t li = 0;
    for (auto &block : func.getFunctionBody()) {
      for (mlir::Operation &inst : block) {
        if (!cirOpProducesPointerResult(&inst))
          continue;
        ctx.localIndexToCirOp[li] = &inst;
        ++li;
      }
    }
    out[func.getSymName().str()] = std::move(ctx);
  }
}

static std::optional<uint64_t>
seqraLocalIndexFromInstruction(const llvm::Instruction *I) {
  if (!I)
    return std::nullopt;
  llvm::MDNode *md = I->getMetadata("seqra.local");
  if (!md || md->getNumOperands() < 2)
    return std::nullopt;
  if (auto *ci =
          llvm::mdconst::dyn_extract<llvm::ConstantInt>(md->getOperand(1)))
    return ci->getZExtValue();
  return std::nullopt;
}

MLIRValue AliasSerializer::makeOpResultValue(const OpCache &opCache,
                                             TypeCache &typeCache,
                                             mlir::OpResult result) {
  MLIRValue pValue;
  *pValue.mutable_type() = typeCache.getMLIRTypeID(result.getType());
  MLIROpResult opResult;
  *opResult.mutable_owner() =
      const_cast<OpCache &>(opCache).getMLIROpID(result.getOwner());
  opResult.set_result_number(result.getResultNumber());
  *pValue.mutable_op_result() = opResult;
  return pValue;
}

MLIRValue
AliasSerializer::makeBlockArgValue(const BlockCache &blockCache,
                                   TypeCache &typeCache,
                                   mlir::BlockArgument arg) {
  MLIRValue pValue;
  *pValue.mutable_type() = typeCache.getMLIRTypeID(arg.getType());
  MLIRBlockArgument blockArg;
  *blockArg.mutable_owner() =
      const_cast<BlockCache &>(blockCache).getMLIRBlockID(arg.getOwner());
  blockArg.set_arg_number(arg.getArgNumber());
  *pValue.mutable_block_argument() = blockArg;
  return pValue;
}

CIRModuleAliasData AliasSerializer::serializeModule(
    llvm::Module &llvmModule, TypeCache &typeCache,
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
    std::map<std::pair<const Node *, unsigned>, std::vector<const llvm::Value *>>
        buckets;

    auto addToBucket = [&](const llvm::Value *V, const seadsa::Cell &cell) {
      if (cell.isNull() || !cell.getNode())
        return;
      auto key = std::make_pair(cell.getNode(), cell.getOffset());
      buckets[key].push_back(V);
    };

    // Iterating `G->scalar_begin/end` is not enough: `Graph::mkCell`
    // calls `stripPointerCasts` on its first argument, so several SSA
    // values are merged into a single `m_values` entry. Walk the LLVM IR
    // ourselves and ask sea-dsa for each ptr-typed value's cell -- this
    // mirrors what `--sea-dsa-aa-eval` does for its pairwise alias()
    // queries and matches what we tagged with `!seqra.local` during
    // lowering. CIR `cir.cast bitcast` ops that get lowered to a no-op
    // (typical in opaque-pointer LLVM) do not appear here, but that is
    // fine: sea-dsa already treats them as transparent and seqra strips
    // them via `accessPathBaseOrNull` before consulting the alias data.
    for (llvm::Argument &A : F.args()) {
      if (!A.getType()->isPointerTy())
        continue;
      if (!G->hasCell(A))
        continue;
      addToBucket(&A, G->getCell(A));
    }
    for (llvm::BasicBlock &BB : F) {
      for (llvm::Instruction &I : BB) {
        if (!I.getType()->isPointerTy())
          continue;
        if (!seqraLocalIndexFromInstruction(&I))
          continue;
        if (!G->hasCell(I))
          continue;
        addToBucket(&I, G->getCell(I));
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
      for (const llvm::Value *V : vals) {
        if (const auto *I = llvm::dyn_cast<llvm::Instruction>(V)) {
          if (auto idx = seqraLocalIndexFromInstruction(I)) {
            auto opIt = fctx.localIndexToCirOp.find(*idx);
            if (opIt == fctx.localIndexToCirOp.end())
              continue;
            mlir::Value pv = [&]() -> mlir::Value {
              mlir::Operation *op = opIt->second;
              if (!op)
                return {};
              for (mlir::Value r : op->getResults()) {
                if (mlir::isa<cir::PointerType>(r.getType()))
                  return r;
              }
              return {};
            }();
            auto orRes = mlir::dyn_cast<mlir::OpResult>(pv);
            if (!orRes)
              continue;
            *group.add_members() =
                makeOpResultValue(fctx.opCache, typeCache, orRes);
          }
        } else if (const auto *A = llvm::dyn_cast<llvm::Argument>(V)) {
          if (!fctx.entryBlock ||
              A->getArgNo() >= fctx.entryBlock->getNumArguments())
            continue;
          mlir::BlockArgument barg = fctx.entryBlock->getArgument(A->getArgNo());
          *group.add_members() =
              makeBlockArgValue(fctx.blockCache, typeCache, barg);
        }
      }

      if (group.members_size() >= 2)
        *fnData.add_alias_groups() = group;
    }

    if (fnData.alias_groups_size() > 0)
      *out.add_functions() = fnData;
  }

  return out;
}
