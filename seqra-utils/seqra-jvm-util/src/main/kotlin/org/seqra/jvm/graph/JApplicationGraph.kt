package org.seqra.jvm.graph

import org.seqra.ir.api.jvm.JIRClasspath
import org.seqra.ir.api.jvm.JIRMethod
import org.seqra.ir.api.jvm.cfg.JIRInst
import org.seqra.util.analysis.ApplicationGraph

interface JApplicationGraph : ApplicationGraph<JIRMethod, JIRInst> {
    val cp: JIRClasspath
}
