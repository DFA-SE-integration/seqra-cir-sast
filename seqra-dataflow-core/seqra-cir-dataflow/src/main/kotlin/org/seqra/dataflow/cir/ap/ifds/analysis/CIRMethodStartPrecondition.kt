package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.access.ApManager
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.seqra.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.seqra.dataflow.cir.ap.ifds.CIRMarkAwareConditionRewriter
import org.seqra.dataflow.cir.ap.ifds.CIRSimpleFactAwareConditionEvaluator
import org.seqra.dataflow.cir.ap.ifds.CalleePositionToCIRValueResolver
import org.seqra.dataflow.cir.ap.ifds.TaintConfigUtils
import org.seqra.dataflow.cir.ap.ifds.taint.CIRTaintRulesProvider
import org.seqra.dataflow.cir.ap.ifds.taint.InitialFactReader
import org.seqra.dataflow.cir.ap.ifds.taint.TaintSourceActionPreconditionEvaluator
import org.seqra.dataflow.configuration.CommonTaintConfigurationSource
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.util.onSome

class CIRMethodStartPrecondition(
    private val apManager: ApManager,
    private val context: CIRMethodAnalysisContext,
) : MethodStartPrecondition {
    override fun factPrecondition(fact: InitialFactAp): List<TaintRulePrecondition.Source> {
        val method = context.methodEntryPoint.method as CIRFunction

        val valueResolver = CalleePositionToCIRValueResolver(method)
        val conditionRewriter = CIRMarkAwareConditionRewriter(
            valueResolver,
            method
        )
        val conditionEvaluator = CIRSimpleFactAwareConditionEvaluator(conditionRewriter, evaluator = null)

        val entryFactReader = InitialFactReader(fact, apManager)
        val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(
            entryFactReader, context.factTypeChecker, returnValueType = null
        )

        val result = TaintConfigUtils.applyEntryPointConfig(
            context.taint.taintConfig as CIRTaintRulesProvider,
            method, conditionEvaluator, sourcePreconditionEvaluator
        )

        result.onSome { sourceActions ->
            return sourceActions.map {
                TaintRulePrecondition.Source(
                    it.first as CommonTaintConfigurationSource,
                    setOf(it.second)
                )
            }
        }

        return emptyList()
    }
}
