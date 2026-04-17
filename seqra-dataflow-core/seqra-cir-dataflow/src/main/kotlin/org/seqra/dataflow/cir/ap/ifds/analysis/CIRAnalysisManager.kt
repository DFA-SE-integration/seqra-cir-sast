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
import org.seqra.dataflow.cir.ap.ifds.CIRFactTypeChecker
import org.seqra.dataflow.cir.ap.ifds.CIRLanguageManager
import org.seqra.dataflow.cir.ap.ifds.CIRMethodCallFactMapper
import org.seqra.dataflow.cir.ap.ifds.cirDowncast
import org.seqra.dataflow.ifds.UnitResolver
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.cfg.CommonCallExpr
import org.seqra.ir.api.common.cfg.CommonInst
import org.seqra.ir.api.common.cfg.CommonValue
import org.seqra.cir.graph.CApplicationGraph
import org.seqra.dataflow.cir.ap.ifds.CIRLocalAliasAnalysis
import org.seqra.dataflow.cir.ap.ifds.CIRLocalVariableReachability
import org.seqra.dataflow.cir.ap.ifds.CIRMethodContextSerializer
import org.seqra.ir.api.cir.cfg.CIRDirectCall
import org.seqra.util.analysis.ApplicationGraph

class CIRAnalysisManager(
    cp: CIRClasspath,
    private val applyAliasInfo: Boolean = true,
) : CIRLanguageManager(cp), TaintAnalysisManager {
    private val factTypeChecker = CIRFactTypeChecker(cp)

    override fun getMethodCallResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver: UnitResolver<CommonMethod>,
        runner: TaintAnalysisUnitRunner
    ): MethodCallResolver {
        val cIRCallResolver = CIRCallResolver(cp)

        return CIRMethodCallResolver(cIRCallResolver, runner)
    }

    override fun getMethodAnalysisContext(
        methodEntryPoint: MethodEntryPoint,
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        taintAnalysisContext: TaintAnalysisContext
    ): MethodAnalysisContext {
        val entryPointStatement = methodEntryPoint.statement
        cirDowncast<CIRInst>(entryPointStatement)
        cirDowncast<CApplicationGraph>(graph)

        val aliasAnalysis = if (applyAliasInfo) {
            CIRLocalAliasAnalysis(entryPointStatement, this)
        } else {
            null
        }

        val method = entryPointStatement.method
        val localVariableReachability = CIRLocalVariableReachability(method, graph, this)
        return CIRMethodAnalysisContext(
            methodEntryPoint,
            factTypeChecker,
            localVariableReachability,
            aliasAnalysis,
            taintAnalysisContext)
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

    override fun getMethodSequentFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst
    ): MethodSequentFlowFunction {
        cirDowncast<CIRInst>(currentInst)
        cirDowncast<CIRMethodAnalysisContext>(analysisContext)

        return CIRMethodSequentFlowFunction(apManager, analysisContext, currentInst)
    }

    override fun getMethodSequentPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst
    ): MethodSequentPrecondition {
        throw RuntimeException("Not implemented")
    }

    override fun getMethodCallFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst
    ): MethodCallFlowFunction {
        cirDowncast<MLIRValue?>(returnValue)
        cirDowncast<CIRDirectCall>(callExpr)
        cirDowncast<CIRInst>(statement)
        cirDowncast<CIRMethodAnalysisContext>(analysisContext)

        return CIRMethodCallFlowFunction(
            apManager,
            analysisContext,
            returnValue,
            callExpr,
            statement,
        )
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

    override fun getMethodCallSummaryHandler(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst
    ): MethodCallSummaryHandler {
        cirDowncast<CIRInst>(statement)
        cirDowncast<CIRMethodAnalysisContext>(analysisContext)

        return CIRMethodCallSummaryHandler(statement, analysisContext)
    }

    override fun isReachable(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        base: AccessPathBase,
        statement: CommonInst
    ): Boolean {
        cirDowncast<CIRMethodAnalysisContext>(analysisContext)
        return analysisContext.localVariableReachability.isReachable(base, statement)
    }

    override fun isValidMethodExitFact(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        fact: FinalFactAp
    ): Boolean {
        return CIRMethodCallFactMapper.isValidMethodExitFact(fact)
    }

    override val methodContextSerializer = CIRMethodContextSerializer(cp)

    override fun onInstructionReached(inst: CommonInst) {
        // Nothing to do
    }

    override fun reportLanguageSpecificRunnerProgress(logger: KLogger) {
        logger.debug {
            val localTotal = factTypeChecker.localFactsTotal.sum()
            val localRejected = factTypeChecker.localFactsRejected.sum()
            val accessTotal = factTypeChecker.accessTotal.sum()
            val accessRejected = factTypeChecker.accessRejected.sum()
            buildString {
                append("Fact types: ")
                append("local $localRejected/$localTotal (${percentToString(localRejected, localTotal)})")
                append(" | ")
                append("access $accessRejected/$accessTotal (${percentToString(accessRejected, accessTotal)})")
            }
        }
    }

    private fun percentToString(current: Long, total: Long): String {
        val percentValue = current.toDouble() / total
        return String.format("%.2f", percentValue * 100) + "%"
    }
}
