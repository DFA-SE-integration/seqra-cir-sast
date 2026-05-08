package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.cir.ap.ifds.CIRLocalAliasAnalysis
import org.seqra.ir.api.cir.cfg.CIRInst

// TODO Iterate over instructions and filter for existing in there ops aliases
fun CIRLocalAliasAnalysis.forEachAliasAtStatement(statement: CIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) =
    forEachAlias(fact, body)

// TODO Iterate over instructions and filter for existing in there ops aliases
fun CIRLocalAliasAnalysis.forEachAliasAfterStatement(statement: CIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) =
    forEachAlias(fact, body)

fun CIRLocalAliasAnalysis.forEachAlias(fact: FinalFactAp, body: (FinalFactAp) -> Unit) {
    val base = fact.base
    findAliases(base)?.forEach { a ->
        if (a.base == base) return@forEach
        val newBase = a.base ?: return@forEach
        val rebased =
            if (a.accessor != null) fact.prependAccessor(a.accessor).rebase(newBase)
            else fact.rebase(newBase)
        body(rebased)
    }
    derefAliasesOf(base).forEach { a ->
        if (a.base == base) return@forEach
        val newBase = a.base ?: return@forEach
        val rebased =
            if (a.accessor != null) fact.prependAccessor(a.accessor).rebase(newBase)
            else fact.rebase(newBase)
        body(rebased)
    }
}

fun CIRLocalAliasAnalysis.forEachAlias(fact: InitialFactAp, body: (InitialFactAp) -> Unit) {
    val base = fact.base
    findAliases(base)?.forEach { a ->
        if (a.base == base) return@forEach
        val newBase = a.base ?: return@forEach
        val rebased =
            if (a.accessor != null) fact.prependAccessor(a.accessor).rebase(newBase)
            else fact.rebase(newBase)
        body(rebased)
    }
    derefAliasesOf(base).forEach { a ->
        if (a.base == base) return@forEach
        val newBase = a.base ?: return@forEach
        val rebased =
            if (a.accessor != null) fact.prependAccessor(a.accessor).rebase(newBase)
            else fact.rebase(newBase)
        body(rebased)
    }
}
