package org.seqra.ir.impl.features

import org.seqra.ir.api.cir.CIRInstExtFeature
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.MLIRBasicBlock
import org.seqra.ir.api.cir.features.CIRCallByPointerOpInst
import org.seqra.ir.impl.cfg.CIRInstListImpl

object CIRFunctionPointerCallTransformer : CIRInstExtFeature {
    private fun transformBlock(block: MLIRBasicBlock): MLIRBasicBlock {
        val instList = mutableListOf<CIRInst>()
        for (inst in block) {
            if (inst is CIRCallOpInst) {
                if (inst.callee == null) {
                    instList.add(CIRCallByPointerOpInst(inst))
                } else {
                    instList.add(inst)
                }
            } else {
                instList.add(inst)
            }
        }
        return block.withInstList(CIRInstListImpl(instList))
    }

    override fun transformBlockList(function: CIRFunction, blockList: CIRBlockList) =
        CIRBlockList(blockList.blocks.map { block ->
            transformBlock(block)
        })
}
