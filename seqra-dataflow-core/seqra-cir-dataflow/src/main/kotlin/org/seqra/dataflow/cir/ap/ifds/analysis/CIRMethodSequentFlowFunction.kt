package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.FactTypeChecker
import org.seqra.dataflow.ap.ifds.TaintMarkAccessor
import org.seqra.dataflow.ap.ifds.access.ApManager
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.seqra.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.clearField
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.excludeField
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mayReadField
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mayRemoveAfterWrite
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkArrayAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkBaseAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkFieldAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.readFieldTo
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.writeToField
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRCastOpExpr
import org.seqra.ir.api.cir.cfg.CIRDynamicCastOpExpr
import org.seqra.ir.api.cir.cfg.CIRExpr
import org.seqra.ir.api.cir.cfg.CIRGetMemberOpExpr
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRPtrStrideOpExpr
import org.seqra.ir.api.cir.cfg.CIRReturnOpInst
import org.seqra.ir.api.cir.cfg.CIRThrowOpInst
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.common.CommonType

class CIRMethodSequentFlowFunction(
    @Suppress("unused") private val apManager: ApManager,
    private val analysisContext: CIRMethodAnalysisContext,
    private val currentInst: CIRInst,
) : MethodSequentFlowFunction {
    private val factTypeChecker get() = analysisContext.factTypeChecker

    override fun propagateZeroToZero(): Set<Sequent> = buildSet {
        add(Sequent.ZeroToZero)

        applyUnconditionalSources()
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp) = buildSet {
        propagate(
            factAp = currentFactAp,
            unchanged = { add(Sequent.Unchanged) },
            propagateFact = { fact ->
                add(Sequent.ZeroToFact(fact))
            },
            propagateFactWithAccessorExclude = { _, _ ->
                error("Zero to Fact edge can't be refined: $currentFactAp")
            }
        )
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ) = buildSet {
        propagate(
            factAp = currentFactAp,
            unchanged = { add(Sequent.Unchanged) },
            propagateFact = { fact ->
                add(Sequent.FactToFact(initialFactAp, fact))
            },
            propagateFactWithAccessorExclude = { fact, accessor ->
                val refinedInitial = initialFactAp.excludeField(accessor)
                val refinedFact = fact.excludeField(accessor)
                add(Sequent.FactToFact(refinedInitial, refinedFact))
            }
        )
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp
    ) = buildSet {
        propagate(
            factAp = currentFactAp,
            unchanged = { add(Sequent.Unchanged) },
            propagateFact = { fact ->
                add(Sequent.NDFactToFact(initialFacts, fact))
            },
            propagateFactWithAccessorExclude = { _, _ ->
                error("NDF2F edge can't be refined: $currentFactAp")
            }
        )
    }

    private fun propagate(
        factAp: FinalFactAp,
        unchanged: () -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        when (currentInst) {
            is CIRAssignInst -> {
                sequentFlowAssign(
                    currentInst.rhv, currentInst.lhv, factAp,
                    unchanged, propagateFact, propagateFactWithAccessorExclude)
            }

            is CIRReturnOpInst -> {
                unchanged()

                val access = if (currentInst.input.isEmpty())
                    null else accessPathBase(currentInst.input.single())
                if (access == factAp.base) {
                    val resultFact = factAp.rebase(AccessPathBase.Return)
                    propagateFact(resultFact)

                    applyMethodExitSinkRules(AccessPathBase.Return, resultFact)
                } else {
                    applyMethodExitSinkRules(AccessPathBase.Return, factAp)
                }
            }

            is CIRThrowOpInst -> {
                unchanged()

                val access = currentInst.exception_ptr?.let { accessPathBase(it) }
                if (access == factAp.base) {
                    val resultFact = factAp.rebase(AccessPathBase.Exception)
                    propagateFact(resultFact)

                    applyMethodExitSinkRules(AccessPathBase.Exception, resultFact)
                } else {
                    applyMethodExitSinkRules(AccessPathBase.Exception, factAp)
                }
            }

            else -> {
                unchanged()
            }
        }
    }

    private fun sequentFlowAssign(
        assignFrom: CIRExpr,
        assignTo: MLIRValue,
        currentFactAp: FinalFactAp,
        unchanged: () -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit,
    ) {
        val cp = currentInst.method.classpath

        var fact = currentFactAp

        val assignFromAccess = resolveExprAccess(assignFrom)
            ?.apply { fact = filterFactBaseType(cp.findTypeOrNull(assignFrom.type), fact) ?: return }

        val assignToAccess = resolveValueAccess(assignTo)
            .apply { fact = filterFactBaseType(cp.findTypeOrNull(assignTo.type), fact) ?: return }

        val factModified = fact != currentFactAp
        val onUnchanged: (FinalFactAp) -> Unit = if (factModified) propagateFact else { _ -> unchanged() }

        when {
            assignFromAccess?.accessor != null -> {
                check(assignToAccess.accessor == null) { "Complex assignment: $assignTo = $assignFrom" }
                fieldRead(
                    assignToAccess.base, assignFromAccess.base, assignFromAccess.accessor, fact,
                    onUnchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            assignToAccess.accessor != null -> {
                fieldWrite(
                    assignToAccess.base, assignToAccess.accessor, assignFromAccess?.base, fact,
                    onUnchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            else -> simpleAssign(assignToAccess.base, assignFromAccess?.base, fact, onUnchanged, propagateFact)
        }
    }

    private fun resolveExprAccess(expr: CIRExpr): MethodFlowFunctionUtils.Access? = when (expr) {
        // Propagate fact through static
        // TODO check for kind of cast, potentially source of false positive
        is CIRCastOpExpr -> mkBaseAccess(expr.src)
        // Propagate fact through array access
        is CIRPtrStrideOpExpr -> mkArrayAccess(expr.base)
        // Propagate fact through field access
        is CIRGetMemberOpExpr -> mkFieldAccess(expr.addr, expr.name.value, expr.result)
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

    private fun MethodFlowFunctionUtils.Access.filterFactBaseType(
        expectedType: CommonType?,
        factAp: FinalFactAp,
    ): FinalFactAp? {
        if (factAp.base != this.base || expectedType == null) return factAp
        return factTypeChecker.filterFactByLocalType(expectedType, factAp)
    }

    private fun simpleAssign(
        assignTo: AccessPathBase,
        assignFrom: AccessPathBase?,
        factAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
    ) {
        if (assignTo == assignFrom) {
            unchanged(factAp)
            return
        }

        if (assignTo != factAp.base) {
            unchanged(factAp)
        }

        if (assignFrom == factAp.base) {
            propagateFact(factAp.rebase(assignTo))
        }
    }

    private fun fieldRead(
        assignTo: AccessPathBase,
        instance: AccessPathBase,
        accessor: Accessor,
        factAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit,
    ) {
        if (!factAp.mayReadField(instance, accessor)) {
            // Pointer-deriving operations (`cir.ptr_stride`, `cir.get_member`) preserve
            // value-mark facts: a UAF (or any other) mark on the bare pointer value
            // also taints any pointer derived from it via arithmetic / member offset.
            if (factAp.base == instance && factAp.startsWithTaintMark()) {
                propagateFact(factAp.rebase(assignTo))
            }
            unchanged(factAp)
            return
        }

        if (factAp.isAbstract() && accessor !in factAp.exclusions) {
            val nonAbstractAp = factAp.removeAbstraction()
            if (nonAbstractAp != null) {
                fieldRead(
                    assignTo, instance, accessor, nonAbstractAp,
                    unchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            propagateAbstractFactWithFieldExcluded(factAp, accessor, propagateFactWithAccessorExclude)

            return
        }

        check(factAp.startsWithAccessor(accessor))

        val newAp = factAp.readFieldTo(newBase = assignTo, field = accessor)
        propagateFact(newAp)

        if (assignTo != factAp.base) {
            unchanged(factAp)
        }
    }

    private fun fieldWrite(
        instance: AccessPathBase,
        accessor: Accessor,
        assignFrom: AccessPathBase?,
        factAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit,
    ) {
        if (assignFrom == instance) {
            if (factAp.base != instance) {
                unchanged(factAp)
                return
            }

            val auxiliaryBase = AccessPathBase.LocalVar(-1)
            check(auxiliaryBase != instance)

            fieldWrite(
                instance = instance,
                accessor = accessor,
                assignFrom = auxiliaryBase,
                factAp = factAp.rebase(auxiliaryBase),
                unchanged = {
                    if (it.base != auxiliaryBase) {
                        unchanged(it)
                    }
                },
                propagateFact = {
                    if (it.base != auxiliaryBase) {
                        propagateFact(it)
                    }
                },
                propagateFactWithAccessorExclude = { f, a ->
                    if (f.base != auxiliaryBase) {
                        propagateFactWithAccessorExclude(f, a)
                    }
                }
            )

            fieldWrite(
                instance = instance,
                accessor = accessor,
                assignFrom = auxiliaryBase,
                factAp = factAp,
                unchanged = {
                    if (it.base != auxiliaryBase) {
                        unchanged(it)
                    }
                },
                propagateFact = {
                    if (it.base != auxiliaryBase) {
                        propagateFact(it)
                    }
                },
                propagateFactWithAccessorExclude = { f, a ->
                    if (f.base != auxiliaryBase) {
                        propagateFactWithAccessorExclude(f, a)
                    }
                }
            )

            return
        }

        if (factAp.base == assignFrom) {
            unchanged(factAp)

            val newAp = factAp.writeToField(newBase = instance, field = accessor)
            propagateFact(newAp)

            analysisContext.aliasAnalysis?.forEachAliasAtStatement(currentInst, newAp) { aliased ->
                propagateFact(aliased)
            }

            return
        }

        if (factAp.base == instance && accessor is ElementAccessor) {
            propagateFact(factAp)
            return
        }

        if (!factAp.mayRemoveAfterWrite(instance, accessor)) {
            unchanged(factAp)
            return
        }

        if (factAp.isAbstract() && accessor !in factAp.exclusions) {
            val nonAbstractAp = factAp.removeAbstraction()
            if (nonAbstractAp != null) {
                fieldWrite(
                    instance, accessor, assignFrom, nonAbstractAp,
                    unchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            propagateAbstractFactWithFieldExcluded(factAp, accessor, propagateFactWithAccessorExclude)
            return
        }

        check(factAp.startsWithAccessor(accessor))

        val newAp = factAp.clearField(accessor) ?: return
        propagateFact(newAp)
    }

    private fun propagateAbstractFactWithFieldExcluded(
        factAp: FinalFactAp,
        accessor: Accessor,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit,
    ) {
        val abstractAp = apManager.createAbstractAp(factAp.base, factAp.exclusions)
        propagateFactWithAccessorExclude(abstractAp, accessor)
    }

    // TODO
    private fun applyUnconditionalSources() {
        // Current CIR use-after-free configuration is method-call-based and does not define static-field sources.
    }

    // TODO
    private fun applyMethodExitSinkRules(methodResult: AccessPathBase, fact: FinalFactAp) {
        // Current CIR use-after-free configuration does not contain sink rules for method exit.
    }

    /**
     * Returns `true` iff the access path begins with a [TaintMarkAccessor] at top level,
     * i.e. the bare value (no nested field/element) carries a taint mark.
     *
     * Implemented via [FinalFactAp.filterFact] so it works uniformly across [ApMode] backends
     * (tree / cactus / automata) without needing a new method on the [FactAp] interface.
     */
    private fun FinalFactAp.startsWithTaintMark(): Boolean {
        val probe = StartsWithTaintMarkProbe()
        filterFact(probe)
        return probe.found
    }

    private class StartsWithTaintMarkProbe : FactTypeChecker.FactApFilter {
        var found: Boolean = false
            private set

        override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
            if (accessor is TaintMarkAccessor) {
                found = true
            }
            return FactTypeChecker.FilterResult.Reject
        }
    }
}
