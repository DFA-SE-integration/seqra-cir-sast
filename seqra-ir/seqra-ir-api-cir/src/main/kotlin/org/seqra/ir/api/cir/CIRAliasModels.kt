package org.seqra.ir.api.cir

import org.seqra.ir.api.cir.cfg.MLIRValue

/**
 * @see seqra-ir/seqra-ir-core-cir/src/main/proto/alias.proto
 */

/** may-alias equivalence class */
data class CIRAliasGroup(val members: Set<MLIRValue>)

/** Alias groups for a single function */
data class CIRFunctionAliasData(val aliasGroups: List<CIRAliasGroup>)
