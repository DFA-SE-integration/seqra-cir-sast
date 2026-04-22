package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.EmptyMethodContext
import org.seqra.dataflow.ap.ifds.ExclusionSet
import org.seqra.dataflow.ap.ifds.MethodEntryPoint
import org.seqra.dataflow.ap.ifds.access.ApManager
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.seqra.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact
import org.seqra.dataflow.cir.ap.ifds.CIRInstanceTypeMethodContext
import org.seqra.dataflow.cir.ap.ifds.CIRMethodPositionBaseTypeResolver
import org.seqra.dataflow.cir.ap.ifds.CIRMarkAwareConditionRewriter
import org.seqra.dataflow.cir.ap.ifds.CIRSimpleFactAwareConditionEvaluator
import org.seqra.dataflow.cir.ap.ifds.CalleePositionToCIRValueResolver
import org.seqra.dataflow.cir.ap.ifds.TaintConfigUtils.applyEntryPointConfig
import org.seqra.dataflow.cir.ap.ifds.cirDowncast
import org.seqra.dataflow.cir.ap.ifds.taint.CIRTaintRulesProvider
import org.seqra.dataflow.cir.ap.ifds.taint.TaintSourceActionEvaluator
import org.seqra.dataflow.configuration.core.This
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.util.onSome

class CIRMethodStartFlowFunction (
    private val apManager: ApManager,
    private val context: CIRMethodAnalysisContext,
) : MethodStartFlowFunction {
    override fun propagateZero(): List<StartFact> {
        val result = mutableListOf<StartFact>()
        result.add(StartFact.Zero)

        applySinkRules()

        val method = context.methodEntryPoint.method as CIRFunction
        val valueResolver = CalleePositionToCIRValueResolver(method)
        val conditionRewriter = CIRMarkAwareConditionRewriter(
            valueResolver, method
        )

        val conditionEvaluator = CIRSimpleFactAwareConditionEvaluator(conditionRewriter, evaluator = null)

        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager,
            exclusion = ExclusionSet.Universe,
            context.factTypeChecker,
            returnValueType = null
        )

        applyEntryPointConfig(
            context.taint.taintConfig as CIRTaintRulesProvider,
            method, conditionEvaluator, sourceEvaluator
        ).onSome { facts ->
            facts.mapTo(result) { StartFact.Fact(it) }
        }

        return result
    }

    override fun propagateFact(fact: FinalFactAp): List<StartFact.Fact> {
        val checkedFact = checkInitialFactTypes(context.methodEntryPoint, fact) ?: return emptyList()
        return listOf(StartFact.Fact(checkedFact))
    }

    private fun checkInitialFactTypes(methodEntryPoint: MethodEntryPoint, factAp: FinalFactAp): FinalFactAp? {
        if (factAp.base !is AccessPathBase.This) return factAp

        val thisType = when (val methodContext = methodEntryPoint.context) {
            EmptyMethodContext -> {
                val method = methodEntryPoint.method
                cirDowncast<CIRFunction>(method)
                CIRMethodPositionBaseTypeResolver(method).resolve(This) ?: return null
            }

            is CIRInstanceTypeMethodContext -> {
                val method = methodEntryPoint.method
                cirDowncast<CIRFunction>(method)
                method.classpath.findTypeOrNull(methodContext.receiverTypeId) ?: return null
            }

            else -> error("Unexpected CIR method context for start-flow receiver type check: $methodContext")
        }

        return context.factTypeChecker.filterFactByLocalType(thisType, factAp)
    }

    //  Apply sink rules to next instruction that is MethodStart
    private fun applySinkRules() {
        val config = context.taint.taintConfig as CIRTaintRulesProvider
        val method = context.methodEntryPoint.method
        val statement = context.methodEntryPoint.statement

        val sinkRules = config.sinkRulesForMethodEntry(method).toList()
        if (sinkRules.isEmpty()) return

        val valueResolver = CalleePositionToCIRValueResolver(method as CIRFunction)
        val conditionRewriter = CIRMarkAwareConditionRewriter(
            valueResolver,
            method // Need to determine which CIRExpr relates to MLIRValue taken from valueResolver
        )

        val conditionEvaluator = CIRSimpleFactAwareConditionEvaluator(conditionRewriter, evaluator = null)

        for (rule in sinkRules) {
            val condition = conditionEvaluator.eval(rule.condition)
            if (!condition) {
                continue
            }

            context.taint.taintSinkTracker.addUnconditionalVulnerability(
                context.methodEntryPoint, statement, rule
            )
        }
    }
}
