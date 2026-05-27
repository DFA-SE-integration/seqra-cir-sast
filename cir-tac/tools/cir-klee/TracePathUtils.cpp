#include "TracePathUtils.h"
#include "TraceUtil.h"

#include "llvm/ADT/SmallVector.h"
#include "llvm/IR/Function.h"
#include "llvm/IR/Instructions.h"
#include "llvm/Support/raw_ostream.h"

#include <algorithm>
#include <cstdint>
#include <deque>
#include <string>
#include <vector>

using namespace llvm;

namespace seqra_trace {

uint64_t traceOpIdFromEntry(const trace::method::TraceEntry &E) {
  return E.statement().id();
}

// BFS from start_entry_id over the FullTrace successors graph. Records both
// reachability and the BFS parent of each discovered node — the parent map is
// the BFS spanning tree, so reconstructing final → start gives a real path
// (independent of id ordering). Sentinel `kNoParent` marks the start.
static constexpr uint32_t kNoParent = UINT32_MAX;

static void bfsParents(const trace::method::FullTrace &Ft,
                       DenseSet<uint32_t> &Reach,
                       DenseMap<uint32_t, uint32_t> &Parent) {
  const auto &Entries = Ft.id_to_trace_entry();
  if (Entries.empty())
    return;

  uint32_t Start = Ft.start_entry_id();
  if (Entries.find(Start) == Entries.end())
    return;

  std::deque<uint32_t> Q;
  Q.push_back(Start);
  Reach.insert(Start);
  Parent[Start] = kNoParent;
  SmallVector<uint32_t, 8> Succ;
  while (!Q.empty()) {
    uint32_t U = Q.front();
    Q.pop_front();
    successorsOf(Ft, U, Succ);
    for (uint32_t V : Succ) {
      if (Entries.find(V) == Entries.end())
        continue;
      if (Reach.insert(V).second) {
        Parent[V] = U;
        Q.push_back(V);
      }
    }
  }
}

void traceReachableFromStart(const trace::method::FullTrace &Ft,
                             DenseSet<uint32_t> &Reach) {
  DenseMap<uint32_t, uint32_t> Parent;
  bfsParents(Ft, Reach, Parent);
}

bool traceBuildPathFwd(const trace::method::FullTrace &Ft,
                       std::vector<uint32_t> &OutPath) {
  const auto &Entries = Ft.id_to_trace_entry();
  uint32_t Start = Ft.start_entry_id();
  uint32_t Final = Ft.final_entry_id();
  if (Entries.find(Start) == Entries.end() ||
      Entries.find(Final) == Entries.end())
    return false;

  DenseSet<uint32_t> LocalReach;
  DenseMap<uint32_t, uint32_t> Parent;
  bfsParents(Ft, LocalReach, Parent);
  if (!LocalReach.count(Final))
    return false;

  SmallVector<uint32_t, 32> Backward;
  uint32_t Cur = Final;
  Backward.push_back(Cur);
  while (Cur != Start) {
    auto Pit = Parent.find(Cur);
    if (Pit == Parent.end() || Pit->second == kNoParent)
      return false;
    Cur = Pit->second;
    Backward.push_back(Cur);
  }

  OutPath.clear();
  OutPath.reserve(Backward.size());
  for (auto It = Backward.rbegin(); It != Backward.rend(); ++It)
    OutPath.push_back(*It);
  return true;
}

bool tracePathHasSourceStart(const trace::method::FullTrace &Ft,
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

Instruction *lookupFactBaseLvInsn(
    const DenseMap<uint64_t, Instruction *> &OpTab, const Function *F,
    const trace::FactAp &Fact) {
  const auto &Base = Fact.base();
  if (Base.value_case() != ap::APBase::kLv)
    return nullptr;
  Instruction *Insn = lookupInsn(OpTab, Base.lv().idx());
  if (!Insn || Insn->getFunction() != F)
    return nullptr;
  return Insn;
}

Instruction *lookupInsnFromTraceEdgeFacts(
    const DenseMap<uint64_t, Instruction *> &OpTab, const Function *F,
    const trace::method::TraceEntry &E) {
  for (const trace::method::TraceEdge &Edge : E.edges()) {
    if (Edge.has_source_trace_edge()) {
      if (Instruction *Insn =
              lookupFactBaseLvInsn(OpTab, F, Edge.source_trace_edge().fact()))
        return Insn;
    }
    if (Edge.has_method_trace_edge()) {
      const trace::method::MethodTraceEdge &Method = Edge.method_trace_edge();
      if (Instruction *Insn = lookupFactBaseLvInsn(OpTab, F, Method.fact()))
        return Insn;
      if (Instruction *Insn =
              lookupFactBaseLvInsn(OpTab, F, Method.initial_fact()))
        return Insn;
    }
  }
  return nullptr;
}

Instruction *traceGetInsnForTraceEntry(
    const trace::method::FullTrace &Ft, uint32_t EntryId,
    const DenseMap<uint64_t, Instruction *> &OpTab, const Function *F) {
  auto It = Ft.id_to_trace_entry().find(EntryId);
  if (It == Ft.id_to_trace_entry().end())
    return nullptr;
  const auto &E = It->second;
  Instruction *Insn = lookupInsn(OpTab, traceOpIdFromEntry(E));
  if (Insn && Insn->getFunction() == F)
    return Insn;
  return lookupInsnFromTraceEdgeFacts(OpTab, F, E);
}

bool traceTrySelectStartFullTrace(const trace::Trace &Pb, const Function *F,
                                  const trace::method::FullTrace **OutFt) {
  const auto &Sts = Pb.source_to_sink_trace();
  const trace::method::FullTrace *Best = nullptr;
  int BestScore = -1;
  size_t BestIdx = SIZE_MAX;
  std::vector<uint32_t> Path;

  for (int Idx = 0; Idx < Sts.start_nodes_size(); ++Idx) {
    const trace::SourceToSinkTraceNode &Node = Sts.start_nodes(Idx);
    if (Node.value_case() != trace::SourceToSinkTraceNode::kFull)
      continue;
    const trace::FullTraceNode &Fn = Node.full();
    if (Fn.method().name() != F->getName().str())
      continue;
    const trace::method::FullTrace &Ft = Fn.trace();

    Path.clear();
    if (!traceBuildPathFwd(Ft, Path))
      continue;

    bool HasSource = tracePathHasSourceStart(Ft, Path);
    int Score = HasSource ? 2 : 1;
    size_t UIdx = static_cast<size_t>(Idx);
    if (Score > BestScore || (Score == BestScore && UIdx < BestIdx)) {
      BestScore = Score;
      BestIdx = UIdx;
      Best = &Ft;
    }
  }

  if (!Best) {
    errs() << "tracepath: no FullTrace in source_to_sink_trace.start_nodes "
              "matching function "
           << F->getName() << "\n";
    return false;
  }

  *OutFt = Best;
  return true;
}

bool traceTrySelectSinkFullTrace(const trace::Trace &Pb, const Function *F,
                                 const trace::method::FullTrace **OutFt) {
  const auto &Sts = Pb.source_to_sink_trace();
  const trace::method::FullTrace *Best = nullptr;
  size_t BestIdx = SIZE_MAX;
  bool HasUnsupportedSink = false;
  std::vector<uint32_t> Path;

  for (int Idx = 0; Idx < Sts.sink_nodes_size(); ++Idx) {
    const trace::SourceToSinkTraceNode &Node = Sts.sink_nodes(Idx);
    if (Node.value_case() != trace::SourceToSinkTraceNode::kFull) {
      HasUnsupportedSink = true;
      continue;
    }
    const trace::FullTraceNode &Fn = Node.full();
    if (Fn.method().name() != F->getName().str())
      continue;
    const trace::method::FullTrace &Ft = Fn.trace();

    Path.clear();
    if (!traceBuildPathFwd(Ft, Path))
      continue;

    size_t UIdx = static_cast<size_t>(Idx);
    if (!Best || UIdx < BestIdx) {
      BestIdx = UIdx;
      Best = &Ft;
    }
  }

  if (!Best) {
    if (HasUnsupportedSink) {
      errs() << "tracepath: source_to_sink_trace.sink_nodes contains "
                "non-Full trace nodes (summary/simple unsupported for "
                "klee_abort); need FullTrace sink for "
             << F->getName() << "\n";
    } else {
      errs() << "tracepath: no FullTrace in source_to_sink_trace.sink_nodes "
                "matching function "
             << F->getName() << "\n";
    }
    return false;
  }

  *OutFt = Best;
  return true;
}

} // namespace seqra_trace
