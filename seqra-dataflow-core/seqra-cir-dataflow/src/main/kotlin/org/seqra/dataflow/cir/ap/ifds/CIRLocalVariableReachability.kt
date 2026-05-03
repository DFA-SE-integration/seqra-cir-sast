package org.seqra.dataflow.cir.ap.ifds

import org.seqra.cir.graph.CApplicationGraph
import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.seqra.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.seqra.dataflow.util.containsAll
import org.seqra.dataflow.util.copy
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRExpr
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef
import org.seqra.ir.api.common.cfg.CommonInst
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties

/**
 * Liveness scaffolding for `MLIROpValue` (the SSA results that
 * `CIRMethodCallFactMapper`/`MethodFlowFunctionUtils` map to
 * `AccessPathBase.LocalVar`).
 *
 * Stage 1 (current behaviour): `isReachable` unconditionally returns `true`
 * for every `LocalVar`. This is over-conservative for performance but
 * required for correctness on CIR — see [isReachable] for why a strict
 * SSA-liveness gate breaks UAF detection.
 *
 * Stage 2 attempt (kept here, but **not** wired into [isReachable]):
 * [computeReachabilityViaLiveness] performs a backward live-range pass over
 * `MLIROpValue.opIndex.id`, marking which SSA results are live at each
 * gate point. It is sound w.r.t. SSA def-use chains and ready to be used
 * by [isReachableViaLiveness] once the issues described below are
 * resolved.
 */
class CIRLocalVariableReachability(
    private val method: CIRFunction,
    private val graph: CApplicationGraph,
    private val languageManager: CIRLanguageManager,
) {
    private val maxInstIdx = method.allInstructions.maxOfOrNull { it.location.index } ?: 0

    private val reachabilityInfo by lazy { computeReachabilityViaLiveness() }

    /**
     * Returns whether [base] is reachable at [statement].
     *
     * STAGE 1 (active): pessimistically `true` for every `LocalVar`.
     *
     * Why we cannot just hand the result of [computeReachabilityViaLiveness]
     * to the IFDS engine yet: CIR taint propagation lifts value-mark facts
     * (e.g. `LocalVar(N)![use-after-free].$`) from a freed SSA pointer onto
     * any aliasing pointer/storage **after** the free instruction
     * (typically through alias info from SeaDSA, applied during call /
     * exit summary lifting). In pure SSA-liveness terms, the freed value
     * is dead immediately after `free(%N)`, so a strict liveness gate
     * drops the fact before the alias-propagation step has a chance to
     * lift it onto the slot/pointer that the sink later observes. This
     * sinks all `return_freed_ptr_*` fixtures (a freed pointer flows
     * through a stack slot and is read back into the returned SSA value).
     *
     * Lifting this restriction needs **either** a richer `isReachable` API
     * that gets the full fact AP (so we can keep value-mark facts alive
     * regardless of SSA liveness), **or** modelling freed memory facts on
     * the alloca slot directly instead of on the SSA pointer.
     */
    fun isReachable(base: AccessPathBase, statement: CommonInst): Boolean {
        if (base !is AccessPathBase.LocalVar) return true
        return true
    }

    @Suppress("unused")
    private fun isReachableViaLiveness(base: AccessPathBase, statement: CommonInst): Boolean {
        if (base !is AccessPathBase.LocalVar) return true
        val storageIdx = instructionStorageIdx(statement, languageManager)
        val storage = reachabilityInfo[storageIdx] ?: return false
        return storage.get(base.idx)
    }

    private fun computeReachabilityViaLiveness(): Array<BitSet?> {
        val statementReachability = arrayOfNulls<BitSet?>(instructionStorageSize(maxInstIdx))
        val unprocessed = graph.exitPoints(method).mapTo(mutableListOf()) { it to BitSet() }

        while (unprocessed.isNotEmpty()) {
            val (statement, prevReachability) = unprocessed.removeLast()
            val storageIdx = instructionStorageIdx(statement, languageManager)
            val currentReachability = statementReachability[storageIdx]

            // Already saturated for this statement.
            if (currentReachability != null && currentReachability.containsAll(prevReachability)) {
                continue
            }

            val reachableLocalsAtStatement = BitSet().apply {
                or(prevReachability)
                if (currentReachability != null) or(currentReachability)
            }

            // Add USES: every `MLIROpValue` the instruction reads becomes live
            // at this statement (and at every predecessor up to its def).
            (statement as? CIRInst)?.forEachOperandValue { value ->
                if (value is MLIROpValue) {
                    reachableLocalsAtStatement.set(value.opIndex.id.toInt())
                }
            }

            statementReachability[storageIdx] = reachableLocalsAtStatement

            // Going BACKWARD across the def kills the SSA result it produced:
            // before this statement that value did not yet exist.
            val nextReachable = statement.assignedLocalVar()?.let { def ->
                reachableLocalsAtStatement.copy().also { it.clear(def.opIndex.id.toInt()) }
            } ?: reachableLocalsAtStatement

            graph.predecessors(statement).forEach { unprocessed.add(it to nextReachable) }
        }

        return statementReachability
    }
}

