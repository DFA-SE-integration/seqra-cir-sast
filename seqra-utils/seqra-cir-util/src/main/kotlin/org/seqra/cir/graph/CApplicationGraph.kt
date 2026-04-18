package org.seqra.cir.graph

import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.util.analysis.ApplicationGraph

interface CApplicationGraph : ApplicationGraph<CIRFunction, CIRInst> {
    val cp: CIRClasspath
}
