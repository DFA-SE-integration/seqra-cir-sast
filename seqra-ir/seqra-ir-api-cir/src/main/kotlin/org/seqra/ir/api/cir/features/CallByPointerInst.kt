package org.seqra.ir.api.cir.features

import org.seqra.ir.api.cir.cfg.CIRCallingConv
import org.seqra.ir.api.cir.cfg.CIRDirectCall
import org.seqra.ir.api.cir.cfg.CIRExtraFuncAttributesAttr
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRInstLocation
import org.seqra.ir.api.cir.cfg.MLIRFlatSymbolRefAttr
import org.seqra.ir.api.cir.cfg.MLIROpID
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRUnitAttr
import org.seqra.ir.api.cir.cfg.MLIRValue


data class CIRCallByPointerOpInst(val actualCall: CIRDirectCall) : CIRInst {
    override val id: MLIROpID = actualCall.id

    override val location: CIRInstLocation = actualCall.location

    val functionType = actualCall.arg_ops.first().type

    val arg_ops: List<MLIRValue> = actualCall.arg_ops.slice(1..<actualCall.arg_ops.size)

    val exception: MLIRUnitAttr? = actualCall.exception
    val callee: MLIRFlatSymbolRefAttr? = actualCall.callee

    val callingConv: CIRCallingConv = actualCall.callingConv
    val extraAttrs: CIRExtraFuncAttributesAttr = actualCall.extraAttrs

    val result: MLIRTypeID? = actualCall.result
}