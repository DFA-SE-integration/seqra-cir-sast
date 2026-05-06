package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.cir.ap.ifds.CIRLocalAliasAnalysis
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRInst

// TODO Iterate over instructions and filter for existing in there ops aliases
fun CIRLocalAliasAnalysis.forEachAliasAtStatement(statement: CIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) =
    forEachAlias(fact, body)

// TODO Iterate over instructions and filter for existing in there ops aliases
fun CIRLocalAliasAnalysis.forEachAliasAfterStatement(statement: CIRInst, fact: FinalFactAp, body: (FinalFactAp) -> Unit) =
    forEachAlias(fact, body)

fun CIRLocalAliasAnalysis.forEachPossibleAliasAtStatement(
    statement: CIRInst,
    fact: InitialFactAp,
    body: (InitialFactAp) -> Unit
) {
    val localVars = statement.method.allInstructions
        .asSequence()
        .filterIsInstance<CIRAssignInst>()
        .mapNotNull { MethodFlowFunctionUtils.accessPathBase(it.lhv) }
        .filterIsInstance<AccessPathBase.LocalVar>()
        .distinct()
        .toList()

    return forEachAliasAmongBases( fact, localVars, body)
}

fun CIRLocalAliasAnalysis.forEachAliasAmongBases(
    fact: InitialFactAp,
    bases: List<AccessPathBase.LocalVar>,
    body: (InitialFactAp) -> Unit
) {
    bases.forEach { base ->
        val aliases = findAliases(base)?.filter { it.base !is AccessPathBase.Constant } ?: return@forEach
        applyAlias(fact, base, aliases, body)
    }
}

fun CIRLocalAliasAnalysis.forEachAlias(fact: FinalFactAp, body: (FinalFactAp) -> Unit) {
    val base = fact.base as? AccessPathBase.LocalVar ?: return
    val aliases = findAliases(base) ?: return
    for (a in aliases) {
        if (a.base == base) continue
        a.base?.let { body(fact.rebase(it)) }
    }
}

private fun applyAlias(fact: InitialFactAp, newBase: AccessPathBase.LocalVar, aliases: List<MethodFlowFunctionUtils.Access>, body: (InitialFactAp) -> Unit) {
    val result = aliases.filter { it.base != fact.base }.mapNotNull { it.accessor }
        .fold(fact.rebase(newBase)) {f, accessor ->
            f.readAccessor(accessor) ?: return
        }

    body(result)
}
