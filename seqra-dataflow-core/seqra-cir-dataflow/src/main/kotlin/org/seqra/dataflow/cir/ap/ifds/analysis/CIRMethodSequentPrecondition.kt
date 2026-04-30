package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.trace.MethodSequentPrecondition

class CIRMethodSequentPrecondition : MethodSequentPrecondition {
    override fun factPrecondition(fact: InitialFactAp): MethodSequentPrecondition.SequentPrecondition =
        MethodSequentPrecondition.SequentPrecondition.Unchanged
}
