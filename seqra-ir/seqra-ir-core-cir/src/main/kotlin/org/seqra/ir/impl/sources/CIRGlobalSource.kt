package org.seqra.ir.impl.sources

import org.seqra.ir.api.cir.CIRGlobalSource
import org.seqra.ir.api.cir.IRNode

class PersistenceCIRGlobalSource(override val node: GlobalIRNode) : CIRGlobalSource {
    override val enclosingModuleId: String
        get() = TODO("Not yet implemented")
}