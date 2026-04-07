package org.seqra.ir.impl.cfg

import org.seqra.ir.api.cir.cfg.*

class CIRGraphImpl(
    override val function: CIRFunction
) : CIRGraph {
    override val entry
        get() = instructions.first()
    override val entries
        get() = if (instructions.isEmpty()) listOf() else listOf(entry)
    override val exits: List<CIRInst> by lazy { instructions.filterIsInstance<CIRTerminatingInst>() }

    override val instructions: List<CIRInst>
        get() = function.blocks.blocks.flatMap { it.instructions }

    private val predecessorMap: Map<CIRInst, Set<CIRInst>>
    private val successorMap: Map<CIRInst, Set<CIRInst>>
    private val blockIdMap = hashMapOf<MLIRBlockID, MLIRBasicBlock>()

    init {
        val predecessorsPreparingMap = hashMapOf<CIRInst, MutableSet<CIRInst>>()
        val successorsPreparingMap = hashMapOf<CIRInst, MutableSet<CIRInst>>()

        for (block in function.blocks) {
            blockIdMap[block.id] = block
        }

        for (block in function.blocks) {
            block.instructions.forEachIndexed { i, current ->
                // else the predecessors will be added while processing terminator instructions
                if (i != 0) {
                    predecessorsPreparingMap[current] = mutableSetOf(block.instructions[i - 1])
                }

                if (i + 1 != block.instructions.size) {
                    successorsPreparingMap[current] = mutableSetOf(block.instructions[i + 1])
                } else {
                    when (current) {
                        is CIRBrCondOpInst -> {
                            val trueInst = blockIdMap[current.destTrue]!!.instructions.first()
                            val falseInst = blockIdMap[current.destFalse]!!.instructions.first()

                            successorsPreparingMap.putIfAbsent(current, mutableSetOf())
                            successorsPreparingMap[current]!!.add(trueInst)
                            successorsPreparingMap[current]!!.add(falseInst)

                            predecessorsPreparingMap.putIfAbsent(trueInst, mutableSetOf())
                            predecessorsPreparingMap[trueInst]!!.add(current)

                            predecessorsPreparingMap.putIfAbsent(falseInst, mutableSetOf())
                            predecessorsPreparingMap[falseInst]!!.add(current)
                        }

                        is CIRBrOpInst -> {
                            val destInst = blockIdMap[current.dest]!!.instructions.first()

                            successorsPreparingMap.putIfAbsent(current, mutableSetOf())
                            predecessorsPreparingMap.putIfAbsent(destInst, mutableSetOf())

                            successorsPreparingMap[current]!!.add(destInst)
                            predecessorsPreparingMap[destInst]!!.add(current)
                        }
                    }
                }
            }
        }
        predecessorMap = predecessorsPreparingMap
        successorMap = successorsPreparingMap
    }

    override fun throwers(node: CIRInst): Set<CIRInst> {
        return emptySet()
    }

    override fun catchers(node: CIRInst): Set<CIRInst> {
        return emptySet()
    }

    override fun successors(node: CIRInst): Set<CIRInst> = successorMap[node].orEmpty()
    override fun predecessors(node: CIRInst): Set<CIRInst> = predecessorMap[node].orEmpty()
}