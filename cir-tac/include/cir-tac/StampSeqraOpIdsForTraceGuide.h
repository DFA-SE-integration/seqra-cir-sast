#pragma once

namespace mlir {
class ModuleOp;
}

/// Match `cir-ser-proto`: populate `OpCache` via `OpSerializer`, then
/// `AliasSerializer::stampAndBuildContext` so CIR→LLVM lowering emits
/// `!seqra.op` metadata for `TraceGuidePass` / `makeOpIndex`.
void stampSeqraOpIdsForTraceGuide(mlir::ModuleOp module);
