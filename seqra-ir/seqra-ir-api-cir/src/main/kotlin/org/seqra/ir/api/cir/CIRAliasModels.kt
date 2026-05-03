package org.seqra.ir.api.cir

import org.seqra.ir.api.cir.cfg.MLIRValue

/** One SeaDSA may-alias equivalence class mapped back to CIR [MLIRValue]s. */
data class CIRAliasGroup(val members: Set<MLIRValue>)

/** Alias groups for a single function. */
data class CIRFunctionAliasData(val groups: List<CIRAliasGroup>)
