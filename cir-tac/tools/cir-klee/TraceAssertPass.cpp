#include "TraceAssertPass.h"
#include "TracePathUtils.h"
#include "TraceUtil.h"

#include "proto/trace.pb.h"

#include "llvm/ADT/DenseMap.h"
#include "llvm/ADT/DenseSet.h"
#include "llvm/ADT/SmallVector.h"
#include "llvm/IR/Constants.h"
#include "llvm/IR/DerivedTypes.h"
#include "llvm/IR/Function.h"
#include "llvm/IR/IRBuilder.h"
#include "llvm/IR/Instructions.h"
#include "llvm/IR/Module.h"
#include "llvm/Support/raw_ostream.h"

#include <string>
#include <vector>

using namespace llvm;

namespace {

bool setIRBuilderForTraceStmt(
    IRBuilder<> &B, const trace::method::FullTrace &Ft, uint32_t EntryId,
    bool afterStmt,
    const DenseMap<uint64_t, Instruction *> &OpTab, Function *F) {
  Instruction *Insn =
      seqra_trace::traceGetInsnForTraceEntry(Ft, EntryId, OpTab, F);
  if (!Insn)
    return false;
  BasicBlock *BB = Insn->getParent();
  if (afterStmt) {
    if (Instruction *N = Insn->getNextNode())
      B.SetInsertPoint(N);
    else
      B.SetInsertPoint(BB->getTerminator());
  } else {
    if (isa<AllocaInst>(Insn)) {
      if (Instruction *N = Insn->getNextNode())
        B.SetInsertPoint(N);
      else
        B.SetInsertPoint(BB->getTerminator());
    } else if (isa<PHINode>(Insn))
      B.SetInsertPoint(BB->getFirstNonPHI());
    else
      B.SetInsertPoint(Insn);
  }
  return true;
}

bool resolveFactAp(IRBuilder<> &B, Function *F,
                   const DenseMap<uint64_t, Instruction *> &OpTab,
                   const trace::FactAp &Ap, Value **OutPtr) {
  Value *Cur = nullptr;
  const auto &PbBase = Ap.base();
  switch (PbBase.value_case()) {
  case ap::APBase::VALUE_NOT_SET:
    return false;
  case ap::APBase::kThis:
    if (F->arg_empty())
      return false;
    Cur = F->getArg(0);
    break;
  case ap::APBase::kLv: {
    Instruction *I = seqra_trace::lookupInsn(OpTab, PbBase.lv().idx());
    if (!I || !I->getType()->isPointerTy())
      return false;
    Cur = I;
    break;
  }
  case ap::APBase::kArg: {
    unsigned Idx = PbBase.arg().idx();
    if (Idx >= F->arg_size())
      return false;
    Cur = F->getArg(Idx);
    break;
  }
  case ap::APBase::kRet:
  case ap::APBase::kExcept:
  default:
    return false;
  }

  if (!Cur->getType()->isPointerTy())
    return false;

  for (const ap::APAccessor &Acc : Ap.accessors()) {
    switch (Acc.value_case()) {
    case ap::APAccessor::VALUE_NOT_SET:
      return false;
    case ap::APAccessor::kTaintMarkAcc:
      break;
    case ap::APAccessor::kFinalAcc:
      break;
    case ap::APAccessor::kRefAcc:
      // Dereferencing the tracked pointer at runtime would force KLEE to load
      // through a potentially-freed address — a UAF source — which fires its
      // own *.ptr.err before our `klee_abort` can run. The trace's `.&`
      // accessor models alias relationship between SSA values, not value
      // identity; equality on the pointer values themselves is the correct
      // KLEE-level constraint.
      break;
    default:
      return false;
    }
  }
  *OutPtr = Cur;
  return true;
}

const trace::method::MethodTraceEdge *
pickMethodEdge(const trace::method::TraceEntry &Te,
               const std::string &lastFactKey) {
  if (lastFactKey.empty())
    return nullptr;
  const trace::method::MethodTraceEdge *Best = nullptr;
  std::string BestSer;
  for (const trace::method::TraceEdge &E : Te.edges()) {
    if (!E.has_method_trace_edge())
      continue;
    const trace::method::MethodTraceEdge &M = E.method_trace_edge();
    if (M.initial_fact().SerializeAsString() != lastFactKey)
      continue;
    std::string Ser = M.SerializeAsString();
    if (!Best || Ser < BestSer) {
      Best = &M;
      BestSer = std::move(Ser);
    }
  }
  return Best;
}

bool traceEntryHasMatchingMethodInitial(const trace::method::TraceEntry &Te,
                                        const std::string &lastFactKey) {
  for (const trace::method::TraceEdge &E : Te.edges()) {
    if (!E.has_method_trace_edge())
      continue;
    if (E.method_trace_edge().initial_fact().SerializeAsString() ==
        lastFactKey)
      return true;
  }
  return false;
}

struct AssertTrace {
  Function *F;
  const trace::method::FullTrace *Ft;
};

void considerAssertTraceCandidate(Function *F,
                                  const trace::method::FullTrace &Ft,
                                  Function *EntryF, bool PreferSourceCall,
                                  const trace::method::FullTrace **BestFt,
                                  Function **BestF, int &BestScore,
                                  size_t &BestIdx, size_t Idx) {
  std::vector<uint32_t> Path;
  if (!seqra_trace::traceBuildPathFwd(Ft, Path))
    return;

  const bool HasSource = seqra_trace::tracePathHasSourceStart(Ft, Path);
  int Score = 0;
  if (HasSource)
    Score += 100;
  if (F == EntryF)
    Score += 10;
  if (PreferSourceCall)
    Score += 5;

  if (Score > BestScore || (Score == BestScore && Idx < BestIdx)) {
    BestScore = Score;
    BestIdx = Idx;
    *BestFt = &Ft;
    *BestF = F;
  }
}

bool selectAssertableStartFullTrace(Module &M, const trace::Trace &Pb,
                                    Function *EntryF, AssertTrace &Out) {
  const auto &Sts = Pb.source_to_sink_trace();
  const trace::method::FullTrace *BestFt = nullptr;
  Function *BestF = nullptr;
  int BestScore = -1;
  size_t BestIdx = 0;
  size_t Idx = 0;

  for (const trace::SourceToSinkTraceNode &Node : Sts.start_nodes()) {
    if (Node.value_case() != trace::SourceToSinkTraceNode::kFull) {
      ++Idx;
      continue;
    }
    const trace::FullTraceNode &FullNode = Node.full();
    Function *F = M.getFunction(FullNode.method().name());
    if (!F) {
      ++Idx;
      continue;
    }
    considerAssertTraceCandidate(F, FullNode.trace(), EntryF,
                                 /*PreferSourceCall=*/false, &BestFt, &BestF,
                                 BestScore, BestIdx, Idx++);
  }

  for (const auto &Succ : Sts.successors()) {
    for (const trace::InterProceduralCall &Call : Succ.second.calls()) {
      if (!Call.has_node()) {
        ++Idx;
        continue;
      }
      const trace::FullTraceNode &FullNode = Call.node();
      Function *F = M.getFunction(FullNode.method().name());
      if (!F) {
        ++Idx;
        continue;
      }
      considerAssertTraceCandidate(
          F, FullNode.trace(), EntryF,
          Call.kind() == trace::InterProceduralCall::CALL_KIND_CALL_TO_SOURCE,
          &BestFt, &BestF, BestScore, BestIdx, Idx++);
    }
  }

  if (!BestFt)
    return false;

  Out = {BestF, BestFt};
  return true;
}

} // namespace

