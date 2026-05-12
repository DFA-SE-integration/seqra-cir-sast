package org.seqra.ir.impl.cfg

import org.seqra.ir.api.cir.cfg.*

/**
 * Based on {Project root}/clangir/clang/include/clang/CIR/Dialect/IR/CIROps.td
 *
 * Terminator trait(CIRTerminatingInst):
 *  - cir.return (::cir::ReturnOp)
 *  - cir.condition (::cir::ConditionOp)
 *  - cir.yield (::cir::YieldOp)
 *  - cir.break (::cir::BreakOp)
 *  - cir.continue (::cir::ContinueOp)
 *  - cir.resume (::cir::ResumeOp)
 *  - cir.br (::cir::BrOp)
 *  - cir.brcond (::cir::BrCondOp)
 *  - cir.try_call (::cir::TryCallOp)
 *  - cir.unreachable (::cir::UnreachableOp)
 *  - cir.trap (::cir::TrapOp)
 *  - cir.switch.flat (::cir::SwitchFlatOp)
 *  - cir.goto (::cir::GotoOp)
 *
 *  RegionBranchOpInterface:
 *  - cir.await (::cir::AwaitOp) TODO op.proto 311: // 3 regions are ignored for now
 *  - cir.case (::cir::CaseOp) TODO op.proto 502: // 1 regions are ignored for now
 *  - cir.do (::cir::DoWhileOp) TODO op.proto 720: // 2 regions are ignored for now
 *  - cir.for (::cir::ForOp) TODO op.proto 851: // 3 regions are ignored for now
 *  - cir.global (::cir::GlobalOp) TODO op.proto 985: // 2 regions are ignored for now
 *  - cir.if (::cir::IfOp) TODO op.proto 1005: // 2 regions are ignored for now
 *  - cir.scope (::cir::ScopeOp) TODO op.proto 1380: // 1 regions are ignored for now
 *  - cir.switch (::cir::SwitchOp) TODO op.proto // 1 regions are ignored for now
 *  - cir.ternary (::cir::TernaryOp) TODO // 2 regions are ignored for now
 *  - cir.try (::cir::TryOp) TODO // 2 regions are ignored for now
 *  - cir.while (::cir::WhileOp) TODO // 2 regions are ignored for now
 *
 *  Thrower(CIRThrowInterface):
 *  - cir.call with exception (::cir::CallOp) "If the cir.call has the exception keyword, the call can throw."
 *  - cir.try_call (::cir::TryCallOp)
 *  - cir.throw (::cir::ThrowOp)
 *  - cir.dyn_cast with kind = ref (::cir::DynamicCastOp) "If kind is ref, the operation will throw a bad_cast exception."
 *
 *  Catchers(CIRCatchInterface):
 *  - cir.try (::cir::TryOp)
 *  - cir.catch_param (::cir::CatchParamOp)
 *  - cir.eh.inflight_exception (::cir::EhInflightOp)
 */
