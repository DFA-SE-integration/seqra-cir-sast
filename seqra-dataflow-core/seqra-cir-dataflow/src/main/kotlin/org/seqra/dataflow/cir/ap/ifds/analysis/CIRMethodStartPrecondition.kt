package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.seqra.dataflow.ap.ifds.trace.TaintRulePrecondition

class CIRMethodStartPrecondition : MethodStartPrecondition {
    override fun factPrecondition(fact: InitialFactAp): List<TaintRulePrecondition.Source> = emptyList()
}
