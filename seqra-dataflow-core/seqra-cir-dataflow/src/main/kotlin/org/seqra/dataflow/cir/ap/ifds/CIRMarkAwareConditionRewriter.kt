package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.cir.ap.ifds.CIRMarkAwareConditionExpr.Literal
import org.seqra.dataflow.cir.ap.ifds.taint.CIRBasicAtomEvaluator
import org.seqra.dataflow.configuration.core.And
import org.seqra.dataflow.configuration.core.Condition
import org.seqra.dataflow.configuration.core.ContainsMark
import org.seqra.dataflow.configuration.core.Not
import org.seqra.dataflow.configuration.core.Or
import org.seqra.dataflow.configuration.core.PositionResolver
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.util.Maybe

class CIRMarkAwareConditionRewriter (
    positionResolver: PositionResolver<Maybe<MLIRValue>>,
    method: CIRFunction // Pass through this from FlowFunc -> CIRBasicAtomEvaluator
) {
//    TODO For now negated not affect eval result for CIR
    private val positiveAtomEvaluator = CIRBasicAtomEvaluator(positionResolver, method)
    private val negativeAtomEvaluator = CIRBasicAtomEvaluator(positionResolver, method)

    fun rewrite(condition: Condition): ExprOrConstant =
        rewriteCondition(condition)

    private fun rewriteCondition(condition: Condition): ExprOrConstant = when (condition) {
        is And -> rewriteAndCondition(condition)
        is Or -> rewriteOrCondition(condition)
        is Not -> rewriteNotCondition(condition)
        else -> rewriteAtom(condition, positiveAtomEvaluator)
    }

    private fun rewriteAndCondition(condition: And): ExprOrConstant {
        return rewriteList(condition.args, trueExpr, CIRMarkAwareConditionExpr::And) {
            when {
                it.isTrue -> null
                it.isFalse -> return falseExpr
                else -> it.expr
            }
        }
    }

    private fun rewriteOrCondition(condition: Or): ExprOrConstant {
        return rewriteList(condition.args, falseExpr, CIRMarkAwareConditionExpr::Or) {
            when {
                it.isTrue -> return trueExpr
                it.isFalse -> null
                else -> it.expr
            }
        }
    }

    private fun rewriteNotCondition(condition: Not): ExprOrConstant =
        rewriteAtom(condition.arg, negativeAtomEvaluator).negate()

    private fun rewriteAtom(atom: Condition, evaluator: CIRBasicAtomEvaluator): ExprOrConstant {
        if (atom is ContainsMark) {
            return ExprOrConstant(Literal(atom, negated = false))
        }

        val result = atom.accept(evaluator)
        return if (result) trueExpr else falseExpr
    }

    private fun CIRMarkAwareConditionExpr.negate(): CIRMarkAwareConditionExpr = when (this) {
        is Literal -> Literal(condition, negated = !negated)
        is CIRMarkAwareConditionExpr.And,
        is CIRMarkAwareConditionExpr.Or -> error("Unexpected formula structure")
    }

    private inline fun rewriteList(
        elements: List<Condition>,
        default: ExprOrConstant,
        create: (Array<CIRMarkAwareConditionExpr>) -> CIRMarkAwareConditionExpr,
        processElement: (ExprOrConstant) -> CIRMarkAwareConditionExpr?,
    ): ExprOrConstant {
        val result = arrayOfNulls<CIRMarkAwareConditionExpr>(elements.size)
        var size = 0
        for (i in elements.indices) {
            val elementResult = rewriteCondition(elements[i])
            val elementExpr = processElement(elementResult) ?: continue
            result[size++] = elementExpr
        }

        if (size == 0) {
            return default
        }

        if (size == 1) {
            return ExprOrConstant(result[0]!!)
        }

        val resultExprs = result.copyOf(size)

        @Suppress("UNCHECKED_CAST")
        resultExprs as Array<CIRMarkAwareConditionExpr>

        return ExprOrConstant(create(resultExprs))
    }

    @JvmInline
    value class ExprOrConstant(private val rawValue: Any?) {
        val isTrue: Boolean get() = rawValue === trueMarker
        val isFalse: Boolean get() = rawValue === falseMarker

        val expr: CIRMarkAwareConditionExpr get() = rawValue as CIRMarkAwareConditionExpr
    }

    private fun ExprOrConstant.negate(): ExprOrConstant = when {
        this.isFalse -> trueExpr
        this.isTrue -> falseExpr
        else -> ExprOrConstant(expr.negate())
    }

    companion object {
        private val trueMarker = Any()
        private val falseMarker = Any()

        private val trueExpr = ExprOrConstant(trueMarker)
        private val falseExpr = ExprOrConstant(falseMarker)
    }
}
