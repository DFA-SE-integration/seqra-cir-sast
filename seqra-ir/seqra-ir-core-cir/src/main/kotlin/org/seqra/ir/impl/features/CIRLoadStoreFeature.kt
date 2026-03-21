package org.seqra.ir.impl.features

import org.seqra.ir.api.cir.CIRInstExtFeature
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.MLIRBasicBlock
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRInstList
import org.seqra.ir.api.cir.cfg.CIRLoadOpInst
import org.seqra.ir.api.cir.cfg.CIRStoreOpInst
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef
import org.seqra.ir.impl.cfg.CIRInstListImpl

object CIRLoadStoreFeature : CIRInstExtFeature {
    private fun transformLoadInst(loadOp: CIRLoadOpInst): CIRInst {
        return CIRAssignInst(
            loadOp.location, loadOp.id, MLIROpValue(loadOp.result, loadOp.id, 0), MLIRValueRef(loadOp.addr)
        )
    }

    private fun transformStoreInst(storeOp: CIRStoreOpInst): CIRInst {
        return CIRAssignInst(storeOp.location, storeOp.id, MLIRValueRef(storeOp.addr), storeOp.value)
    }

    private fun MLIRBasicBlock.withInstList(instList: CIRInstList<CIRInst>): MLIRBasicBlock = MLIRBasicBlock(
        id = id, instructions = instList, arguments = arguments
    )

    private fun transformBlock(block: MLIRBasicBlock): MLIRBasicBlock {
        val blockInstList = mutableListOf<CIRInst>()

        var instIdx = 0
        while (instIdx < block.instructions.size) {
            val inst = block.instructions[instIdx]
            blockInstList.add(
                when (inst) {
                    is CIRLoadOpInst -> transformLoadInst(inst)
                    is CIRStoreOpInst -> transformStoreInst(inst)
                    else -> inst
                }
            )
            ++instIdx
        }
        return block.withInstList(CIRInstListImpl(blockInstList))
    }

    override fun transformBlockList(function: CIRFunction, blockList: CIRBlockList): CIRBlockList {
        val blocks = mutableListOf<MLIRBasicBlock>()
        for (block in blockList.blocks) {
            blocks.add(transformBlock(block))
        }
        return CIRBlockList(blocks)
    }
}
