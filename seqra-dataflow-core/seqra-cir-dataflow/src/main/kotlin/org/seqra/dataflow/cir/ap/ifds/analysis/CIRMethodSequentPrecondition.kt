package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.ReferenceAccessor
import org.seqra.dataflow.ap.ifds.access.ApManager
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.seqra.dataflow.ap.ifds.trace.MethodSequentPrecondition.*
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkArrayAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkBaseAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkFieldAccess
import org.seqra.ir.api.cir.cfg.*

class CIRMethodSequentPrecondition(
    @Suppress("unused") private val apManager: ApManager,
    private val currentInst: CIRInst,
    private val analysisContext: CIRMethodAnalysisContext
) : MethodSequentPrecondition {

    override fun factPrecondition(
        fact: InitialFactAp
    ): SequentPrecondition {
        val results = mutableListOf<SequentPreconditionFacts>()
        val seen = hashSetOf<InitialFactAp>()

        fun tryFact(f: InitialFactAp) {
            if (!seen.add(f)) return
            preconditionForFact(f)?.let {
                results += PreconditionFactsForInitialFact(f, it)
            }
            results.unconditionalSourcesPrecondition(f)
        }

        tryFact(fact)

        // Mirror MethodTraceResolver.traceResolutionTargetPatterns: the IFDS forward index can carry
        // facts shaped with or without a leading [ReferenceAccessor] / [ElementAccessor] (deref bridge
        // / element fallback). Widening here lets the backward sequent recognise the `.&`-variant of
        // a slot fact (`var(p).&!mark`) when the trace edge carries the original sink/target shape
        // (`var(p)!mark`), so [containsEntryEdge] strict match against the index actually succeeds.
        runCatching { fact.prependAccessor(ReferenceAccessor) }.getOrNull()?.let(::tryFact)
        fact.readAccessor(ReferenceAccessor)?.let(::tryFact)
        runCatching { fact.prependAccessor(ElementAccessor) }.getOrNull()?.let(::tryFact)
        fact.readAccessor(ElementAccessor)?.let(::tryFact)

        analysisContext.aliasAnalysis?.forEachAlias(fact) { aliasedFact ->
            tryFact(aliasedFact)
        }

        return if (results.isEmpty()) {
            SequentPrecondition.Unchanged
        } else {
            SequentPrecondition.Facts(results)
        }
    }

    private fun preconditionForFact(fact: InitialFactAp): List<InitialFactAp>? {
        when (currentInst) {
            is CIRAssignInst -> {
                return sequentAssignPrecondition(currentInst.rhv, currentInst.lhv, fact)
            }

            is CIRReturnOpInst -> {
                if (fact.base !is AccessPathBase.Return) {
                    return null
                }

                val base = if (currentInst.input.isEmpty())
                    null else accessPathBase(currentInst.input.single())

                if (base != null)
                    return listOf(fact.rebase(base))
                return emptyList()
            }

            is CIRThrowOpInst -> {
                if (fact.base !is AccessPathBase.Exception) {
                    return null
                }

                val base = currentInst.exception_ptr?.let { accessPathBase(it) }

                if (base != null)
                    return listOf(fact.rebase(base))
                return emptyList()
            }

            else -> return null
        }
    }

    private fun sequentAssignPrecondition(
        assignFrom: CIRExpr,
        assignTo: MLIRValue,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        val assignFromAccess = resolveExprAccess(assignFrom)

        val assignToAccess = resolveValueAccess(assignTo)

        return when {
            // TODO
            // Compound element/field copy: `*A.acc = *B.acc` (e.g. `reversedString[j] = aString[i-j-1]`
            // after CIRLoadStoreFeature — both sides resolve to `Access(base, ElementAccessor)`).
            // Precondition decomposition would need a synthetic temp; for now skip rather than throw
            // — the trace will miss the byte-copy edge but won't crash trace resolution.
            assignFromAccess?.accessor != null && assignToAccess.accessor != null -> null

            assignFromAccess?.accessor != null -> {
                fieldRead(
                    assignToAccess.base, assignFromAccess.base, assignFromAccess.accessor, fact
                )
            }

            assignToAccess.accessor != null -> {
                fieldWrite(
                    assignToAccess.base, assignToAccess.accessor, assignFromAccess?.base, fact
                )
            }

            else -> simpleAssign(assignToAccess.base, assignFromAccess?.base, fact)
        }
    }

    private fun simpleAssign(
        assignTo: AccessPathBase?,
        assignFrom: AccessPathBase?,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (assignTo == assignFrom || assignTo != fact.base) {
            return null
        }

        if (assignFrom != null) {
            return listOf(fact.rebase(assignFrom))
        }

        // kill fact
        return emptyList()
    }

    private fun fieldRead(
        assignTo: AccessPathBase?,
        instance: AccessPathBase?,
        accessor: Accessor,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (fact.base != assignTo) {
            return null
        }

        if (instance != null)
            return listOf(fact.prependAccessor(accessor).rebase(instance))
        return emptyList()
    }

    private fun fieldWrite(
        instance: AccessPathBase?,
        accessor: Accessor,
        assignFrom: AccessPathBase?,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (fact.base != instance || !fact.startsWithAccessor(accessor)) {
            return null
        }

        val facts = buildList {
            val factAtAccessor = fact.readAccessor(accessor) ?: error("No fact")
            if (assignFrom != null) {
                this += factAtAccessor.rebase(assignFrom)
            }

            val otherFact = fact.clearAccessor(accessor)
            if (otherFact != null) {
                this += otherFact
            }

            if (accessor is ElementAccessor) {
                this += factAtAccessor.prependAccessor(ElementAccessor)
            }
        }

        return facts
    }

    // TODO
    private fun MutableList<SequentPreconditionFacts>.unconditionalSourcesPrecondition(fact: InitialFactAp) {
        // Current CIR use-after-free configuration is method-call-based and does not define static-field sources.
    }

    private fun resolveExprAccess(expr: CIRExpr): MethodFlowFunctionUtils.Access? = when (expr) {
        // Propagate through cast src unconditionally; see [CIRLocalAliasAnalysis] for cast-kind filtering on aliases.
        is CIRCastOpExpr -> mkBaseAccess(expr.src)
        // Propagate fact through array access
        is CIRPtrStrideOpExpr -> mkArrayAccess(expr.base)
        // Propagate fact through field access
        is CIRGetMemberOpExpr -> mkFieldAccess(expr.addr, expr.name.value, expr.result)
        // TODO is CIRGetRuntimeMemberOpInst
        // Propagate fact through dynamic cast
        is CIRDynamicCastOpExpr -> mkBaseAccess(expr.src)
        // Propagate fact through pure value
        is MLIRValue -> resolveValueAccess(expr)
        else -> null
    }

    private fun resolveValueAccess(value: MLIRValue): MethodFlowFunctionUtils.Access = when (value) {
        is MLIRValueRef -> resolveValueAccess(value.value)
        is MLIROpValue -> currentInst.method.assignInstByLhv[value]
            ?.takeIf { it.location.index < currentInst.location.index }
            ?.rhv
            ?.let(::resolveExprAccess)
            ?: mkBaseAccess(value)

        else -> mkBaseAccess(value)
    }
}
