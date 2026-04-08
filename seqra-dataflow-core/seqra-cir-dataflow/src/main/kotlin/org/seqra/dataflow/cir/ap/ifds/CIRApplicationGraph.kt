package org.seqra.dataflow.cir.ap.ifds

import mu.KLogging
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRGraph
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRTryCallOpInst
import org.seqra.util.analysis.ApplicationGraph

open class CIRApplicationGraph(
    val cp: CIRClasspath,
) : ApplicationGraph<CIRFunction, CIRInst> {

    /**
     * Reverse call graph: callee symbol name → list of call-site instructions.
     * Built lazily on first [callers] access by scanning all defined functions.
     * Analogous to JVM's [SyncUsagesExtension] which uses a pre-built bytecode index.
     */
    private val reverseCallGraph: Map<String, List<CIRInst>> by lazy {
        buildReverseCallGraph()
    }

    override fun predecessors(node: CIRInst): Sequence<CIRInst> {
        val graph = graphOf(node)
        return graph.predecessors(node).asSequence() + graph.throwers(node).asSequence()
    }

    override fun successors(node: CIRInst): Sequence<CIRInst> {
        val graph = graphOf(node)
        return graph.successors(node).asSequence() + graph.catchers(node).asSequence()
    }

    override fun callees(node: CIRInst): Sequence<CIRFunction> {
        val calleeRef = when (node) {
            is CIRCallOpInst -> node.calleeRef
            is CIRTryCallOpInst -> node.calleeRef
            else -> null
        } ?: return emptySequence()
        val function = calleeRef.function ?: return emptySequence()
        return sequenceOf(function)
    }

    override fun callers(method: CIRFunction): Sequence<CIRInst> {
        return reverseCallGraph[method.name]?.asSequence() ?: emptySequence()
    }

    override fun entryPoints(method: CIRFunction): Sequence<CIRInst> = try {
        method.flowGraph().entries.asSequence()
    } catch (e: Throwable) {
        logger.error(e) { "Failed to get entry points for ${method.name}" }
        emptySequence()
    }

    override fun exitPoints(method: CIRFunction): Sequence<CIRInst> = try {
        method.flowGraph().exits.asSequence()
    } catch (e: Throwable) {
        logger.error(e) { "Failed to get exit points for ${method.name}" }
        emptySequence()
    }

    override fun methodOf(node: CIRInst): CIRFunction {
        return node.location.method
    }

    override fun statementsOf(method: CIRFunction): Sequence<CIRInst> {
        return method.allInstructions.asSequence()
    }

    private fun graphOf(node: CIRInst): CIRGraph {
        return node.method.flowGraph() as CIRGraph
    }

    private fun buildReverseCallGraph(): Map<String, List<CIRInst>> {
        val result = mutableMapOf<String, MutableList<CIRInst>>()
        val allFunctionIds = cp.db.persistence.findAllDefinedFunctionIds(cp)
        for (functionId in allFunctionIds) {
            val function = try {
                cp.findFunctionOrNull(functionId) ?: continue
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to load function ${functionId.id} for reverse call graph" }
                continue
            }
            for (inst in function.allInstructions) {
                val symbolName = when (inst) {
                    is CIRCallOpInst -> inst.calleeRef?.symbolName
                    is CIRTryCallOpInst -> inst.calleeRef?.symbolName
                    else -> null
                } ?: continue
                result.getOrPut(symbolName) { mutableListOf() }.add(inst)
            }
        }
        return result
    }

    companion object : KLogging()
}
