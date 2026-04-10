package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.MethodContext
import org.seqra.ir.api.cir.cfg.MLIRTypeID

data class CIRInstanceTypeMethodContext(val receiverTypeId: MLIRTypeID) : MethodContext {
    override fun toString(): String = "{this is $receiverTypeId}"
}
