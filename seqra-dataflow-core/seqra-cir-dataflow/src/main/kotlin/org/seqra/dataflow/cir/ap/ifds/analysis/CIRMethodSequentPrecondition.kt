package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.ElementAccessor
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

        preconditionForFact(fact)?.let {
            results += PreconditionFactsForInitialFact(fact, it)
        }

        results.unconditionalSourcesPrecondition(fact)

        analysisContext.aliasAnalysis?.forEachAlias(fact) { aliasedFact ->
            preconditionForFact(aliasedFact)?.let {
                results += PreconditionFactsForInitialFact(aliasedFact, it)
            }

            results.unconditionalSourcesPrecondition(aliasedFact)
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
            assignFromAccess?.accessor != null -> {
                check(assignToAccess.accessor == null) { "Complex assignment: $assignTo = $assignFrom" }
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
        is MLIROpValue -> currentInst.method.assignInstByLhv[value]
            ?.takeIf { it.location.index < currentInst.location.index }
            ?.rhv
            ?.let(::resolveExprAccess)
            ?: mkBaseAccess(value)

        else -> mkBaseAccess(value)
    }
}
