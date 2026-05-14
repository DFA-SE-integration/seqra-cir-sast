# Bug: CIR taint trace fact representation mismatch (forward IFDS vs. backward `TraceResolver`)

**Status: resolved** (see “Fix” below). `TraceSerializer` requires a full trace graph again (single entry-point node + serializable source-to-sink trace).

## Symptom (historical)

For every `bad` sample of the CWE-416 Juliet corpus (e.g. `CWE416_Use_After_Free__malloc_free_char_01_bad`), `CIRProjectAnalyzer.analyze(...)` reported a UAF vulnerability (the sink fired), but `VulnerabilityWithTrace.trace.entryPointToStart` ended up degenerate:

- `entryPointToStart.entryPoints` was **empty**;
- `sourceToSinkTrace.startNodes` and `.sinkNodes` were **empty**;
- `summaryTraces=0` reported by `TraceResolver.resolveTrace`.

As a consequence, `TraceSerializer` could not build the full proto graph for many samples.

## Diagnosis (what we have proved)

After turning on diagnostic logging in ` TraceResolver.resolveTrace` and `MethodTraceResolver.resolveIntraProceduralTraceEdge`, the picture for `*_01_bad` was:

```text
[TraceResolver] empty trace: vuln=use-after-free
  method=fun CWE416_Use_After_Free__malloc_free_char_01_bad() -> !cir.void
  summaryTraces=0

[MethodTraceResolver] empty trace-edge:
  method=fun CWE416_Use_After_Free__malloc_free_char_01_bad() -> !cir.void
  includeStatement=false
  fact = var(42)![use-after-free].$.*/*           # what the sink emits
  stmt = CIRCallOpInst(callee=printLine, id=43)
  zeroToFactAtStmt(pattern=fact) = 1 [var(42).&![use-after-free].$]   # what IFDS actually produced
  factToFactAtStmt(pattern=fact) = 0
  ndFactToFactAtStmt(pattern=fact) = 0
```

So:

1. The sink (`use-after-free` rule, `ContainsMark(USE_AFTER_FREE_MARK, Argument(0))` on calls) **did fire** during forward IFDS — the `TaintSinkTracker` recorded a `TaintVulnerabilityWithFact`.
2. The backward `MethodTraceResolver.resolveIntraProceduralTraceEdge` was unable to find any IFDS edge at the same statement whose final-fact "contains" the sink's `InitialFactAp`. Hence `summaryTraces=0`, hence the empty trace graph, hence the empty `entryPointToStart`.

The access-paths differed structurally:

| Where                                                      | Access path                              | Meaning |
|------------------------------------------------------------|------------------------------------------|---|
| Forward IFDS edge present at the sink statement            | `var(42).&![use-after-free].$`           | Mark is attached **under** a `ReferenceAccessor` of `var(42)` — i.e. "the thing `var(42)` references is tainted". |
| Sink fact pattern emitted by backward `ContainsMark` precondition literals (via `CIRMethodCallPrecondition` → `createPositionWithTaintMark`) | `var(42)![use-after-free].$.*/*` | Mark is attached **directly** to `var(42)` — i.e. "`var(42)` itself is tainted". |

`searchInitialFacts` in `MethodAnalyzerEdgeSearcher` uses  
`override fun matchFact(factAtStatement: FinalFactAp, targetFactPattern: InitialFactAp): Boolean = factAtStatement.contains(targetFactPattern)` — a strict containment check on the path shape. The two paths above did not satisfy it, even though semantically they described the same condition modulo aliasing.

Notably, the **forward** sink check uses a different matcher: `InitialFactReader.containsPositionWithTaintMark` first tries the literal `position`, and if `position` is `PositionAccess.Simple`, also tries `PositionAccess.Complex(position, ElementAccessor)`. That extra "look under element" attempt is part of why the sink fires forward while a single literal precondition pattern failed backward.

## Root cause

An asymmetry between forward CIR taint propagation / call-to-start mapping and backward trace precondition construction:

* Forward matchers and `CIRMethodCallFactMapper.mapMethodCallToStartFlowFact` (with alias analysis) can attach a leading `ReferenceAccessor` (“deref bridge”) and treat element vs base.
* Backward `CIRMethodCallPrecondition` previously emitted a single `ContainsMark` literal shape, and `mapMethodExitToReturnFlowFact` did not strip the symmetric `ReferenceAccessor` when mapping callee exit facts back to the caller.

Either way, the two sides must agree; the strict `matchFact` in `MethodTraceResolver` was left unchanged.

## Fix (implemented)

1. **`CIRMethodCallPrecondition`**: For each `ContainsMark` literal in pass-rule DNF, enumerate the same position variants forward matching considers for a simple base: `p`, `p.element`, `p.&` (`cirMirrorPreconditionPositionAccessCandidates` / `preconditionPositionAccessCandidates`), then map each through `mapMethodExitToReturnFlowFact`.
2. **`CIRMethodCallFactMapper.mapMethodExitToReturnFlowFact`**: When a callee `Argument(i)` fact has a leading `ReferenceAccessor`, strip it before rebasing to the caller’s argument SSA (symmetric to the call-to-start deref bridge).
3. **`CIRMethodSequentPrecondition`**: Mirror forward `CIRMethodSequentFlowFunction` by resolving `MLIRValueRef` explicitly in `resolveValueAccess`.

## Where to look in code

* [CIRMethodSequentFlowFunction.kt](../seqra-dataflow-core/seqra-cir-dataflow/src/main/kotlin/org/seqra/dataflow/cir/ap/ifds/analysis/CIRMethodSequentFlowFunction.kt) — `applyUseAfterFreeDereferenceSink` / `emitUseAfterFreeDereferenceSink`.
* [FactReader.kt](../seqra-dataflow-core/seqra-cir-dataflow/src/main/kotlin/org/seqra/dataflow/cir/ap/ifds/taint/FactReader.kt) — `containsPositionWithTaintMark`.
* [MethodTraceResolver.kt](../seqra-dataflow-core/seqra-dataflow/src/main/kotlin/org/seqra/dataflow/ap/ifds/trace/MethodTraceResolver.kt) — strict `matchFact`.
* [CIRMethodCallPrecondition.kt](../seqra-dataflow-core/seqra-cir-dataflow/src/main/kotlin/org/seqra/dataflow/cir/ap/ifds/analysis/CIRMethodCallPrecondition.kt) — `preconditionDnf`, `cirMirrorPreconditionPositionAccessCandidates`.
* [CIRMethodCallFactMapper.kt](../seqra-dataflow-core/seqra-cir-dataflow/src/main/kotlin/org/seqra/dataflow/cir/ap/ifds/CIRMethodCallFactMapper.kt) — exit-to-return `ReferenceAccessor` strip.

## Follow-ups

* **JVM:** [JIRMethodCallPrecondition.kt](../seqra-dataflow-core/seqra-jvm-dataflow/src/main/kotlin/org/seqra/dataflow/jvm/ap/ifds/trace/JIRMethodCallPrecondition.kt) still uses a single-variant `ContainsMark` literal expansion; consider the same DNF mirroring if JVM traces hit analogous shape mismatches (without CIR’s `ReferenceAccessor` bridge).