/**
 * The SSA result an instruction defines, if any.
 *
 * For CIR, this is the `lhv` of a `CIRAssignInst` **only when `lhv` is itself
 * an `MLIROpValue`** (i.e. a real SSA result — `cir.load %addr` becomes
 * `CIRAssignInst(lhv = MLIROpValue(...), rhv = MLIRValueRef(%addr))`).
 *
 * `cir.store %value, %addr` is also represented as a `CIRAssignInst`, but its
 * `lhv` is `MLIRValueRef(%addr)`. The store *reads* `%addr` (and writes to
 * memory) — it does NOT redefine `%addr`'s SSA value. Treating it as a def
 * would kill the address-carrying local in backward liveness, dropping every
 * fact about that local before it ever reached its real producer (e.g. a
 * `cir.alloca`). That regression sank all `return_freed_ptr_*_bad` fixtures.
 */
private fun CommonInst.assignedLocalVar(): MLIROpValue? = when (this) {
    is CIRAssignInst -> lhv as? MLIROpValue
    else -> null
}

/**
 * Walks every `MLIRValue` operand of this instruction (recursing through
 * `MLIRValueRef` and nested `CIRExpr` operands such as `cir.ptr_stride`'s
 * base/stride). Reflection metadata for each concrete `CIRInst` /
 * `CIRExpr` subclass is cached on first observation, so the walk is
 * effectively O(#operands) after warm-up.
 */
private fun CIRInst.forEachOperandValue(action: (MLIRValue) -> Unit) {
    OperandWalker.visitInst(this, action)
}

private object OperandWalker {
    private val instOperandsCache = ConcurrentHashMap<KClass<*>, List<KProperty1<Any, Any?>>>()
    private val exprOperandsCache = ConcurrentHashMap<KClass<*>, List<KProperty1<Any, Any?>>>()

    fun visitInst(inst: CIRInst, action: (MLIRValue) -> Unit) {
        val props = instOperandsCache.getOrPut(inst::class) { collectOperandProps(inst::class) }
        for (prop in props) {
            visitProp(prop.get(inst), action)
        }
    }

    private fun visitExpr(expr: CIRExpr, action: (MLIRValue) -> Unit) {
        if (expr is MLIRValue) {
            visitValue(expr, action)
            return
        }
        val props = exprOperandsCache.getOrPut(expr::class) { collectOperandProps(expr::class) }
        for (prop in props) {
            visitProp(prop.get(expr), action)
        }
    }

    private fun visitValue(value: MLIRValue, action: (MLIRValue) -> Unit) {
        when (value) {
            is MLIRValueRef -> visitValue(value.value, action)
            else -> action(value)
        }
    }

    private fun visitProp(value: Any?, action: (MLIRValue) -> Unit) {
        when (value) {
            null -> Unit
            is MLIRValue -> visitValue(value, action)
            is CIRExpr -> visitExpr(value, action)
            is List<*> -> for (item in value) visitProp(item, action)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectOperandProps(cls: KClass<*>): List<KProperty1<Any, Any?>> {
        return cls.memberProperties
            .filter { prop -> isOperandBearingType(prop) }
            .map { it as KProperty1<Any, Any?> }
            .toList()
    }

    private fun isOperandBearingType(prop: KProperty1<*, *>): Boolean {
        val classifier = prop.returnType.classifier as? KClass<*> ?: return false
        if (classifier.isSubclassOf(MLIRValue::class)) return true
        if (classifier.isSubclassOf(CIRExpr::class)) return true
        if (!classifier.isSubclassOf(List::class)) return false
        val elementClassifier =
            prop.returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*> ?: return false
        return elementClassifier.isSubclassOf(MLIRValue::class) ||
            elementClassifier.isSubclassOf(CIRExpr::class)
    }
}
