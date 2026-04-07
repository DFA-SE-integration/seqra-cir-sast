package org.seqra.ir.impl.ir

import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionParameter
import org.seqra.ir.api.cir.cfg.MLIRTypeID

class CIRParameterImpl(override val type: MLIRTypeID, override val index: Int, override val method: CIRFunction) :
    CIRFunctionParameter {
    override fun toString(): String {
        return "arg$index: ${type.id}"
    }
}
