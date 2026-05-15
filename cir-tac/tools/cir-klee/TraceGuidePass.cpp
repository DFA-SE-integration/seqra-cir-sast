#include "TraceGuidePass.h"
#include "TracePathUtils.h"
#include "TraceUtil.h"

#include "proto/trace.pb.h"

#include "llvm/ADT/DenseMap.h"
#include "llvm/ADT/DenseSet.h"
#include "llvm/ADT/SmallVector.h"
#include "llvm/IR/BasicBlock.h"
#include "llvm/IR/Function.h"
#include "llvm/IR/IRBuilder.h"
#include "llvm/IR/Instructions.h"
#include "llvm/IR/IntrinsicInst.h"
#include "llvm/IR/Module.h"
#include "llvm/Support/raw_ostream.h"

#include <string>
#include <vector>

using namespace llvm;

namespace {

unsigned cfgSuccessorCount(const Instruction *Term) {
  if (!Term || !Term->isTerminator())
    return 0;
  if (isa<UnreachableInst>(Term) || isa<ReturnInst>(Term) ||
      isa<ResumeInst>(Term))
    return 0;
  return Term->getNumSuccessors();
}

void insertSilentExitAfterFirstMeaningful(BasicBlock *BB, Module &M) {
  LLVMContext &Ctx = M.getContext();
  FunctionCallee KSilentExit = seqra_trace::getKleeSilentExit(M);
  Instruction *Terminator = BB->getTerminator();

  Instruction *FirstMeaningful = nullptr;
  for (Instruction &I : *BB) {
    if (isa<PHINode>(&I))
      continue;
    if (isa<DbgInfoIntrinsic>(&I))
      continue;
    FirstMeaningful = &I;
    break;
  }

  IRBuilder<> B(Ctx);
  if (!FirstMeaningful || FirstMeaningful == Terminator) {
    B.SetInsertPoint(Terminator);
  } else {
    B.SetInsertPoint(FirstMeaningful->getNextNode());
  }
  seqra_trace::emitKleeSilentExit(B, KSilentExit, 0);
}

void instrumentPathAlternatives(
    Module &M, Function *F, const trace::method::FullTrace &Ft,
    const std::vector<uint32_t> &Path,
    const DenseMap<uint64_t, Instruction *> &OpTab) {
  for (size_t I = 0; I + 1 < Path.size(); ++I) {
    uint32_t U = Path[I];
    uint32_t V = Path[I + 1];

    Instruction *Iu =
        seqra_trace::traceGetInsnForTraceEntry(Ft, U, OpTab, F);
    Instruction *Iv =
        seqra_trace::traceGetInsnForTraceEntry(Ft, V, OpTab, F);
    if (!Iu || !Iv) {
      errs() << "traceguide: missing llvm instruction for trace entries " << U
             << " or " << V << "\n";
      continue;
    }

    BasicBlock *ParentU = Iu->getParent();
    if (ParentU->getTerminator() != Iu)
      continue;

    Instruction *Term = Iu;
    if (cfgSuccessorCount(Term) <= 1)
      continue;

    BasicBlock *BBv = Iv->getParent();
    for (unsigned Si = 0; Si < Term->getNumSuccessors(); ++Si) {
      BasicBlock *SuccBB = Term->getSuccessor(Si);
      if (SuccBB == BBv)
        continue;

      BasicBlock *Pred = SuccBB->getSinglePredecessor();
      if (Pred != ParentU) {
        errs() << "traceguide: skip klee_silent_exit(0) for block; not single "
                  "pred from terminator parent (entry edge "
               << U << " -> " << V << ")\n";
        continue;
      }

      insertSilentExitAfterFirstMeaningful(SuccBB, M);
    }
  }
}

bool insertKleeAbortAfterTraceEntry(
    Module &M, Function *F, const trace::method::FullTrace &Ft,
    uint32_t EntryId, const DenseMap<uint64_t, Instruction *> &OpTab) {
  auto ItEntry = Ft.id_to_trace_entry().find(EntryId);
  if (ItEntry == Ft.id_to_trace_entry().end()) {
    errs() << "traceguide: sink entry id " << EntryId
           << " not in id_to_trace_entry\n";
    return false;
  }
  const auto &Te = ItEntry->second;
  trace::method::TraceEntry::Kind Kind = Te.kind();
  if (Kind != trace::method::TraceEntry::KIND_FINAL &&
      Kind != trace::method::TraceEntry::KIND_UNSPECIFIED) {
    errs() << "traceguide: warning: sink marker entry " << EntryId
           << " is not KIND_FINAL; inserting klee_abort anyway\n";
  }

  Instruction *SinkInsn =
      seqra_trace::lookupInsn(OpTab, seqra_trace::traceOpIdFromEntry(Te));
  if (!SinkInsn || SinkInsn->getFunction() != F)
    SinkInsn =
        seqra_trace::traceGetInsnForTraceEntry(Ft, EntryId, OpTab, F);
  if (!SinkInsn || SinkInsn->getFunction() != F) {
    errs() << "traceguide: could not lookup llvm instruction for sink entry "
           << EntryId << "\n";
    return false;
  }

  LLVMContext &Ctx = M.getContext();
  FunctionCallee KAbort = seqra_trace::getKleeAbort(M);
  IRBuilder<> B(Ctx);
  if (Instruction *Next = SinkInsn->getNextNode())
    B.SetInsertPoint(Next);
  else
    B.SetInsertPoint(SinkInsn->getParent()->getTerminator());
  seqra_trace::emitKleeAbort(B, KAbort);
  return true;
}

} // namespace

