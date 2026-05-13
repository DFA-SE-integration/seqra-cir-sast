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

llvm::FunctionCallee getKleeAssume(llvm::Module &M);
llvm::FunctionCallee getKleeAssert(llvm::Module &M);

void emitKleeAssumeEq(llvm::IRBuilder<> &B, llvm::FunctionCallee KAssume,
                      llvm::Value *CondI1);

void emitKleeAssertTrue(llvm::IRBuilder<> &B, llvm::FunctionCallee KAssert,
                        llvm::Value *CondI1);

} // namespace seqra_trace
