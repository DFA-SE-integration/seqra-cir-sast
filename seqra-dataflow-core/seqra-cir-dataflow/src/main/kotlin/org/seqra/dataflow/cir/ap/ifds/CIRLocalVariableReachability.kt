package org.seqra.dataflow.cir.ap.ifds

import org.seqra.cir.graph.CApplicationGraph
import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.seqra.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.seqra.dataflow.util.containsAll
import org.seqra.dataflow.util.copy
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.common.cfg.CommonInst
import java.util.*

class CIRLocalVariableReachability(
    private val method: CIRFunction,
    private val graph: CApplicationGraph,
    private val languageManager: CIRLanguageManager
) {
    private val maxInstIdx = method.allInstructions.maxOf { it.location.index }

    private val reachabilityInfo by lazy { computeReachability() }

    fun isReachable(base: AccessPathBase, statement: CommonInst): Boolean {
        if (base !is AccessPathBase.LocalVar) return true
        val storageIdx = instructionStorageIdx(statement, languageManager)
        val storage = reachabilityInfo[storageIdx] ?: return false
        return storage.get(base.idx)
    }

    private fun computeReachability(): Array<BitSet?> {
        val statementReachability = arrayOfNulls<BitSet?>(instructionStorageSize(maxInstIdx))
        val unprocessed = graph.exitPoints(method).mapTo(mutableListOf()) { it to BitSet() }

        while (unprocessed.isNotEmpty()) {
            val (statement, prevReachability) = unprocessed.removeLast()
            val storageIdx = instructionStorageIdx(statement, languageManager)
            val currentReachability = statementReachability[storageIdx]

            // no new reachability info
            if (currentReachability != null && currentReachability.containsAll(prevReachability)) {
                continue
            }

            val reachableLocalsAtStatement = BitSet()
            reachableLocalsAtStatement.or(prevReachability)
            if (currentReachability != null) {
                reachableLocalsAtStatement.or(currentReachability)
            }

//            statement.locals.forEach {
//                if (it is MLIRBlockValue) {
//                    reachableLocalsAtStatement.set(it.index)
//                }
//            }

            statementReachability[storageIdx] = reachableLocalsAtStatement

            val removedVar = statement.assignedLocalVar()
            val nextReachable = if (removedVar != null) {
                reachableLocalsAtStatement.copy().also { it.clear(removedVar.argIndex.toInt()) }
            } else {
                reachableLocalsAtStatement
            }

            graph.predecessors(statement).forEach { unprocessed.add(it to nextReachable) }
        }

        return statementReachability
    }
}

private fun CIRInst.assignedLocalVar(): MLIRBlockValue? = when (this) {
    is CIRAssignInst -> lhv as? MLIRBlockValue
//    TODO is CIRCatchParamOpInst -> throwable as? JIRLocalVar
    else -> null
}