#include "TraceUtil.h"

#include "llvm/IR/DerivedTypes.h"
#include "llvm/IR/Function.h"
#include "llvm/IR/Instructions.h"
#include "llvm/IR/Metadata.h"
#include "llvm/IR/Module.h"

#include <deque>

using namespace llvm;

namespace seqra_trace {

StringLiteral seqraOpMetadataName() { return StringLiteral("seqra.op"); }

std::optional<uint64_t> seqraOpId(const Instruction *I) {
  if (!I)
    return std::nullopt;
  MDNode *Md = I->getMetadata(seqraOpMetadataName());
  if (!Md || Md->getNumOperands() < 1)
    return std::nullopt;
  if (auto *CI = mdconst::dyn_extract<ConstantInt>(Md->getOperand(0)))
    return CI->getZExtValue();
  return std::nullopt;
}

DenseMap<uint64_t, Instruction *> makeOpIndex(Function &F) {
  DenseMap<uint64_t, Instruction *> Idx;
  for (BasicBlock &BB : F)
    for (Instruction &I : BB)
      if (auto Id = seqraOpId(&I))
        Idx.insert({*Id, &I});
  return Idx;
}

Instruction *lookupInsn(const DenseMap<uint64_t, Instruction *> &Tab,
                        uint64_t OpId) {
  auto It = Tab.find(OpId);
  return It == Tab.end() ? nullptr : It->second;
}

void successorsOf(const trace::method::FullTrace &Ft, uint32_t U,
                  SmallVectorImpl<uint32_t> &Succ) {
  Succ.clear();
  const auto &SuccMap = Ft.successors();
  auto It = SuccMap.find(U);
  if (It == SuccMap.end())
    return;
  for (uint32_t Sid : It->second.ids())
    Succ.push_back(Sid);
}

void bfsReachable(const trace::method::FullTrace &Ft,
                  DenseSet<uint32_t> &Reach) {
  const auto &Entries = Ft.id_to_trace_entry();
  if (Entries.empty())
    return;

  uint32_t Start = Ft.start_entry_id();
  if (Entries.find(Start) == Entries.end()) {
    Start = 0;
    if (Entries.find(Start) == Entries.end())
      Start = Entries.begin()->first;
  }

  std::deque<uint32_t> Q;
  Q.push_back(Start);
  Reach.insert(Start);
  SmallVector<uint32_t, 8> Succ;
  while (!Q.empty()) {
    uint32_t U = Q.front();
    Q.pop_front();
    successorsOf(Ft, U, Succ);
    for (uint32_t V : Succ) {
      if (Entries.find(V) == Entries.end())
        continue;
      if (Reach.insert(V).second)
        Q.push_back(V);
    }
  }
}

FunctionCallee getKleeAssume(Module &M) {
  LLVMContext &Ctx = M.getContext();
  Type *I64 = IntegerType::getInt64Ty(Ctx);
  FunctionType *FT =
      FunctionType::get(Type::getVoidTy(Ctx), {I64}, false);
  return M.getOrInsertFunction("klee_assume", FT);
}

FunctionCallee getKleeAssert(Module &M) {
  LLVMContext &Ctx = M.getContext();
  Type *I32 = IntegerType::getInt32Ty(Ctx);
  FunctionType *FT =
      FunctionType::get(Type::getVoidTy(Ctx), {I32}, false);
  return M.getOrInsertFunction("klee_assert", FT);
}

void emitKleeAssumeEq(IRBuilder<> &B, FunctionCallee KAssume, Value *CondI1) {
  Value *Ext =
      B.CreateZExt(CondI1, IntegerType::getInt64Ty(B.getContext()));
  B.CreateCall(KAssume, {Ext});
}

void emitKleeAssertTrue(IRBuilder<> &B, FunctionCallee KAssert, Value *CondI1) {
  Value *I32 =
      B.CreateZExt(CondI1, IntegerType::getInt32Ty(B.getContext()));
  B.CreateCall(KAssert, {I32});
}

} // namespace seqra_trace
