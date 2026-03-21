package org.seqra.ir.api.cir.cfg

import org.seqra.ir.api.common.cfg.BytecodeGraph

interface CIRGraph : BytecodeGraph<CIRInst> {
    val function: CIRFunction
    val entry: CIRInst
}