bool runTraceAssertPass(Module &M, const trace::Trace &Pb) {
  const std::string &EntryName = Pb.entry_point_name();
  Function *EntryF = M.getFunction(EntryName);
  if (!EntryF) {
    errs() << "traceassert: function not in module: " << EntryName << "\n";
    return false;
  }

  AssertTrace Selected{};
  if (!selectAssertableStartFullTrace(M, Pb, EntryF, Selected)) {
    errs() << "traceassert: no assertable FullTrace for " << EntryName
           << "; skipping TraceAssertPass\n";
    return true;
  }
  Function *F = Selected.F;
  const trace::method::FullTrace &StartFt = *Selected.Ft;

  std::vector<uint32_t> Path;
  if (!seqra_trace::traceBuildPathFwd(StartFt, Path)) {
    errs() << "traceassert: could not reconstruct start_nodes path\n";
    return false;
  }

  DenseMap<uint64_t, Instruction *> OpTab = seqra_trace::makeOpIndex(*F);
  LLVMContext &Ctx = M.getContext();
  PointerType *PtrTy = PointerType::getUnqual(Ctx);
  FunctionCallee KAbort = seqra_trace::getKleeAbort(M);

  BasicBlock &Entry = F->getEntryBlock();
  IRBuilder<> EntryB(&Entry, Entry.getFirstInsertionPt());
  AllocaInst *FreedSlot =
      EntryB.CreateAlloca(PtrTy, nullptr, "seqra.trace.freed.ptr");

  const bool afterStmt =
      StartFt.trace_kind() ==
      trace::method::FullTrace::TRACE_KIND_TRACE_TO_FACT_AFTER_STATEMENT;

  bool slotInit = false;
  std::string lastFactKey;

  for (size_t i = 0; i < Path.size(); ++i) {
    uint32_t Eid = Path[i];
    auto ItE = StartFt.id_to_trace_entry().find(Eid);
    if (ItE == StartFt.id_to_trace_entry().end())
      continue;
    const trace::method::TraceEntry &Te = ItE->second;

    if (Te.kind() == trace::method::TraceEntry::KIND_SOURCE_START) {
      IRBuilder<> B(Ctx);
      if (!setIRBuilderForTraceStmt(B, StartFt, Eid, afterStmt, OpTab, F)) {
        errs() << "traceassert: no llvm instruction for source entry " << Eid
               << "\n";
        return false;
      }
      bool sawSource = false;
      for (const trace::method::TraceEdge &Edge : Te.edges()) {
        if (!Edge.has_source_trace_edge())
          continue;
        sawSource = true;
        const trace::FactAp &Fact = Edge.source_trace_edge().fact();
        Value *P = nullptr;
        if (!resolveFactAp(B, F, OpTab, Fact, &P)) {
          errs() << "traceassert: failed to resolve SourceTraceEdge fact at "
                 << Eid << "\n";
          return false;
        }
        if (!slotInit) {
          B.CreateStore(P, FreedSlot);
          slotInit = true;
          lastFactKey = Fact.SerializeAsString();
        } else {
          Value *Ld = B.CreateLoad(PtrTy, FreedSlot);
          seqra_trace::emitKleeAbortIfPtrNe(B, KAbort, P, Ld);
        }
      }
      if (!sawSource) {
        errs() << "traceassert: KIND_SOURCE_START entry " << Eid
               << " has no SourceTraceEdge\n";
        return false;
      }
    }

    if (i + 1 >= Path.size())
      continue;

    uint32_t U = Path[i];
    uint32_t V = Path[i + 1];
    auto ItU = StartFt.id_to_trace_entry().find(U);
    auto ItV = StartFt.id_to_trace_entry().find(V);
    if (ItU == StartFt.id_to_trace_entry().end() ||
        ItV == StartFt.id_to_trace_entry().end()) {
      errs() << "traceassert: missing trace entry in id_to_trace_entry\n";
      return false;
    }

    if (!lastFactKey.size())
      continue;

    const trace::method::MethodTraceEdge *MeU =
        pickMethodEdge(ItU->second, lastFactKey);
    const trace::method::MethodTraceEdge *MeV =
        pickMethodEdge(ItV->second, lastFactKey);

    const trace::method::MethodTraceEdge *Me = nullptr;
    uint32_t InsertEid = U;

    if (MeU && MeV) {
      if (MeU->SerializeAsString() != MeV->SerializeAsString()) {
        errs() << "traceassert: conflicting MethodTraceEdge for step " << U
               << " -> " << V << "\n";
        return false;
      }
      Me = MeU;
      InsertEid = U;
    } else if (MeU) {
      Me = MeU;
      InsertEid = U;
    } else if (MeV) {
      Me = MeV;
      InsertEid = V;
    }

    if (Me) {
      if (!slotInit) {
        errs() << "traceassert: MethodTraceEdge before freed slot init\n";
        return false;
      }
      IRBuilder<> B(Ctx);
      // `initial_fact` is the precondition on entry to the edge's operation, so
      // the check goes strictly *before* that operation, independent of the
      // trace's after-statement materialization mode.
      if (!setIRBuilderForTraceStmt(B, StartFt, InsertEid, /*afterStmt=*/false,
                                    OpTab, F)) {
        errs() << "traceassert: no llvm instruction for edge insert at "
               << InsertEid << "\n";
        return false;
      }
      Value *Ini = nullptr;
      if (!resolveFactAp(B, F, OpTab, Me->initial_fact(), &Ini)) {
        errs() << "traceassert: resolve initial_fact failed step " << U << "->"
               << V << "\n";
        return false;
      }
      // Check only `init`: this edge's `fact` is the next edge's `init`, so it
      // is checked before the next operation; the trace's final `fact` is left
      // to KLEE's native use-after-free detection at the sink.
      Value *Ld = B.CreateLoad(PtrTy, FreedSlot);
      seqra_trace::emitKleeAbortIfPtrNe(B, KAbort, Ini, Ld);
      lastFactKey = Me->fact().SerializeAsString();
    } else {
      bool Expect =
          traceEntryHasMatchingMethodInitial(ItU->second, lastFactKey) ||
          traceEntryHasMatchingMethodInitial(ItV->second, lastFactKey);
      if (Expect) {
        errs() << "traceassert: no MethodTraceEdge selected for step " << U
               << " -> " << V << " but matching initial_fact exists\n";
        return false;
      }
    }
  }

  if (!slotInit) {
    errs() << "traceassert: never initialized freed pointer (no source?)\n";
    return false;
  }

  return true;
}
