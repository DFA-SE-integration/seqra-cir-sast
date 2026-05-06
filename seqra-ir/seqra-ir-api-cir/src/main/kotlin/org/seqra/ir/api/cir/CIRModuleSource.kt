package org.seqra.ir.api.cir

interface CIRModuleSource {
    val node: IRNode
    val location: RegisteredLocation

    val aliasData: ByteArray?
        get() = null
}