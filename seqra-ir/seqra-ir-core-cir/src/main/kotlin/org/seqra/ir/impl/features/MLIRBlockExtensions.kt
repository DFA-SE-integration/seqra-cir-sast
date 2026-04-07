package org.seqra.ir.impl.features

import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRInstList
import org.seqra.ir.api.cir.cfg.MLIRBasicBlock

operator fun MLIRBasicBlock.iterator() = instructions.iterator()

fun MLIRBasicBlock.withInstList(instList: CIRInstList<CIRInst>): MLIRBasicBlock = MLIRBasicBlock(
    id = id, instructions = instList, arguments = arguments
)