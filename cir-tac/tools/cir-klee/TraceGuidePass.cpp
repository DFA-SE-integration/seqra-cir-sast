#include "TraceGuidePass.h"
#include "TraceUtil.h"

#include "proto/trace.pb.h"

#include "llvm/ADT/DenseMap.h"
#include "llvm/ADT/DenseSet.h"
#include "llvm/ADT/SmallVector.h"
#include "llvm/IR/BasicBlock.h"
#include "llvm/IR/DerivedTypes.h"
#include "llvm/IR/Function.h"
#include "llvm/IR/IRBuilder.h"
#include "llvm/IR/Instructions.h"
#include "llvm/IR/IntrinsicInst.h"
#include "llvm/IR/Module.h"
#include "llvm/Support/raw_ostream.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <deque>
#include <string>
#include <vector>

using namespace llvm;

namespace {

void reachableFromStart(const trace::method::FullTrace &Ft,
                        DenseSet<uint32_t> &Reach) {
  const auto &Entries = Ft.id_to_trace_entry();
  if (Entries.empty())
    return;

  uint32_t Start = Ft.start_entry_id();
  if (Entries.find(Start) == Entries.end())
    return;

  std::deque<uint32_t> Q;
  Q.push_back(Start);
  Reach.insert(Start);
  SmallVector<uint32_t, 8> Succ;
  while (!Q.empty()) {
    uint32_t U = Q.front();
    Q.pop_front();
    seqra_trace::successorsOf(Ft, U, Succ);
    for (uint32_t V : Succ) {
      if (Entries.find(V) == Entries.end())
        continue;
      if (Reach.insert(V).second)
        Q.push_back(V);
    }
  }
}

uint64_t opIdFromEntry(const trace::method::TraceEntry &E) {
  return E.statement().id();
}

bool buildPredecessorMap(
    const trace::method::FullTrace &Ft,
    DenseMap<uint32_t, SmallVector<uint32_t, 4>> &Preds) {
  const auto &Entries = Ft.id_to_trace_entry();
  for (const auto &Pair : Ft.successors()) {
    uint32_t From = Pair.first;
    if (Entries.find(From) == Entries.end())
      continue;
    for (uint32_t To : Pair.second.ids()) {
      if (Entries.find(To) == Entries.end())
        continue;
      Preds[To].push_back(From);
    }
  }
  return true;
}

bool buildPathFwd(const trace::method::FullTrace &Ft,
                  const DenseSet<uint32_t> &Reach,
                  const DenseMap<uint32_t, SmallVector<uint32_t, 4>> &Preds,
                  std::vector<uint32_t> &OutPath) {
  const auto &Entries = Ft.id_to_trace_entry();
  uint32_t Start = Ft.start_entry_id();
  uint32_t Final = Ft.final_entry_id();
  if (Entries.find(Start) == Entries.end() ||
      Entries.find(Final) == Entries.end())
    return false;
  if (!Reach.count(Final))
    return false;

  SmallVector<uint32_t, 32> Backward;
  uint32_t Cur = Final;
  Backward.push_back(Cur);
  while (Cur != Start) {
    auto Pit = Preds.find(Cur);
    if (Pit == Preds.end())
      return false;
    uint32_t Best = UINT32_MAX;
    for (uint32_t P : Pit->second) {
      if (Reach.count(P) && Entries.find(P) != Entries.end())
        Best = std::min(Best, P);
    }
    if (Best == UINT32_MAX)
      return false;
    Cur = Best;
    Backward.push_back(Cur);
  }

  OutPath.clear();
  OutPath.reserve(Backward.size());
  for (auto It = Backward.rbegin(); It != Backward.rend(); ++It)
    OutPath.push_back(*It);
  return true;
}

bool pathHasSourceStart(const trace::method::FullTrace &Ft,
                        const std::vector<uint32_t> &Path) {
  for (uint32_t Eid : Path) {
    auto It = Ft.id_to_trace_entry().find(Eid);
    if (It == Ft.id_to_trace_entry().end())
      continue;
    if (It->second.kind() == trace::method::TraceEntry::KIND_SOURCE_START)
      return true;
  }
  return false;
}

Instruction *getInsnForTraceEntry(
    const trace::method::FullTrace &Ft, uint32_t EntryId,
    const DenseMap<uint64_t, Instruction *> &OpTab, const Function *F) {
  auto It = Ft.id_to_trace_entry().find(EntryId);
  if (It == Ft.id_to_trace_entry().end())
    return nullptr;
  const auto &E = It->second;
  // Intra-procedural FullTrace: op ids are for this LLVM function. Protobuf
  // `fun_id` may use a different string than `F->getName()` (symName vs id).
  Instruction *Insn = seqra_trace::lookupInsn(OpTab, opIdFromEntry(E));
  if (!Insn || Insn->getFunction() != F)
    return nullptr;
  return Insn;
}

Instruction *findNextMappedInsnOnPath(
    const trace::method::FullTrace &Ft, const std::vector<uint32_t> &Path,
    size_t StartIdx, const DenseMap<uint64_t, Instruction *> &OpTab,
    const Function *F) {
  for (size_t I = StartIdx + 1; I < Path.size(); ++I) {
    Instruction *Insn = getInsnForTraceEntry(Ft, Path[I], OpTab, F);
    if (!Insn)
      continue;
    return Insn;
  }
  return nullptr;
}

unsigned cfgSuccessorCount(const Instruction *Term) {
  if (!Term || !Term->isTerminator())
    return 0;
  if (isa<UnreachableInst>(Term) || isa<ReturnInst>(Term) ||
      isa<ResumeInst>(Term))
    return 0;
  return Term->getNumSuccessors();
}

/// Prune an alternate CFG successor by ending the path with klee_silent_exit(0)
/// (no test case), after the first meaningful instruction in BB. If the only
/// non-PHI is the terminator, insert before the terminator.
/// Unsafe if BB has multiple predecessors: caller checks singlePredecessor.
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

bool trySelectFullTrace(const trace::Trace &Pb, const Function *F,
                        const trace::method::FullTrace **OutFt) {
  const auto &Sts = Pb.source_to_sink_trace();
  const trace::method::FullTrace *Best = nullptr;
  int BestScore = -1;
  size_t BestIdx = SIZE_MAX;
  std::vector<uint32_t> Path;
  DenseSet<uint32_t> Reach;
  DenseMap<uint32_t, SmallVector<uint32_t, 4>> Preds;

  for (int Idx = 0; Idx < Sts.start_nodes_size(); ++Idx) {
    const trace::SourceToSinkTraceNode &Node = Sts.start_nodes(Idx);
    if (Node.value_case() != trace::SourceToSinkTraceNode::kFull)
      continue;
    const trace::FullTraceNode &Fn = Node.full();
    if (Fn.method().id() != F->getName().str())
      continue;
    const trace::method::FullTrace &Ft = Fn.trace();

    Reach.clear();
    reachableFromStart(Ft, Reach);
    Preds.clear();
    buildPredecessorMap(Ft, Preds);
    Path.clear();
    if (!buildPathFwd(Ft, Reach, Preds, Path))
      continue;

    bool HasSource = pathHasSourceStart(Ft, Path);
    int Score = HasSource ? 2 : 1;
    size_t UIdx = static_cast<size_t>(Idx);
    if (Score > BestScore ||
        (Score == BestScore && UIdx < BestIdx)) {
      BestScore = Score;
      BestIdx = UIdx;
      Best = &Ft;
    }
  }

  if (!Best) {
    errs() << "traceguide: no FullTrace in source_to_sink_trace.start_nodes "
              "matching function "
           << F->getName() << "\n";
    return false;
  }

  *OutFt = Best;
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

  const trace::method::FullTrace *FtPtr = nullptr;
  if (!trySelectFullTrace(Pb, F, &FtPtr))
    return false;
  const trace::method::FullTrace &Ft = *FtPtr;

  DenseSet<uint32_t> Reach;
  reachableFromStart(Ft, Reach);
  DenseMap<uint32_t, SmallVector<uint32_t, 4>> Preds;
  buildPredecessorMap(Ft, Preds);
  std::vector<uint32_t> Path;
  if (!buildPathFwd(Ft, Reach, Preds, Path)) {
    errs() << "traceguide: could not reconstruct path from start to final\n";
    return false;
  }

  DenseMap<uint64_t, Instruction *> OpTab = seqra_trace::makeOpIndex(*F);

  for (size_t I = 0; I + 1 < Path.size(); ++I) {
    uint32_t U = Path[I];
    uint32_t V = Path[I + 1];

    Instruction *Iu =
        getInsnForTraceEntry(Ft, U, OpTab, F);
    Instruction *Iv =
        getInsnForTraceEntry(Ft, V, OpTab, F);
    if (!Iu || !Iv) {
      errs() << "traceguide: missing llvm instruction for trace entries "
             << U << " or " << V << "\n";
      continue;
    }

    BasicBlock *ParentU = Iu->getParent();
    if (ParentU->getTerminator() != Iu) {
      // Not a block terminator; trace step may be intra-block — no
      // klee_silent_exit pruning for alternate CFG targets.
      continue;
    }

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
        errs()
            << "traceguide: skip klee_silent_exit(0) for block; not single "
               "pred from terminator parent (entry edge "
            << U << " -> " << V << ")\n";
        continue;
      }

      insertSilentExitAfterFirstMeaningful(SuccBB, M);
    }
  }

  const trace::method::TraceEntry *SourceEntry = nullptr;
  uint32_t SourceEid = 0;
  size_t SourcePathIdx = 0;
  for (size_t CurrentIdx = 0; CurrentIdx < Path.size(); ++CurrentIdx) {
    uint32_t Eid = Path[CurrentIdx];
    auto It = Ft.id_to_trace_entry().find(Eid);
    if (It == Ft.id_to_trace_entry().end())
      continue;
    if (It->second.kind() == trace::method::TraceEntry::KIND_SOURCE_START) {
      SourceEntry = &It->second;
      SourceEid = Eid;
      SourcePathIdx = CurrentIdx;
      break;
    }
  }

  if (!SourceEntry) {
    errs() << "traceguide: warning: no KIND_SOURCE_START on path; skipping "
              "klee_abort marker\n";
    return true;
  }

  Instruction *SrcInsn =
      seqra_trace::lookupInsn(OpTab, opIdFromEntry(*SourceEntry));
  bool InsertBeforeSrcInsn = false;
  if (!SrcInsn || SrcInsn->getFunction() != F) {
    Instruction *FallbackInsn =
        findNextMappedInsnOnPath(Ft, Path, SourcePathIdx, OpTab, F);
    if (FallbackInsn) {
      SrcInsn = FallbackInsn;
      InsertBeforeSrcInsn = true;
    }
  }
  if (!SrcInsn || SrcInsn->getFunction() != F) {
    errs() << "traceguide: could not lookup llvm instruction for source entry "
           << SourceEid << "\n";
    return false;
  }

  {
    LLVMContext &Ctx = M.getContext();
    FunctionCallee KAbort = seqra_trace::getKleeAbort(M);
    IRBuilder<> B(Ctx);
    if (InsertBeforeSrcInsn)
      B.SetInsertPoint(SrcInsn);
    else if (Instruction *Next = SrcInsn->getNextNode())
      B.SetInsertPoint(Next);
    else
      B.SetInsertPoint(SrcInsn->getParent()->getTerminator());
    seqra_trace::emitKleeAbort(B, KAbort);
  }

  return true;
}
