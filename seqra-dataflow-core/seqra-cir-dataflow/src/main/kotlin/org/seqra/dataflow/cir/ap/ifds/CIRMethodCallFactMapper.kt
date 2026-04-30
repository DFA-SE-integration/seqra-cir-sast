package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.FactTypeChecker
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.seqra.dataflow.ap.ifds.access.FactAp
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRTryCallOpInst
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef
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
        return mapMethodExitToReturnFlowFact(
            callStatement = callStatement,
            factAp = factAp,
            checkFactType = { _, fact -> checker.filterFactByLocalType(null, fact) },
            rebaseFact = { fact, base -> fact.rebase(base) },
        )
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: InitialFactAp
    ): List<InitialFactAp> {
        return mapMethodExitToReturnFlowFact(
            callStatement = callStatement,
            factAp = factAp,
            checkFactType = { _, fact -> fact },
            rebaseFact = { fact, base -> fact.rebase(base) },
        )
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit,
    ) {
        callee as? CIRFunction ?: return
        val factBase = factAp.base
        if (factBase is AccessPathBase.ClassStatic) {
            onMappedFact(factAp, factBase)
        }
        val args = callArgs(callExpr) ?: return
        for ((i, arg) in args.withIndex()) {
            val argBase = accessPathBase(arg) ?: continue
            if (argBase == factBase) {
                val checked = checker.filterFactByLocalType(null, factAp) ?: continue
                onMappedFact(checked, AccessPathBase.Argument(i))
            }
        }
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit,
    ) {
        callee as? CIRFunction ?: return
        val factBase = fact.base
        if (factBase is AccessPathBase.ClassStatic) {
            onMappedFact(fact, factBase)
        }
        val args = callArgs(callExpr) ?: return
        for ((i, arg) in args.withIndex()) {
            val argBase = accessPathBase(arg) ?: continue
            if (argBase == factBase) {
                onMappedFact(fact, AccessPathBase.Argument(i))
            }
        }
    }

    override fun factIsRelevantToMethodCall(
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp,
    ): Boolean = factIsRelevantToMethodCall(returnValue as? MLIRValue, callExpr, factAp.base)

    override fun isValidMethodExitFact(factAp: FactAp): Boolean =
        factAp.base !is AccessPathBase.LocalVar

    private fun factIsRelevantToMethodCall(
        returnValue: MLIRValue?,
        callExpr: CommonCallExpr,
        factBase: AccessPathBase,
    ): Boolean {
        if (factBase is AccessPathBase.ClassStatic) {
            return true
        }
        val args = callArgs(callExpr) ?: return false
        for (arg in args) {
            if (accessPathBase(arg) == factBase) {
                return true
            }
        }
        if (returnValue != null && accessPathBase(returnValue) == factBase) {
            return true
        }
        return false
    }

    private fun callArgs(callExpr: CommonCallExpr): List<MLIRValue>? = when (callExpr) {
        is CIRCallOpInst -> callExpr.arg_ops
        is CIRTryCallOpInst -> callExpr.arg_ops
        else -> null
    }

    private inline fun <F : FactAp> mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: F,
        checkFactType: (MLIRValue?, F) -> F?,
        rebaseFact: (F, AccessPathBase) -> F,
    ): List<F> = listOfNotNull(
        mapMethodExitToReturnSingleFlowFact(
            callStatement = callStatement,
            factAp = factAp,
            checkFactType = checkFactType,
            rebaseFact = rebaseFact,
        ),
    )

    private inline fun <F : FactAp> mapMethodExitToReturnSingleFlowFact(
        callStatement: CommonInst,
        factAp: F,
        checkFactType: (MLIRValue?, F) -> F?,
        rebaseFact: (F, AccessPathBase) -> F,
    ): F? {
        val callExpr = callStatement as? CommonCallExpr ?: error("Non call statement")
        val base = factAp.base

        return when (base) {
            is AccessPathBase.ClassStatic,
            is AccessPathBase.Constant -> factAp

            is AccessPathBase.Argument -> {
                val argExpr = callArgs(callExpr)?.getOrNull(base.idx) ?: return null
                val newBase = accessPathBase(argExpr) ?: return null
                if (newBase is AccessPathBase.Constant) return null

                val checkedFact = checkFactType(argExpr, factAp) ?: return null
                rebaseFact(checkedFact, newBase)
            }

            AccessPathBase.Return -> {
                val returnValue = callExpr.resultValueOrNull() ?: return null
                val newBase = accessPathBase(returnValue) ?: return null
                if (newBase is AccessPathBase.Constant) return null

                val checkedFact = checkFactType(returnValue, factAp) ?: return null
                rebaseFact(checkedFact, newBase)
            }

            AccessPathBase.This,
            AccessPathBase.Exception,
            is AccessPathBase.LocalVar -> null
        }
    }

    private fun accessPathBase(value: MLIRValue): AccessPathBase? = when (value) {
        is MLIRValueRef -> accessPathBase(value.value)
        is MLIRBlockValue -> AccessPathBase.Argument(value.argIndex.toInt())
        is MLIROpValue -> AccessPathBase.LocalVar(value.opIndex.id.toInt())
        else -> null
    }

    private fun CommonCallExpr.resultValueOrNull(): MLIRValue? = when (this) {
        is CIRCallOpInst -> {
            val r = result ?: return null
            MLIROpValue(r, id, 0L)
        }

        is CIRTryCallOpInst -> {
            val r = result ?: return null
            MLIROpValue(r, id, 0L)
        }

        else -> null
    }
}
