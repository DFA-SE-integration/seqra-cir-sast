package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.cir.ap.ifds.taint.ConditionEvaluator
import org.seqra.dataflow.configuration.core.Condition

class CIRSimpleFactAwareConditionEvaluator(
    private val conditionRewriter: CIRMarkAwareConditionRewriter,
    private val evaluator: CIRFactAwareConditionEvaluator?,
) : ConditionEvaluator<Boolean> {
    override fun eval(condition: Condition): Boolean {
        val simplifiedCondition = conditionRewriter.rewrite(condition)
        val conditionExpr = when {
            simplifiedCondition.isFalse -> return false
            simplifiedCondition.isTrue -> return true
            else -> simplifiedCondition.expr
        }

        if (evaluator == null) {
            return false
        }

        return evaluator.evalWithAssumptionsCheck(conditionExpr)
    }
}