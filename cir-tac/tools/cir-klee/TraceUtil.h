#pragma once

#include "proto/trace.pb.h"

#include "llvm/ADT/DenseMap.h"
#include "llvm/ADT/DenseSet.h"
#include "llvm/ADT/SmallVector.h"
#include "llvm/IR/IRBuilder.h"

#include <cstdint>
#include <optional>

namespace llvm {
class Function;
class Instruction;
class Module;
class Value;
class FunctionCallee;
} // namespace llvm

namespace seqra_trace {

llvm::StringLiteral seqraOpMetadataName();

std::optional<uint64_t> seqraOpId(const llvm::Instruction *I);

llvm::DenseMap<uint64_t, llvm::Instruction *>
makeOpIndex(llvm::Function &F);

llvm::Instruction *
lookupInsn(const llvm::DenseMap<uint64_t, llvm::Instruction *> &Tab,
           uint64_t OpId);

void successorsOf(const trace::method::FullTrace &Ft, uint32_t U,
                  llvm::SmallVectorImpl<uint32_t> &Succ);

void bfsReachable(const trace::method::FullTrace &Ft,
                  llvm::DenseSet<uint32_t> &Reach);

llvm::FunctionCallee getKleeSilentExit(llvm::Module &M);
llvm::FunctionCallee getKleeAbort(llvm::Module &M);
llvm::FunctionCallee getKleeAssume(llvm::Module &M);

void emitKleeSilentExit(llvm::IRBuilder<> &B,
                        llvm::FunctionCallee KSilentExit, int Status);

void emitKleeAbort(llvm::IRBuilder<> &B, llvm::FunctionCallee KAbort);

/// KLEE runtime: `void klee_assume(int cond);` — pass i1 zext to i32.
void emitKleeAssumeI1(llvm::IRBuilder<> &B, llvm::FunctionCallee KAssume,
                      llvm::Value *CondI1);

void emitKleeAssumePtrEq(llvm::IRBuilder<> &B, llvm::FunctionCallee KAssume,
                         llvm::Value *PtrA, llvm::Value *PtrB);

} // namespace seqra_trace
