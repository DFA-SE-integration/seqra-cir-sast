package org.seqra.dataflow.cir.ap.ifds.analysis

import mu.KLogger
import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.MethodEntryPoint
import org.seqra.dataflow.ap.ifds.TaintAnalysisManager
import org.seqra.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.seqra.dataflow.ap.ifds.access.ApManager
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.seqra.dataflow.ap.ifds.analysis.MethodCallResolver
import org.seqra.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.seqra.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.seqra.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.seqra.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.seqra.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.seqra.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.seqra.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.seqra.dataflow.cir.ap.ifds.CIRCallResolver
import org.seqra.dataflow.cir.ap.ifds.CIRLanguageManager
import org.seqra.dataflow.cir.ap.ifds.cirDowncast
import org.seqra.dataflow.ifds.UnitResolver
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.cfg.CommonCallExpr
import org.seqra.ir.api.common.cfg.CommonInst
import org.seqra.ir.api.common.cfg.CommonValue
import org.seqra.util.analysis.ApplicationGraph

class CIRAnalysisManager(
    cp: CIRClasspath,
) : CIRLanguageManager(cp), TaintAnalysisManager {
    override fun getMethodCallResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver: UnitResolver<CommonMethod>,
        runner: TaintAnalysisUnitRunner
    ): MethodCallResolver {
        val cIRCallResolver = CIRCallResolver(cp)
    }

    override fun getMethodAnalysisContext(
        methodEntryPoint: MethodEntryPoint,
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        taintAnalysisContext: TaintAnalysisContext
    ): MethodAnalysisContext {
        throw RuntimeException("Not implemented")
    }

    override fun getMethodStartFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext
    ): MethodStartFlowFunction {
        cirDowncast<CIRMethodAnalysisContext>(analysisContext)
        return CIRMethodStartFlowFunction(apManager, analysisContext)
    }

    override fun getMethodStartPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext
    ): MethodStartPrecondition {
        throw RuntimeException("Not implemented")
    }

    override fun getMethodSequentPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst
    ): MethodSequentPrecondition {
        throw RuntimeException("Not implemented")
    }

    override fun getMethodSequentFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst
    ): MethodSequentFlowFunction {
        throw RuntimeException("Not implemented")
    }

    override fun getMethodCallFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst
    ): MethodCallFlowFunction {
        throw RuntimeException("Not implemented")
    }

    override fun getMethodCallSummaryHandler(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst
    ): MethodCallSummaryHandler {
        throw RuntimeException("Not implemented")
    }

    override fun getMethodCallPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst
    ): MethodCallPrecondition {
        throw RuntimeException("Not implemented")
    }

    override fun isReachable(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        base: AccessPathBase,
        statement: CommonInst
    ): Boolean {
        throw RuntimeException("Not implemented")
    }

    override fun isValidMethodExitFact(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        fact: FinalFactAp
    ): Boolean {
        throw RuntimeException("Not implemented")
    }

    override fun onInstructionReached(inst: CommonInst) {
        throw RuntimeException("Not implemented")
    }

    override fun reportLanguageSpecificRunnerProgress(logger: KLogger) {
        throw RuntimeException("Not implemented")
    }
}
