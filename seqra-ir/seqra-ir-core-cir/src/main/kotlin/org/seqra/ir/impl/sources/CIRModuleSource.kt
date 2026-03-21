package org.seqra.ir.impl.sources

import org.seqra.ir.api.cir.IRNode
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.CIRModuleSource
import org.seqra.ir.api.cir.RegisteredLocation

class CIRModuleSourceImpl(
    override val node: IRNode, override val location: RegisteredLocation
) : CIRModuleSource
