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

// Insert `klee_silent_exit(0)` at the top of an off-trace block, AFTER PHIs but
// BEFORE the first content instruction. Placing it after the first meaningful
// op would let that op execute (potentially with side effects on KLEE state)
// before the exit — defeating the cut.
void insertSilentExitAtBlockTop(BasicBlock *BB, Module &M) {
  FunctionCallee KSilentExit = seqra_trace::getKleeSilentExit(M);
  IRBuilder<> B(BB, BB->getFirstInsertionPt());
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

      insertSilentExitAtBlockTop(SuccBB, M);
    }
  }
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

  std::vector<uint32_t> StartPath;
  if (!seqra_trace::traceBuildPathFwd(StartFt, StartPath)) {
    errs()
        << "traceguide: could not reconstruct start_nodes path start to final\n";
    return false;
  }

  const trace::method::FullTrace *SinkFtPtr = nullptr;
  if (!seqra_trace::traceTrySelectSinkFullTrace(Pb, F, &SinkFtPtr))
    return false;
  const trace::method::FullTrace &SinkFt = *SinkFtPtr;

  std::vector<uint32_t> SinkPath;
  if (!seqra_trace::traceBuildPathFwd(SinkFt, SinkPath)) {
    errs()
        << "traceguide: could not reconstruct sink_nodes path start to final\n";
    return false;
  }

  DenseMap<uint64_t, Instruction *> OpTab = seqra_trace::makeOpIndex(*F);

  instrumentPathAlternatives(M, F, StartFt, StartPath, OpTab);
  if (&SinkFt != &StartFt)
    instrumentPathAlternatives(M, F, SinkFt, SinkPath, OpTab);

  return true;
}
