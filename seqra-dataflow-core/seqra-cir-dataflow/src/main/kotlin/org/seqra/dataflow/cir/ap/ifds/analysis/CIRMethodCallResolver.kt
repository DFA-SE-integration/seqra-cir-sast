package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.MethodAnalyzer
import org.seqra.dataflow.ap.ifds.MethodEntryPoint
import org.seqra.dataflow.ap.ifds.MethodWithContext
import org.seqra.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.seqra.dataflow.ap.ifds.analysis.MethodCallResolver
import org.seqra.dataflow.cir.ap.ifds.CIRCallResolver
import org.seqra.dataflow.cir.ap.ifds.cirDowncast
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.common.cfg.CommonCallExpr
import org.seqra.ir.api.common.cfg.CommonInst

class CIRMethodCallResolver(
    private val callResolver: CIRCallResolver,
    private val runner: TaintAnalysisUnitRunner,
) : MethodCallResolver {
    override fun resolveMethodCall(
        callerEntryPoint: MethodEntryPoint,
        callExpr: CommonCallExpr,
        location: CommonInst,
        handler: MethodAnalyzer.MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler
    ) {
        cirDowncast<CIRInst>(location)
        resolveMethodCall(callerEntryPoint, callExpr, location, handler, failureHandler)
    }

    override fun resolvedMethodCalls(
        callerEntryPoint: MethodEntryPoint,
        callExpr: CommonCallExpr,
        location: CommonInst
    ): List<MethodWithContext> {
        cirDowncast<CIRInst>(location)
        return resolvedMethodCalls(callerEntryPoint, callExpr, location)
    }

    private fun resolveMethodCall(
        callerEntryPoint: MethodEntryPoint,
        callExpr: CommonCallExpr,
        location: CIRInst,
        handler: MethodAnalyzer.MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler
    ) {
        val callees = callResolver.resolve(callExpr, location, callerEntryPoint.context)

        val analyzer = runner.getMethodAnalyzer(callerEntryPoint)
        for (resolvedCallee in callees) {
            when (resolvedCallee) {
                CIRCallResolver.MethodResolutionResult.MethodResolutionFailed -> {
                    analyzer.handleMethodCallResolutionFailure(callExpr, failureHandler)
                }

                is CIRCallResolver.MethodResolutionResult.ConcreteMethod -> {
                    analyzer.handleResolvedMethodCall(resolvedCallee.method, handler)
                }
            }
        }
    }

    private fun resolvedMethodCalls(
        callerEntryPoint: MethodEntryPoint,
        callExpr: CommonCallExpr,
        location: CIRInst
    ): List<MethodWithContext> {
        val callees = callResolver.resolve(callExpr, location, callerEntryPoint.context)
        return callees.flatMap { resolvedCallee ->
            when (resolvedCallee) {
                CIRCallResolver.MethodResolutionResult.MethodResolutionFailed -> {
                    emptyList()
                }

                is CIRCallResolver.MethodResolutionResult.ConcreteMethod -> {
                    listOf(resolvedCallee.method)
                }
            }
        }
    }
}
