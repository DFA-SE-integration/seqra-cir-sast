package org.seqra.ir.impl.sources

import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.CIRTypeSource
import org.seqra.ir.api.cir.IRNode

class PersistenceCIRTypeSource(
    private val db: CIRDatabase,
    private val typeInstanceId: Long,
    override val enclosingModuleId: String,
    private val cachedByteCode: IRNode? = null
) : CIRTypeSource {
    override val node: IRNode
        get() = cachedByteCode!!
}