package org.seqra.ir.impl.alias

import org.seqra.ir.api.cir.CIRAliasGroup
import org.seqra.ir.api.cir.CIRFunctionAliasData
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.impl.cfg.builder.buildCIRFunctionID
import org.seqra.ir.impl.cfg.builder.buildMLIRValue
import org.seqra.ir.impl.grpc.CIRAliasProtos

/** Parses [CIRAliasProtos.CIRModuleAliasData] blobs emitted by cir-ser-proto (`--emit-alias`). */
object CIRAliasDataCodec {
    fun decodeModule(bytes: ByteArray): Map<CIRFunctionID, CIRFunctionAliasData> {
        val module = CIRAliasProtos.CIRModuleAliasData.parseFrom(bytes)
        return module.functionsList.associate { f ->
            val id = buildCIRFunctionID(f.function)
            val groups =
                f.aliasGroupsList.map { g ->
                    CIRAliasGroup(
                        g.membersList.map { buildMLIRValue(it) }.toSet(),
                    )
                }
            id to CIRFunctionAliasData(groups)
        }
    }
}
