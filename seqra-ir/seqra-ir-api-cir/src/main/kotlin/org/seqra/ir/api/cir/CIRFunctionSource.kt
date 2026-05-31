package org.seqra.ir.api.cir

import org.seqra.ir.api.cir.cfg.CIRFunctionID

interface CIRFunctionSource {
    val functionID: CIRFunctionID

    val infoNode: ByteArray
    val bytecodeNode: ByteArray?

    val enclosingModuleId: String
    val locationId: Long?
        get() = null
}
