package org.seqra.cir.graph

import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.cfg.CIRDirectCall
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.resolveDirectCall
import org.seqra.ir.impl.features.SyncCIRUsagesExtension

open class CApplicationGraphImpl(
    override val cp: CIRClasspath,
    private val usages: SyncCIRUsagesExtension,
) : CApplicationGraph {
    override fun predecessors(node: CIRInst): Sequence<CIRInst> {
        val graph = node.method.flowGraph()
        val predecessors = graph.predecessors(node)
        val throwers = graph.throwers(node)
        return predecessors.asSequence() + throwers.asSequence()
    }

    override fun successors(node: CIRInst): Sequence<CIRInst> {
        val graph = node.location.method.flowGraph()
        val successors = graph.successors(node)
        val catchers = graph.catchers(node)
        return successors.asSequence() + catchers.asSequence()
    }

    override fun callees(node: CIRInst): Sequence<CIRFunction> {
        return when (node) {
            is CIRDirectCall -> node.resolveDirectCall(cp).asSequence()
            else -> emptySequence()
        }
    }

    override fun callers(method: CIRFunction): Sequence<CIRInst> {
        return usages.findUsages(method).flatMap { caller ->
            caller.allInstructions.asSequence()
                .filterIsInstance<CIRDirectCall>()
                .filter { inst -> inst.resolveDirectCall(cp).any { it.id == method.id } }
        }
    }

    override fun entryPoints(method: CIRFunction): Sequence<CIRInst> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: CIRFunction): Sequence<CIRInst> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: CIRInst): CIRFunction {
        return node.location.method
    }

    override fun statementsOf(method: CIRFunction): Sequence<CIRInst> {
        return method.allInstructions.asSequence()
    }
}
