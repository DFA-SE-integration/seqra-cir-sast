#pragma once

#include "proto/trace.pb.h"

#include "llvm/ADT/DenseMap.h"
#include "llvm/ADT/DenseSet.h"
#include "llvm/ADT/SmallVector.h"

#include <cstdint>
#include <vector>

namespace llvm {
class Function;
class Instruction;
} // namespace llvm

namespace seqra_trace {

uint64_t traceOpIdFromEntry(const trace::method::TraceEntry &E);

void traceReachableFromStart(const trace::method::FullTrace &Ft,
                             llvm::DenseSet<uint32_t> &Reach);

bool traceBuildPathFwd(const trace::method::FullTrace &Ft,
                       std::vector<uint32_t> &OutPath);

bool tracePathHasSourceStart(
    const trace::method::FullTrace &Ft,
    const std::vector<uint32_t> &Path);

llvm::Instruction *traceGetInsnForTraceEntry(
    const trace::method::FullTrace &Ft, uint32_t EntryId,
    const llvm::DenseMap<uint64_t, llvm::Instruction *> &OpTab,
    const llvm::Function *F);

bool traceTrySelectStartFullTrace(const trace::Trace &Pb,
                                  const llvm::Function *F,
                                  const trace::method::FullTrace **OutFt);

bool traceTrySelectSinkFullTrace(const trace::Trace &Pb,
                                 const llvm::Function *F,
                                 const trace::method::FullTrace **OutFt);

} // namespace seqra_trace
