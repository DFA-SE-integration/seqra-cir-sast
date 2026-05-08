package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.ReferenceAccessor
import org.seqra.dataflow.ap.ifds.FactTypeChecker
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.seqra.dataflow.ap.ifds.access.FactAp
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.seqra.ir.api.cir.cfg.CIRDirectCall
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRType
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.cfg.CommonCallExpr
import org.seqra.ir.api.common.cfg.CommonInst
import org.seqra.ir.api.common.cfg.CommonValue

object CIRMethodCallFactMapper : MethodCallFactMapper {
    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker
    ): List<FinalFactAp> {
        cirDowncast<CIRDirectCall>(callStatement)
        return mapMethodExitToReturnFlowFact(callStatement, factAp, checker)
    }

    private fun mapMethodExitToReturnFlowFact(
        callStatement: CIRDirectCall,
        factAp: FinalFactAp,
        checker: FactTypeChecker
    ): List<FinalFactAp> = mapMethodExitToReturnFlowFact(
        callStatement = callStatement,
        factAp = factAp,
        checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
        rebaseFact = { f, base -> f.rebase(base) }
    )

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: InitialFactAp
    ): List<InitialFactAp> {
        cirDowncast<CIRDirectCall>(callStatement)
        return mapMethodExitToReturnFlowFact(callStatement, factAp)
    }

    private fun mapMethodExitToReturnFlowFact(
        callStatement: CIRDirectCall,
        factAp: InitialFactAp
    ): List<InitialFactAp> = mapMethodExitToReturnFlowFact(
        callStatement = callStatement,
        factAp = factAp,
        checkFactType = { _, f -> f },
        rebaseFact = { f, base -> f.rebase(base) }
    )

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit
    ) {
        cirDowncast<CIRFunction>(callee)
        cirDowncast<CIRDirectCall>(callExpr)
        mapMethodCallToStartFlowFactImpl(
            callExpr = callExpr,
            factAp = factAp,
            checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
            aa = null,
            prependAccessor = { f, acc -> f.prependAccessor(acc) },
            onMappedFact = onMappedFact,
        )
    }

    /**
     * Same as [mapMethodCallToStartFlowFact] but uses [aa] so facts on a loaded pointer can map to
     * [AccessPathBase.Argument] with [ReferenceAccessor] when the call passes the load address (deref bridge).
     */
    fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        aa: CIRLocalAliasAnalysis?,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit,
    ) {
        cirDowncast<CIRFunction>(callee)
        cirDowncast<CIRDirectCall>(callExpr)
        mapMethodCallToStartFlowFactImpl(
            callExpr = callExpr,
            factAp = factAp,
            checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
            aa = aa,
            prependAccessor = { f, acc -> f.prependAccessor(acc) },
            onMappedFact = onMappedFact,
        )
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit
    ) {
        cirDowncast<CIRFunction>(callee)
        cirDowncast<CIRDirectCall>(callExpr)
        mapMethodCallToStartFlowFactImpl(
            callExpr = callExpr,
            factAp = fact,
            checkFactType = { _, f -> f },
            aa = null,
            prependAccessor = { f, acc -> f.prependAccessor(acc) },
            onMappedFact = onMappedFact,
        )
    }

    fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        aa: CIRLocalAliasAnalysis?,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit,
    ) {
        cirDowncast<CIRFunction>(callee)
        cirDowncast<CIRDirectCall>(callExpr)
        mapMethodCallToStartFlowFactImpl(
            callExpr = callExpr,
            factAp = fact,
            checkFactType = { _, f -> f },
            aa = aa,
            prependAccessor = { f, acc -> f.prependAccessor(acc) },
            onMappedFact = onMappedFact,
        )
    }

    override fun factIsRelevantToMethodCall(
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp
    ): Boolean {
        cirDowncast<MLIRValue?>(returnValue)
        cirDowncast<CIRDirectCall>(callExpr)
        return factIsRelevantToMethodCall(returnValue, callExpr, factAp.base, null)
    }

    fun factIsRelevantToMethodCall(
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp,
        aa: CIRLocalAliasAnalysis?
    ): Boolean {
        cirDowncast<MLIRValue?>(returnValue)
        cirDowncast<CIRDirectCall>(callExpr)
        return factIsRelevantToMethodCall(returnValue, callExpr, factAp.base, aa)
    }

    override fun isValidMethodExitFact(factAp: FactAp): Boolean =
        factAp.base !is AccessPathBase.LocalVar

    /* */

    private inline fun <F : FactAp> mapMethodExitToReturnFlowFact(
        callStatement: CIRDirectCall,
        factAp: F,
        checkFactType: (MLIRType, F) -> F?,
        rebaseFact: (F, AccessPathBase) -> F,
    ): List<F> = listOfNotNull(
        mapMethodExitToReturnSingleFlowFact(
            callStatement,
            factAp,
            checkFactType,
            rebaseFact
        )
    )

    private inline fun <F : FactAp> mapMethodExitToReturnSingleFlowFact(
        callStatement: CIRDirectCall,
        factAp: F,
        checkFactType: (MLIRType, F) -> F?,
        rebaseFact: (F, AccessPathBase) -> F,
    ): F? {
        return when (val base = factAp.base) {
            is AccessPathBase.ClassStatic,
            is AccessPathBase.Constant -> factAp

            is AccessPathBase.Argument -> {
                val argExpr = callStatement.callArgs().getOrNull(base.idx) ?: return null
                val newBase = accessPathBase(argExpr) ?: return null
                if (newBase is AccessPathBase.Constant) return null

                val stripped = factAp.startsWithAccessor(ReferenceAccessor)
                val factForExit: F =
                    if (stripped) {
                        @Suppress("UNCHECKED_CAST")
                        when (factAp) {
                            is InitialFactAp ->
                                factAp.readAccessor(ReferenceAccessor) as? F ?: return null

                            is FinalFactAp ->
                                factAp.readAccessor(ReferenceAccessor) as? F ?: return null

                            else -> return null
                        }
                    } else {
                        factAp
                    }

                val checkedFact =
                    callStatement.argType(base.idx)?.let { checkFactType(it, factForExit) } ?: return null
                rebaseFact(checkedFact, newBase)
            }

            AccessPathBase.Return -> {
                val returnValue = callStatement.resultValueOrNull()
                val newBase = returnValue?.let { accessPathBase(it) }
                val resultType = callStatement.resultType()
                val checkedFact = resultType?.let { checkFactType(it, factAp) }
                val finalResult: F? = when {
                    returnValue == null -> null
                    newBase == null || newBase is AccessPathBase.Constant -> null
                    checkedFact == null -> null
                    else -> rebaseFact(checkedFact, newBase)
                }
                
                finalResult
            }

            AccessPathBase.This,
            AccessPathBase.Exception,
            is AccessPathBase.LocalVar -> null
        }
    }

    private inline fun <F : FactAp> mapMethodCallToStartFlowFactImpl(
        callExpr: CIRDirectCall,
        factAp: F,
        checkFactType: (MLIRType, F) -> F?,
        aa: CIRLocalAliasAnalysis?,
        prependAccessor: (F, Accessor) -> F,
        onMappedFact: (F, AccessPathBase) -> Unit,
    ) {
        val factBase = factAp.base

        if (factBase is AccessPathBase.ClassStatic) {
            onMappedFact(factAp, factBase)
        }

        for ((i, arg) in callExpr.arg_ops.withIndex()) {
            val argBase = accessPathBaseForCallArg(aa, arg) ?: continue
            val argMlirType = callExpr.argType(i)
            val checkedFact = argMlirType?.let { checkFactType(it, factAp) }
            val factOk = checkedFact ?: continue
            when {
                argBase == factBase ->
                    onMappedFact(factOk, AccessPathBase.Argument(i))

                // Forward deref bridge: factBase is loaded from argBase's slot (or a derivation
                // ancestor). Prepend [ReferenceAccessor] so the fact maps to `Argument(i).[Ref]`.
                // NOTE: must be checked BEFORE any must-alias direct-map to avoid losing the
                // `*slot` vs `slot` distinction — SeaDSA cells often union a slot with the value
                // loaded from it, and they need different mapping shapes.
                aa != null && aa.loadedFromSlot(factBase, argBase) ->
                    onMappedFact(
                        prependAccessor(factOk, ReferenceAccessor),
                        AccessPathBase.Argument(i),
                    )

                // Reverse deref bridge: argBase is loaded from factBase (`free(load(slot))`).
                // The fact's leading [ReferenceAccessor] represents "value at slot" which equals the
                // loaded value passed as Argument(i). Strip the leading `.&` and map to Argument(i).
                aa != null && aa.loadedFromSlot(argBase, factBase) && factOk.startsWithAccessor(ReferenceAccessor) -> {
                    @Suppress("UNCHECKED_CAST")
                    val stripped: F? = when (factOk) {
                        is InitialFactAp -> factOk.readAccessor(ReferenceAccessor) as? F
                        is FinalFactAp -> factOk.readAccessor(ReferenceAccessor) as? F
                        else -> null
                    }
                    if (stripped != null) {
                        onMappedFact(stripped, AccessPathBase.Argument(i))
                    }
                }

                MethodFlowFunctionUtils.zeroStridePtrStrideRhsBase(callExpr.location.method, arg) == factBase ->
                    onMappedFact(factOk, AccessPathBase.Argument(i))
            }
        }
    }

    private fun accessPathBaseForCallArg(aa: CIRLocalAliasAnalysis?, arg: MLIRValue): AccessPathBase? =
        aa?.canonicalAccessPathBase(arg) ?: accessPathBase(arg)

    private fun factIsRelevantToMethodCall(
        returnValue: MLIRValue?,
        callExpr: CIRDirectCall,
        factBase: AccessPathBase,
        aa: CIRLocalAliasAnalysis?
    ): Boolean {
        if (factBase is AccessPathBase.ClassStatic) return true
        if (aa?.aliasGroupContainsClassStatic(factBase) == true) return true

        // Per-operand relevance: forward direction asks "is the fact derived from a load of arg
        // (or alias / cast / ptr_stride chain)?", reverse direction asks the symmetric question
        // (matters for backward trace resolution — `free(load(slot))` style: the call is relevant
        // to slot-based facts so the precondition isn't collapsed to [CallPrecondition.Unchanged]).
        // [pointerDerivedFromSameLoadedSlotAsAddress] in the new alias graph subsumes both the
        // must-alias and direct-deref cases (its pointedByOf closure includes derivation chains).
        fun operandIsRelevant(opBase: AccessPathBase): Boolean {
            if (opBase == factBase) return true
            if (aa == null) return false
            return aa.pointerDerivedFromSameLoadedSlotAsAddress(factBase, opBase) ||
                aa.pointerDerivedFromSameLoadedSlotAsAddress(opBase, factBase)
        }

        for (arg in callExpr.arg_ops) {
            val argBase = accessPathBaseForCallArg(aa, arg) ?: continue
            if (operandIsRelevant(argBase)) return true
            if (MethodFlowFunctionUtils.zeroStridePtrStrideRhsBase(callExpr.location.method, arg) == factBase) {
                return true
            }
        }

        if (returnValue != null) {
            val retValBase = accessPathBaseForCallArg(aa, returnValue)
            if (retValBase != null && operandIsRelevant(retValBase)) return true
        }

        return false
    }

    private fun CIRLocalAliasAnalysis.aliasGroupContainsClassStatic(base: AccessPathBase): Boolean =
        findAliases(base)?.any { it.base is AccessPathBase.ClassStatic } == true

    /* */

    private fun CIRDirectCall.callArgs(): List<MLIRValue> = arg_ops

    private fun CIRDirectCall.argType(idx: Int): MLIRType? {
        return callArgs().getOrNull(idx)?.let { method.classpath.findTypeOrNull(it.type) }
    }

    private fun CIRDirectCall.resultType(): MLIRType? {
        return result?.let { method.classpath.findTypeOrNull(it) }
    }

    private fun CIRDirectCall.resultValueOrNull(): MLIRValue? {
        val r = result ?: return null
        return MLIROpValue(r, id, 0L)
    }
}
