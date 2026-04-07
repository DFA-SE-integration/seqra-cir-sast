package org.seqra.ir.impl.sources

import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRFunctionSource
import org.seqra.ir.api.cir.cfg.CIRFunctionID

class PersistenceCIRFunctionSource(
    private val cp: CIRClasspath,
    override val functionID: CIRFunctionID,
    override val enclosingModuleId: String,
    private val cachedInfo: ByteArray? = null,
    private val cachedBytecode: ByteArray? = null
) : CIRFunctionSource {
    override val infoNode by lazy {
        cachedInfo ?: cp.db.persistence.findFunctionInfo(cp, functionID)
    }
    override val bytecodeNode: ByteArray? by lazy {
        cachedBytecode ?: cp.db.persistence.findFunctionBytecode(cp, functionID)
    }
}
