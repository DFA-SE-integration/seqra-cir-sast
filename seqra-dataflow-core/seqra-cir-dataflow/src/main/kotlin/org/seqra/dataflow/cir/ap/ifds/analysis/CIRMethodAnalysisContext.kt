package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.MethodEntryPoint
import org.seqra.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.seqra.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.seqra.dataflow.cir.ap.ifds.CIRFactTypeChecker
import org.seqra.dataflow.cir.ap.ifds.CIRLocalAliasAnalysis
import org.seqra.dataflow.cir.ap.ifds.CIRMethodCallFactMapper
import org.seqra.dataflow.cir.ap.ifds.CIRLocalVariableReachability

class CIRMethodAnalysisContext(
    override val methodEntryPoint: MethodEntryPoint,
    val factTypeChecker: CIRFactTypeChecker,
    val localVariableReachability: CIRLocalVariableReachability,
    val aliasAnalysis: CIRLocalAliasAnalysis?,
    val taint: TaintAnalysisContext,
) : MethodAnalysisContext {
    override val methodCallFactMapper: MethodCallFactMapper
        get() = CIRMethodCallFactMapper
}
