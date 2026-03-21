package org.seqra.ir.api.cir.cfg

import org.seqra.ir.api.common.cfg.CommonInstLocation

data class CIRInstLocation(
    override val method: CIRFunction,
    val index: Int,
    val location: MLIRLocation,
) : CommonInstLocation {
    override fun toString(): String {
        return "#${method.name}:$location"
    }
}