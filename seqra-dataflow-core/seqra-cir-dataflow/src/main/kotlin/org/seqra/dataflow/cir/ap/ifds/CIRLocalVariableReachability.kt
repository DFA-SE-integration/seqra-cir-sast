package org.seqra.dataflow.cir.ap.ifds

import org.seqra.cir.graph.CApplicationGraph
import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.common.cfg.CommonInst

class CIRLocalVariableReachability(
    private val method: CIRFunction,
    private val graph: CApplicationGraph,
    private val languageManager: CIRLanguageManager,
) {
    fun isReachable(base: AccessPathBase, statement: CommonInst): Boolean = true
}
