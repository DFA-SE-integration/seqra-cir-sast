package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.ReferenceAccessor
import org.seqra.dataflow.ap.ifds.access.ApManager
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.seqra.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.seqra.dataflow.ap.ifds.taint.TaintSinkTracker
import org.seqra.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.seqra.dataflow.configuration.core.ConstantTrue
import org.seqra.dataflow.configuration.core.TaintMark
import org.seqra.dataflow.configuration.core.TaintMethodSink
import org.seqra.dataflow.configuration.core.TaintSinkMeta
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
import org.seqra.dataflow.cir.ap.ifds.taint.FinalFactReader
import org.seqra.dataflow.cir.ap.ifds.taint.PositionAccess
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
import org.seqra.ir.api.cir.cfg.MLIRValueRef
import org.seqra.ir.api.common.CommonType

class CIRMethodSequentFlowFunction(
    @Suppress("unused") private val apManager: ApManager,
    private val analysisContext: CIRMethodAnalysisContext,
    private val currentInst: CIRInst,
) : MethodSequentFlowFunction {
    private val factTypeChecker get() = analysisContext.factTypeChecker

    companion object {
        /** Must match built-in UAF mark in [org.seqra.cir.sast.dataflow.CIRTaintAnalyzer]. */
        private const val USE_AFTER_FREE_MARK_NAME = "use-after-free"

        private const val USE_AFTER_FREE_DEREF_SINK_ID = "use-after-free-deref"
    }

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
                applyUseAfterFreeDereferenceSink(factAp, currentInst.lhv, currentInst.rhv)
                sequentFlowAssign(
                    currentInst.rhv, currentInst.lhv, factAp,
                    unchanged, propagateFact, propagateFactWithAccessorExclude)
            }

            is CIRReturnOpInst -> {
                unchanged()

                val retInput = currentInst.input.singleOrNull()
                val access = retInput?.let { accessPathBase(it) }
                var propagated = false
                if (access == factAp.base) {
                    val resultFact = factAp.rebase(AccessPathBase.Return)
                    propagateFact(resultFact)

                    applyMethodExitSinkRules(AccessPathBase.Return, resultFact)
                    propagated = true
                } else {
                    analysisContext.aliasAnalysis?.forEachAlias(factAp) { aliased ->
                        if (aliased.base == access) {
                            val resultFact = aliased.rebase(AccessPathBase.Return)
                            propagateFact(resultFact)
                            applyMethodExitSinkRules(AccessPathBase.Return, resultFact)
                            propagated = true
                        }
                    }
                }

                // The ClangIR `__retval` lowering (`store %v, %slot; %r = load %slot; return %r`) needs
                // no special handling: the load/store copy chain already rebases the fact onto the return
                // operand `%r`, so the direct `access == base` match above carries it to Return.
                if (!propagated) {
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

    private fun applyUseAfterFreeDereferenceSink(factAp: FinalFactAp, lhv: MLIRValue, rhv: CIRExpr) {
        if (lhv !is MLIROpValue) return

        if (rhv is CIRPtrStrideOpExpr && applyUseAfterFreePtrStrideSink(factAp, rhv)) {
            return
        }

        if (rhv is CIRGetMemberOpExpr && applyUseAfterFreeGetMemberSink(factAp, rhv)) {
            return
        }

        val loadAddr = (rhv as? MLIRValueRef)?.value ?: return
        if (!loadAddressAliasesFactBase(loadAddr, factAp.base)) return

        val reader = FinalFactReader(factAp, apManager)
        val mark = TaintMark(USE_AFTER_FREE_MARK_NAME)
        val markAtSimpleBase =
            reader.containsPositionWithTaintMark(PositionAccess.Simple(factAp.base), mark)
        val markUnderRef = reader.containsPositionWithTaintMark(
            PositionAccess.Complex(PositionAccess.Simple(factAp.base), ReferenceAccessor),
            mark,
        )
        if (!markAtSimpleBase && !markUnderRef) {
            return
        }

        val positionForSink =
            if (markUnderRef && !markAtSimpleBase) {
                PositionAccess.Complex(PositionAccess.Simple(factAp.base), ReferenceAccessor)
            } else {
                PositionAccess.Simple(factAp.base)
            }
        emitUseAfterFreeDereferenceSink(reader, positionForSink)
    }

    /**
     * Pointer arithmetic (`cir.ptr_stride`) uses an array-element access path; treat it as a dereference-style UAF sink
     * when the fact carries `use-after-free` at `[base]` (element of base pointer).
     */
    private fun applyUseAfterFreePtrStrideSink(factAp: FinalFactAp, rhv: CIRPtrStrideOpExpr): Boolean {
        val mark = TaintMark(USE_AFTER_FREE_MARK_NAME)
        val candidates = buildList {
            add(factAp)
            analysisContext.aliasAnalysis?.forEachAlias(factAp) { add(it) }
        }
        for (candidate in candidates) {
            if (!loadAddressAliasesFactBase(rhv.base, candidate.base)) continue
            val reader = FinalFactReader(candidate, apManager)
            val elemPos = PositionAccess.Complex(PositionAccess.Simple(candidate.base), ElementAccessor)
            if (!reader.containsPositionWithTaintMark(elemPos, mark)) continue
            emitUseAfterFreeDereferenceSink(reader, elemPos)
            return true
        }
        return false
    }

    /**
     * Field access (`cir.get_member`) computes the address of a struct member; treat it as a dereference-style UAF
     * sink when the fact carries `use-after-free` at the field position `.field` of the (aliased) base pointer.
     * Mirrors [applyUseAfterFreePtrStrideSink], with the member accessor in place of the array element.
     */
    private fun applyUseAfterFreeGetMemberSink(factAp: FinalFactAp, rhv: CIRGetMemberOpExpr): Boolean {
        val mark = TaintMark(USE_AFTER_FREE_MARK_NAME)
        val fieldAccessor = mkFieldAccess(rhv.addr, rhv.name.value, rhv.result).accessor ?: return false
        val candidates = buildList {
            add(factAp)
            analysisContext.aliasAnalysis?.forEachAlias(factAp) { add(it) }
        }
        for (candidate in candidates) {
            if (!loadAddressAliasesFactBase(rhv.addr, candidate.base)) continue
            val reader = FinalFactReader(candidate, apManager)
            val fieldPos = PositionAccess.Complex(PositionAccess.Simple(candidate.base), fieldAccessor)
            if (!reader.containsPositionWithTaintMark(fieldPos, mark)) continue
            emitUseAfterFreeDereferenceSink(reader, fieldPos)
            return true
        }
        return false
    }

    private fun emitUseAfterFreeDereferenceSink(reader: FinalFactReader, positionAccess: PositionAccess) {
        val rule = TaintMethodSink(
            method = currentInst.location.method,
            condition = ConstantTrue,
            id = USE_AFTER_FREE_DEREF_SINK_ID,
            meta = TaintSinkMeta(
                message = "Freed value is dereferenced after free",
                severity = CommonTaintConfigurationSinkMeta.Severity.Error,
                cwe = listOf(416),
            ),
        )
        val initialFact = reader.createInitialFactWithTaintMark(
            positionAccess,
            TaintMark(USE_AFTER_FREE_MARK_NAME),
        )
        analysisContext.taint.taintSinkTracker.addVulnerability(
            analysisContext.methodEntryPoint,
            setOf(initialFact),
            currentInst,
            rule,
            vulnerabilityTriggerPosition = TaintSinkTracker.VulnerabilityTriggerPosition.BEFORE_INST,
        )
    }

    private fun loadAddressAliasesFactBase(loadAddress: MLIRValue, factBase: AccessPathBase): Boolean {
        val loadBase = accessPathBase(loadAddress) ?: return false
        return analysisContext.aliasAnalysis?.basesAliasSymmetric(loadBase, factBase)
            ?: (loadBase == factBase)
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
            // TODO
            // Compound element/field copy: `*A.acc = *B.acc` (e.g. `reversedString[j] = aString[i-j-1]`
            // after CIRLoadStoreFeature). Forward IFDS skips fact propagation through this shape; the
            // mirror in [CIRMethodSequentPrecondition.sequentAssignPrecondition] does the same so trace
            // resolution stays in sync.
            assignFromAccess?.accessor != null && assignToAccess.accessor != null -> onUnchanged(fact)

            assignFromAccess?.accessor != null -> {
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
        // Propagate through cast src unconditionally; [CIRLocalAliasAnalysis] instead treats only
        // pointer-transparent cast kinds as equivalent for alias queries — different policy by design.
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
        // Lowered store/load uses MLIRValueRef(lhs); recurse so assignInstByLhv resolves through ptr_stride/get_member.
        is MLIRValueRef -> resolveValueAccess(value.value)
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
        assignTo: AccessPathBase?,
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
            assignTo?.let { propagateFact(factAp.rebase(it)) }
        }
    }

    private fun fieldRead(
        assignTo: AccessPathBase?,
        instance: AccessPathBase?,
        accessor: Accessor,
        factAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit,
    ) {
        if (!factAp.mayReadField(instance, accessor)) {
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

        val newAp = assignTo?.let { factAp.readFieldTo(newBase = it, field = accessor) }
        newAp?.let { propagateFact(it) }

        if (assignTo != factAp.base) {
            unchanged(factAp)
        }
    }

    private fun fieldWrite(
        instance: AccessPathBase?,
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

            val newAp = instance?.let { factAp.writeToField(newBase = it, field = accessor) }
            newAp?.let { propagateFact(it) }

            newAp?.let {
                analysisContext.aliasAnalysis?.forEachAliasAtStatement(currentInst, it) { aliased ->
                    propagateFact(aliased)
                }
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
}
