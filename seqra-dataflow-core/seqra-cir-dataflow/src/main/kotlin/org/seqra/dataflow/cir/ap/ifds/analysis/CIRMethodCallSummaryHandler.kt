package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.ExclusionSet
import org.seqra.dataflow.ap.ifds.FactTypeChecker
import org.seqra.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.seqra.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.seqra.dataflow.cir.ap.ifds.CIRMethodCallFactMapper
import org.seqra.ir.api.cir.cfg.CIRInst
import kotlin.collections.plusAssign

class CIRMethodCallSummaryHandler(
    private val statement: CIRInst,
    private val analysisContext: CIRMethodAnalysisContext,
) : MethodCallSummaryHandler {
    override val factTypeChecker: FactTypeChecker get() = analysisContext.factTypeChecker

    override fun mapMethodExitToReturnFlowFact(fact: FinalFactAp): List<FinalFactAp> =
        CIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, fact, factTypeChecker)

    override fun handleSummary(
        currentFactAp: FinalFactAp,
        summaryEffect: MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication,
        summaryFact: FinalFactAp,
        handleSummaryEdge: (initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp) -> Sequent
    ): Set<Sequent> {
        val result = hashSetOf<Sequent>()

        result += super.handleSummary(
            currentFactAp,
            summaryEffect,
            summaryFact
        ) { initialFactRefinement: ExclusionSet?, summaryFactAp: FinalFactAp ->
//            TODO
//            analysisContext.aliasAnalysis?.forEachAliasAfterStatement(statement, summaryFactAp) { aliased ->
//                handleSummaryEdge(initialFactRefinement, aliased)
//            }

            handleSummaryEdge(initialFactRefinement, summaryFactAp)
        }

        return result
    }
}