bool runTraceGuidePass(Module &M, const trace::Trace &Pb) {
  const std::string &EntryName = Pb.entry_point_name();
  Function *F = M.getFunction(EntryName);
  if (!F) {
    errs() << "traceguide: function not in module: " << EntryName << "\n";
    return false;
  }

  const trace::method::FullTrace *StartFtPtr = nullptr;
  if (!seqra_trace::traceTrySelectStartFullTrace(Pb, F, &StartFtPtr))
    return false;
  const trace::method::FullTrace &StartFt = *StartFtPtr;

  DenseSet<uint32_t> Reach;
  seqra_trace::traceReachableFromStart(StartFt, Reach);
  DenseMap<uint32_t, SmallVector<uint32_t, 4>> Preds;
  seqra_trace::traceBuildPredecessorMap(StartFt, Preds);
  std::vector<uint32_t> StartPath;
  if (!seqra_trace::traceBuildPathFwd(StartFt, Reach, Preds, StartPath)) {
    errs()
        << "traceguide: could not reconstruct start_nodes path start to final\n";
    return false;
  }

  const trace::method::FullTrace *SinkFtPtr = nullptr;
  if (!seqra_trace::traceTrySelectSinkFullTrace(Pb, F, &SinkFtPtr))
    return false;
  const trace::method::FullTrace &SinkFt = *SinkFtPtr;

  Reach.clear();
  seqra_trace::traceReachableFromStart(SinkFt, Reach);
  Preds.clear();
  seqra_trace::traceBuildPredecessorMap(SinkFt, Preds);
  std::vector<uint32_t> SinkPath;
  if (!seqra_trace::traceBuildPathFwd(SinkFt, Reach, Preds, SinkPath)) {
    errs()
        << "traceguide: could not reconstruct sink_nodes path start to final\n";
    return false;
  }

  DenseMap<uint64_t, Instruction *> OpTab = seqra_trace::makeOpIndex(*F);

  instrumentPathAlternatives(M, F, StartFt, StartPath, OpTab);
  if (&SinkFt != &StartFt)
    instrumentPathAlternatives(M, F, SinkFt, SinkPath, OpTab);

  uint32_t SinkFinalId = SinkFt.final_entry_id();
  if (!insertKleeAbortAfterTraceEntry(M, F, SinkFt, SinkFinalId, OpTab))
    return false;

  return true;
}
