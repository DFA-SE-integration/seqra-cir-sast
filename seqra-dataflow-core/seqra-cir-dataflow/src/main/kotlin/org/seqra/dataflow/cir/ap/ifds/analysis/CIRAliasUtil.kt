package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.cir.ap.ifds.CIRLocalAliasAnalysis
import org.seqra.ir.api.cir.cfg.CIRInst

fun CIRLocalAliasAnalysis.forEachAliasAtStatement(statement: CIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) {
//    val base = fact.base as? AccessPathBase.LocalVar ?: return
//    val alias = findAlias(base, statement) ?: return
//    if (alias.base is AccessPathBase.Constant) return
//
//    applyAlias(fact, alias, body)
}