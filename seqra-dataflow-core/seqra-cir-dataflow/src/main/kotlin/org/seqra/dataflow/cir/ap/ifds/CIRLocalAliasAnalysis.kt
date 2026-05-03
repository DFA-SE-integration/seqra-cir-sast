package org.seqra.dataflow.cir.ap.ifds

import org.seqra.cir.graph.CApplicationGraph
import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst

class CIRLocalAliasAnalysis(
    private val entryPoint: CIRInst,
    private val graph: CApplicationGraph,
    @Suppress("unused") private val languageManager: CIRLanguageManager,
) {
    private val function: CIRFunction = entryPoint.location.method

    /** Flow-insensitive alias groups derived from SeaDSA (serialized in `.alias.pb`). */
    private val aliasGroupByBase: Map<AccessPathBase, Set<AccessPathBase>> = buildMap {
        val data = graph.cp.findFunctionAliasData(function.id) ?: return@buildMap
        for (group in data.groups) {
            val bases =
                group.members
                    .mapNotNull { v -> MethodFlowFunctionUtils.accessPathBaseOrNull(v) }
                    .toHashSet()
            if (bases.size < 2) continue
            for (b in bases) put(b, bases)
        }
    }

    fun findAliases(base: AccessPathBase): Set<AccessPathBase>? =
        aliasGroupByBase[base]?.takeIf { it.size > 1 }
}
