#include "TraceGuidePass.h"
#include "TracePathUtils.h"
#include "TraceUtil.h"

#include "proto/trace.pb.h"

#include "llvm/ADT/DenseMap.h"
#include "llvm/ADT/DenseSet.h"
#include "llvm/ADT/Hashing.h"
#include "llvm/ADT/SmallVector.h"
#include "llvm/ADT/StringRef.h"
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

struct GuideTrace {
  Function *F;
  const trace::method::FullTrace *Ft;
  std::string Kind;
};

struct FullTraceKey {
  Function *F;
  uint32_t StartEntryId;
  uint32_t FinalEntryId;

  bool operator==(const FullTraceKey &O) const {
    return F == O.F && StartEntryId == O.StartEntryId &&
           FinalEntryId == O.FinalEntryId;
  }
};

struct FullTraceKeyInfo {
  static FullTraceKey getEmptyKey() {
    return {nullptr, 0, 0};
  }
  static FullTraceKey getTombstoneKey() {
    return {reinterpret_cast<Function *>(-1), 0, 0};
  }
  static unsigned getHashValue(const FullTraceKey &K) {
    return hash_combine(K.F, K.StartEntryId, K.FinalEntryId);
  }
  static bool isEqual(const FullTraceKey &LHS, const FullTraceKey &RHS) {
    return LHS == RHS;
  }
};

void collectGuideableFullTraces(
    Module &M,
    const google::protobuf::RepeatedPtrField<trace::SourceToSinkTraceNode>
        &Nodes,
    StringRef Kind, std::vector<GuideTrace> &Out,
    DenseSet<FullTraceKey, FullTraceKeyInfo> &Seen) {
  for (const trace::SourceToSinkTraceNode &Node : Nodes) {
    if (Node.value_case() != trace::SourceToSinkTraceNode::kFull) {
      errs() << "traceguide: skip non-Full " << Kind << " trace node\n";
      continue;
    }

    const trace::FullTraceNode &FullNode = Node.full();
    StringRef MethodName(FullNode.method().name());
    Function *F = M.getFunction(MethodName);
    if (!F) {
      errs() << "traceguide: skip " << Kind
             << " FullTrace; function not in module: " << MethodName << "\n";
      continue;
    }

    const trace::method::FullTrace &Ft = FullNode.trace();
    FullTraceKey Key{F, Ft.start_entry_id(), Ft.final_entry_id()};
    if (!Seen.insert(Key).second)
      continue;

    Out.push_back({F, &Ft, Kind.str()});
  }
}

} // namespace

bool runTraceGuidePass(Module &M, const trace::Trace &Pb) {
  const std::string &EntryName = Pb.entry_point_name();
  Function *EntryF = M.getFunction(EntryName);
  if (!EntryF) {
    errs() << "traceguide: function not in module: " << EntryName << "\n";
    return false;
  }

  const auto &Sts = Pb.source_to_sink_trace();
  std::vector<GuideTrace> Traces;
  DenseSet<FullTraceKey, FullTraceKeyInfo> Seen;
  collectGuideableFullTraces(M, Sts.start_nodes(), "start", Traces, Seen);
  collectGuideableFullTraces(M, Sts.sink_nodes(), "sink", Traces, Seen);

  if (Traces.empty()) {
    errs() << "traceguide: no guideable FullTrace nodes; skipping "
              "TraceGuidePass\n";
    return true;
  }

  DenseMap<Function *, DenseMap<uint64_t, Instruction *>> OpTabs;
  for (const GuideTrace &Gt : Traces) {
    std::vector<uint32_t> Path;
    if (!seqra_trace::traceBuildPathFwd(*Gt.Ft, Path)) {
      errs() << "traceguide: skip " << Gt.Kind
             << " FullTrace; could not reconstruct path for "
             << Gt.F->getName() << "\n";
      continue;
    }

    auto &OpTab = OpTabs[Gt.F];
    if (OpTab.empty())
      OpTab = seqra_trace::makeOpIndex(*Gt.F);
    instrumentPathAlternatives(M, Gt.F, *Gt.Ft, Path, OpTab);
  }

  return true;
}
