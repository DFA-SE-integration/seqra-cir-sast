package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.cir.ap.ifds.CIRLocalAliasAnalysis
import org.seqra.ir.api.cir.cfg.CIRInst

@Suppress("UNUSED_PARAMETER")
fun CIRLocalAliasAnalysis.forEachAliasAtStatement(statement: CIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) {
    val base = fact.base
    val aliases = findAliases(base) ?: return
    for (a in aliases) {
        if (a == base) continue
        body(fact.rebase(a))
    }
}