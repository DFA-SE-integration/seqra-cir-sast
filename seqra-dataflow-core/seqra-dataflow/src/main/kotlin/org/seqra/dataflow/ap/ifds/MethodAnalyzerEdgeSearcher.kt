package org.seqra.dataflow.ap.ifds

import org.seqra.dataflow.ap.ifds.access.ApManager
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.analysis.AnalysisManager
import org.seqra.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.seqra.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPrecondition
import org.seqra.dataflow.ap.ifds.trace.MethodSequentPrecondition.SequentPrecondition
import org.seqra.dataflow.graph.MethodInstGraph
import org.seqra.ir.api.common.cfg.CommonAssignInst
import org.seqra.ir.api.common.cfg.CommonInst
import org.seqra.ir.api.common.cfg.CommonValue

private val SEQRA_TRACE_DEBUG: Boolean = System.getenv("SEQRA_TRACE_DEBUG") != null

abstract class MethodAnalyzerEdgeSearcher(
    private val edges: MethodAnalyzerEdges,
    private val apManager: ApManager,
    private val analysisManager: AnalysisManager,
    private val analysisContext: MethodAnalysisContext,
    private val graph: MethodInstGraph,
) {
    abstract fun matchFact(factAtStatement: FinalFactAp, targetFactPattern: InitialFactAp): Boolean

    /**
     * [MethodAnalyzerEdges] indexes facts by [InitialFactAp.base] before [matchFact]. Subclasses may
     * return alternative shapes (e.g. deref-bridge) so lookup and matching use the same variant.
     */
    protected open fun traceTargetPatternVariants(storedFact: InitialFactAp): List<InitialFactAp> = listOf(storedFact)

    fun findMatchingEdgesInitialFacts(statement: CommonInst, fact: InitialFactAp): Set<Set<InitialFactAp>> {
        val matchingInitialFacts = hashSetOf<Set<InitialFactAp>>()

        val visitedStatements = hashSetOf<CommonInst>()
        val unprocessedStatements = mutableListOf(statement)

        while (unprocessedStatements.isNotEmpty()) {
            val stmt = unprocessedStatements.removeLast()
            if (!visitedStatements.add(stmt)) continue

            possiblePreconditionAtStatement(unprocessedStatements, stmt, fact).forEach { storedFact ->
                collectMatchingInitialFacts(stmt, storedFact, matchingInitialFacts)
            }
        }

        return matchingInitialFacts
    }

    private fun collectMatchingInitialFacts(
        stmt: CommonInst,
        storedFact: InitialFactAp,
        matchingInitialFacts: HashSet<Set<InitialFactAp>>,
    ) {
        if (SEQRA_TRACE_DEBUG) {
            System.err.println("[ES] storedFact=$storedFact stmt=$stmt")
        }
        for (p in traceTargetPatternVariants(storedFact)) {
            val z2f = edges.allZeroToFactFactsAtStatement(stmt, p)
            val f2f = edges.allFactToFactFactsAtStatement(stmt, p)
            val nd2f = edges.allNDFactToFactFactsAtStatement(stmt, p)

            if (SEQRA_TRACE_DEBUG) {
                val z2fMatch = z2f.count { matchFact(it, p) }
                val f2fMatch = f2f.count { (_, finalFact) -> matchFact(finalFact, p) }
                val ndMatch = nd2f.count { (_, finalFact) -> matchFact(finalFact, p) }
                System.err.println(
                    "[ES]   variant=$p z2f=${z2f.size}(match=$z2fMatch)" +
                            " f2f=${f2f.size}(match=$f2fMatch)" +
                            " nd=${nd2f.size}(match=$ndMatch)"
                )
            }

            if (z2f.any { matchFact(it, p) }) {
                matchingInitialFacts.add(emptySet())
            }

            f2f.forEach { (initialFact, finalFact) ->
                if (matchFact(finalFact, p)) {
                    matchingInitialFacts.add(setOf(initialFact))
                }
            }

            nd2f.forEach { (initialFacts, finalFact) ->
                if (matchFact(finalFact, p)) {
                    matchingInitialFacts.add(initialFacts)
                }
            }
        }
    }

    private fun possiblePreconditionAtStatement(
        unprocessed: MutableList<CommonInst>,
        statement: CommonInst,
        fact: InitialFactAp,
    ): List<InitialFactAp> {
        var predecessorsIsEmpty = true
        val result = mutableListOf<InitialFactAp>()

        graph.forEachPredecessor(analysisManager, statement) { predecessor ->
            predecessorsIsEmpty = false

            val facts = factsForPrecondition(predecessor, fact)
            if (facts != null) {
                result.addAll(facts)
                return@forEachPredecessor
            }

            unprocessed.add(predecessor)
        }

        if (predecessorsIsEmpty) {
            result.add(fact)
        }

        return result
    }

    private fun factsForPrecondition(statement: CommonInst, fact: InitialFactAp): List<InitialFactAp>? {
        val statementCall = analysisManager.getCallExpr(statement)
        if (statementCall != null) {
            val returnValue: CommonValue? = (statement as? CommonAssignInst)?.lhv

            val preconditionFunction = analysisManager.getMethodCallPrecondition(
                apManager, analysisContext, returnValue, statementCall, statement
            )
            val precondition = preconditionFunction.factPrecondition(fact)
            return when (precondition) {
                // todo: use provided fact instead of this list?
                is CallPrecondition.Facts -> precondition.facts.map { it.initialFact }
                CallPrecondition.Unchanged -> null
            }
        } else {
            val preconditionFunction = analysisManager.getMethodSequentPrecondition(
                apManager, analysisContext, statement
            )
            val precondition = preconditionFunction.factPrecondition(fact)
            return when (precondition) {
                // todo: use provided fact instead of this list?
                is SequentPrecondition.Facts -> precondition.facts.map { it.fact }
                SequentPrecondition.Unchanged -> null
            }
        }
    }
}
