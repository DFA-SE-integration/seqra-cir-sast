package org.seqra.ir.impl.alias

import org.seqra.ir.api.cir.CIRAliasGroup
import org.seqra.ir.api.cir.CIRFunctionAliasData
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.impl.cfg.builder.buildCIRFunctionID
import org.seqra.ir.impl.cfg.builder.buildMLIRModuleID
import org.seqra.ir.impl.cfg.builder.buildMLIRValue
import org.seqra.ir.impl.grpc.CIRAliasProtos

/**
 * Decode-only counterpart for the alias side-channel. Encoding lives on
 * the C++ side in `cir-tac/src/AliasSerializer.cpp`; here we just parse
 * what `cir-ser-proto` embeds into `MLIRModule.alias_data`.
 */
object CIRAliasDataCodec {
    /**
     * Parse a `CIRModuleAliasData` blob.
     *
     * The blob is verified to belong to [expectedModuleId] - otherwise the
     * whole anti-aliasing channel is poisoned and we want to know loudly,
     * not silently substitute wrong groups. Singleton/empty groups are
     * dropped (sea-dsa occasionally emits cells with a single CIR-side
     * member - they carry no aliasing information). Duplicate function
     * entries within one blob are an error.
     */
    fun decodeModule(
        bytes: ByteArray,
        expectedModuleId: MLIRModuleID,
    ): Map<CIRFunctionID, CIRFunctionAliasData> {
        val module = CIRAliasProtos.CIRModuleAliasData.parseFrom(bytes)
        val actualModuleId = buildMLIRModuleID(module.moduleId)
        check(actualModuleId == expectedModuleId) {
            "Alias blob is for module $actualModuleId but $expectedModuleId was expected"
        }

        val out = HashMap<CIRFunctionID, CIRFunctionAliasData>()
        for (f in module.functionsList) {
            val id = buildCIRFunctionID(f.function)
            val groups = f.aliasGroupsList.mapNotNull { g ->
                val members = g.membersList.map { buildMLIRValue(it) }.toSet()
                if (members.size < 2) null else CIRAliasGroup(members)
            }
            val previous = out.put(id, CIRFunctionAliasData(groups))
            check(previous == null) {
                "Duplicate alias data for function $id in module $expectedModuleId"
            }
        }
        return out
    }
}
