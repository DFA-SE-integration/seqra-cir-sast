package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
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
        return mapMethodCallToStartFlowFact(callee, callExpr, factAp, checker, onMappedFact)
    }

    private fun mapMethodCallToStartFlowFact(
        callee: CIRFunction,
        callExpr: CIRDirectCall,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit
    ) = mapMethodCallToStartFlowFact(
        callExpr = callExpr,
        factAp = factAp,
        checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
        onMappedFact = onMappedFact
    )

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit
    ) {
        cirDowncast<CIRFunction>(callee)
        cirDowncast<CIRDirectCall>(callExpr)
        return mapMethodCallToStartFlowFact(callee, callExpr, fact, onMappedFact)
    }

    private fun mapMethodCallToStartFlowFact(
        callee: CIRFunction,
        callExpr: CIRDirectCall,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit
    ) = mapMethodCallToStartFlowFact(
        callExpr = callExpr,
        factAp = fact,
        checkFactType = { _, f -> f },
        onMappedFact = onMappedFact
    )

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

                val checkedFact = callStatement.argType(base.idx)?.let { checkFactType(it, factAp) } ?: return null
                rebaseFact(checkedFact, newBase)
            }

            AccessPathBase.Return -> {
                val returnValue = callStatement.resultValueOrNull() ?: return null
                val newBase = accessPathBase(returnValue) ?: return null
                if (newBase is AccessPathBase.Constant) return null

                val checkedFact = callStatement.resultType()?.let { checkFactType(it, factAp) } ?: return null
                rebaseFact(checkedFact, newBase)
            }

            AccessPathBase.This,
            AccessPathBase.Exception,
            is AccessPathBase.LocalVar -> null
        }
    }

    private inline fun <F : FactAp> mapMethodCallToStartFlowFact(
        callExpr: CIRDirectCall,
        factAp: F,
        checkFactType: (MLIRType, F) -> F?,
        onMappedFact: (F, AccessPathBase) -> Unit,
    ) {
        val factBase = factAp.base

        if (factBase is AccessPathBase.ClassStatic) {
            onMappedFact(factAp, factBase)
        }

        for ((i, arg) in callExpr.arg_ops.withIndex()) {
            val argBase = accessPathBase(arg)
            if (argBase == factBase) {
                val checkedFact = callExpr.argType(i)?.let { checkFactType(it, factAp) }
                if (checkedFact != null) {
                    onMappedFact(checkedFact, AccessPathBase.Argument(i))
                }
            }
        }
    }

    private fun factIsRelevantToMethodCall(
        returnValue: MLIRValue?,
        callExpr: CIRDirectCall,
        factBase: AccessPathBase,
        aa: CIRLocalAliasAnalysis?
    ): Boolean {
        if (factBase is AccessPathBase.ClassStatic) {
            return true
        }
        if (aa?.aliasGroupContainsClassStatic(factBase) == true) {
            return true
        }

        for (arg in callExpr.arg_ops) {
            val argBase = accessPathBase(arg)
            if (argBase == factBase) {
                return true
            }

            if (argBase != null && aa?.basesAlias(argBase, factBase) == true) {
                return true
            }
        }

        if (returnValue != null) {
            val retValBase = accessPathBase(returnValue)
            if (retValBase == factBase) {
                return true
            }
            if (retValBase != null && aa?.basesAlias(retValBase, factBase) == true) {
                return true
            }
        }

        return false
    }

    private fun CIRLocalAliasAnalysis.aliasGroupContainsClassStatic(base: AccessPathBase): Boolean =
        findAliases(base)?.any { it.base is AccessPathBase.ClassStatic } == true

    private fun CIRLocalAliasAnalysis.basesAlias(a: AccessPathBase, b: AccessPathBase): Boolean =
        findAliases(a)?.any { it.base == b } == true

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
