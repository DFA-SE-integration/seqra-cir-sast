package org.seqra.ir.api.cir

interface CIRTypeSource {
    val node: IRNode
    val enclosingModuleId: String
}