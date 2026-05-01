package org.seqra.dataflow.cir.ap.ifds

import org.seqra.cir.graph.CApplicationGraph
import org.seqra.ir.api.cir.cfg.CIRInst

class CIRLocalAliasAnalysis (
    private val entryPoint: CIRInst,
    private val graph: CApplicationGraph,
    private val languageManager: CIRLanguageManager
) {
}