class CIRGraphImpl(
    override val function: CIRFunction
) : CIRGraph {
    override val entry
        get() = instructions.first()
    override val entries
        get() = if (instructions.isEmpty()) listOf() else listOf(entry)
    override val exits: List<CIRInst> by lazy { instructions.filterIsInstance<CIRTerminatingInst>() }

    override val instructions: List<CIRInst>
        get() = function.allInstructions

    private val predecessorMap: Map<CIRInst, Set<CIRInst>>
    private val successorMap: Map<CIRInst, Set<CIRInst>>
    private val throwPredecessorsMap: Map<CIRInst, Set<CIRInst>>
    private val throwSuccessorsMap: Map<CIRInst, Set<CIRInst>>
    private val blockIdMap = hashMapOf<MLIRBlockID, MLIRBasicBlock>()

    private fun connectBlockSuccessor(
        source: CIRInst,
        destination: MLIRBlockID,
        predecessorsPreparingMap: HashMap<CIRInst, MutableSet<CIRInst>>,
        successorsPreparingMap: HashMap<CIRInst, MutableSet<CIRInst>>,
    ) {
        val destinationInst = blockIdMap[destination]!!.instructions.first()

        successorsPreparingMap.putIfAbsent(source, mutableSetOf())
        successorsPreparingMap[source]!!.add(destinationInst)

        predecessorsPreparingMap.putIfAbsent(destinationInst, mutableSetOf())
        predecessorsPreparingMap[destinationInst]!!.add(source)
    }

    private fun connectExceptionalSuccessors(
        source: CIRInst,
        destination: MLIRBlockID,
        throwPredecessorsPreparingMap: HashMap<CIRInst, MutableSet<CIRInst>>,
        throwSuccessorsPreparingMap: HashMap<CIRInst, MutableSet<CIRInst>>,
    ) {
        if (source !is CIRThrowInterface) return

        val destinationBlock = blockIdMap[destination] ?: return
        val catchers = destinationBlock.instructions.filter { it is CIRCatchInterface }
        if (catchers.isEmpty()) return

        catchers.forEach { catcher ->
            throwSuccessorsPreparingMap.putIfAbsent(source, mutableSetOf())
            throwSuccessorsPreparingMap[source]!!.add(catcher)

            throwPredecessorsPreparingMap.putIfAbsent(catcher, mutableSetOf())
            throwPredecessorsPreparingMap[catcher]!!.add(source)
        }
    }

    init {
        val predecessorsPreparingMap = hashMapOf<CIRInst, MutableSet<CIRInst>>()
        val successorsPreparingMap = hashMapOf<CIRInst, MutableSet<CIRInst>>()
        val throwPredecessorsPreparingMap = hashMapOf<CIRInst, MutableSet<CIRInst>>()
        val throwSuccessorsPreparingMap = hashMapOf<CIRInst, MutableSet<CIRInst>>()

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
                        /** cir.br (::cir::BrOp)
                         * The cir.br branches unconditionally to a block
                         *
                         *   cir.br ^bb3
                         *   ^bb3:
                         *     cir.return
                         */
                        is CIRBrOpInst -> {
                            connectBlockSuccessor(current, current.dest, predecessorsPreparingMap, successorsPreparingMap)
                        }

                        /** cir.call (::cir::CallOp)
                         * Direct/undirect call to a function that is within the same symbol scope as the call
                         *
                         * // Direct call
                         * %2 = cir.call @my_add(%0, %1) : (f32, f32) -> f32
                         * // Indirect call
                         * %20 = cir.call %18(%17)
                         *
                         * TODO
                         * // Call that might throw
                         * cir.call exception @my_div() -> () cleanup {
                         *   // call dtor...
                         * }
                         */
                        is CIRCallOpInst -> {
                            current.resolveDirectCall(function.classpath)
                                .filter { it.blocks.blocks.isNotEmpty() }
                                .forEach { c ->
                                    connectBlockSuccessor(current, c.blocks.blocks[0].id, predecessorsPreparingMap, successorsPreparingMap)
                                }

                        }

                        /** cir.brcond (::cir::BrCondOp)
                         * The cir.brcond %cond, ^bb0, ^bb1 branches to ‘bb0’ block in case %cond evaluates to true,
                         * otherwise it branches to ‘bb1’
                         *
                         *   cir.brcond %a, ^bb3, ^bb4
                         *   ^bb3:
                         *     cir.return
                         *   ^bb4:
                         *     cir.yield
                         */
                        is CIRBrCondOpInst -> {
                            connectBlockSuccessor(current, current.destTrue, predecessorsPreparingMap, successorsPreparingMap)
                            connectBlockSuccessor(current, current.destFalse, predecessorsPreparingMap, successorsPreparingMap)
                        }

                        /** cir.switch.flat (::cir::SwitchFlatOp)
                         *
                         * The cir.switch.flat operation is a region-less and simplified version of the cir.switch.
                         * Its representation is closer to LLVM IR dialect than the C/C++ language feature.
                         */
                        is CIRSwitchFlatOpInst -> {
                            current.caseDestinations.forEach { dest ->
                                connectBlockSuccessor(current, dest, predecessorsPreparingMap,
                                    successorsPreparingMap)
                            }
                            connectBlockSuccessor(current, current.defaultDestination, predecessorsPreparingMap,
                                successorsPreparingMap)
                        }

                        /** cir.try_call (::cir::TryCallOp)
                         * Mostly similar to cir.call but requires two destination branches, one for handling exceptions in case its thrown and the other one to follow on regular control-flow.
                         *
                         * // Direct call
                         * %2 = cir.try_call @my_add(%0, %1) ^continue, ^landing_pad : (f32, f32) -> f32
                         */
                        is CIRTryCallOpInst -> {
                            connectBlockSuccessor(current, current.cont, predecessorsPreparingMap, successorsPreparingMap)
                            connectBlockSuccessor(current, current.landingPad, predecessorsPreparingMap, successorsPreparingMap)
                            connectExceptionalSuccessors(
                                current,
                                current.landingPad,
                                throwPredecessorsPreparingMap,
                                throwSuccessorsPreparingMap,
                            )
                        }
                    }
                }
            }
        }
        predecessorMap = predecessorsPreparingMap
        successorMap = successorsPreparingMap
        throwPredecessorsMap = throwPredecessorsPreparingMap
        throwSuccessorsMap = throwSuccessorsPreparingMap
    }

    override fun throwers(node: CIRInst): Set<CIRInst> = throwPredecessorsMap[node].orEmpty()

    override fun catchers(node: CIRInst): Set<CIRInst> = throwSuccessorsMap[node].orEmpty()

    override fun successors(node: CIRInst): Set<CIRInst> = successorMap[node].orEmpty()
    override fun predecessors(node: CIRInst): Set<CIRInst> = predecessorMap[node].orEmpty()
}
