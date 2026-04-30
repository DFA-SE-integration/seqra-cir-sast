package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.seqra.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPrecondition
import org.seqra.dataflow.ap.ifds.trace.MethodCallPrecondition.PassRuleConditionFacts
import org.seqra.dataflow.ap.ifds.trace.TaintRulePrecondition.PassRuleCondition

class CIRMethodCallPrecondition : MethodCallPrecondition {
    override fun factPrecondition(fact: InitialFactAp): CallPrecondition = CallPrecondition.Unchanged

    override fun resolvePassRuleCondition(precondition: PassRuleCondition): List<PassRuleConditionFacts> = emptyList()
}
