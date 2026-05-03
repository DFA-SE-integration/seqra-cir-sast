package org.seqra.ir.api.cir

interface CIRModuleSource {
    val node: IRNode
    val location: RegisteredLocation

    /** Serialized [org.seqra.ir.impl.grpc.Alias.CIRModuleAliasData] for this module, if present. */
    val aliasData: ByteArray?
        get() = null
}