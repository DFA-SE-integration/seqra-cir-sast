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

void emitKleeSilentExit(llvm::IRBuilder<> &B,
                        llvm::FunctionCallee KSilentExit, int Status);

void emitKleeAbort(llvm::IRBuilder<> &B, llvm::FunctionCallee KAbort);

/// Emit, at the builder's current insertion point, a guarded abort:
///   if (PtrA != PtrB) klee_abort();
/// The current block is split; the `then` block calls `klee_abort()` and ends
/// in `unreachable`, execution falls through to the original continuation when
/// the pointers are equal. Used by TraceAssertPass to flag, at runtime, a
/// guided path on which a trace edge's precondition (`init == freed slot`) does
/// not hold — an IFDS↔KLEE mismatch — without pruning the path.
void emitKleeAbortIfPtrNe(llvm::IRBuilder<> &B, llvm::FunctionCallee KAbort,
                          llvm::Value *PtrA, llvm::Value *PtrB);

} // namespace seqra_trace
