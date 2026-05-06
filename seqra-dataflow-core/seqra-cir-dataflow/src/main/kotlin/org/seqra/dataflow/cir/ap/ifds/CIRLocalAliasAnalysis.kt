package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkArrayAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkBaseAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkFieldAccess
import org.seqra.ir.api.cir.cfg.*

/**
 * Flow-insensitive alias groups derived from SeaDSA (embedded in the protocir module message).
 *
 * SeaDSA runs over LLVM IR, where transparent pointer casts (`bitcast`,
 * `array_to_ptrdecay`, `address_space`) collapse via `stripPointerCasts`
 * inside `Graph::mkCell`/`Graph::getCell`. As a result, the on-disk groups
 * only carry the canonical anchors (allocas / loads / arguments) and never
 * the cast results themselves. To make alias queries from CIR taint flow
 * agree with that semantics, every group is expanded with all CIR SSA values
 * that reach a member through a chain of transparent `cir.cast`s, plus
 * lhs-bases of `cir.get_member` / `cir.ptr_stride` that close over a known
 * canonical anchor.
 *
 * Groups are then transitively closed by union-find: if any two sea-dsa cells
 * share a CIR-side base after expansion (e.g. one cell owns the alloca, the
 * other owns a derived bitcast that resolves to the same alloca), they are
 * merged into a single alias class.
 */
class CIRLocalAliasAnalysis(
    entryPoint: CIRInst,
    private val languageManager: CIRLanguageManager,
) {
    private val function: CIRFunction = entryPoint.location.method
    private val instById: Map<Long, CIRInst> = function.allInstructions.associateBy { it.id.id }

    private val derivedByCanonical: Map<AccessPathBase, Set<MethodFlowFunctionUtils.Access>> =
        computeDerivedByCanonical()

    private val aliasGroupByBase: Map<AccessPathBase, Set<MethodFlowFunctionUtils.Access>> = buildAliasGroups()

    fun findAliases(base: AccessPathBase): Set<MethodFlowFunctionUtils.Access>? = aliasGroupByBase[base]

    /**
     * Decide whether two MLIR values are reported aliased by the analysis.
     *
     * Both sides go through the same `cir.cast` strip used to build the
     * groups; values that reduce to the same canonical base are trivially
     * aliased, otherwise the alias class of the first base is consulted.
     */
    fun areAliased(a: MLIRValue, b: MLIRValue): Boolean {
        val baseA = propagateBaseThroughCasts(a) ?: return false
        val baseB = propagateBaseThroughCasts(b) ?: return false
        if (baseA == baseB) return true
        val group = aliasGroupByBase[baseA] ?: return false
        return group.any { it.base == baseB }
    }

    private fun buildAliasGroups(): Map<AccessPathBase, Set<MethodFlowFunctionUtils.Access>> {
        val data = languageManager.cp.findFunctionAliasData(function.id) ?: return emptyMap()

        // Step 1: per sea-dsa group, expand to the set of accesses we care about
        // (canonical anchor + derived lhv bases reaching it through casts /
        // field / element ops).
        val perGroup = data.aliasGroups.mapNotNull { group ->
            val members = HashSet<MethodFlowFunctionUtils.Access>()
            for (member in group.members) {
                val canonical = propagateBaseThroughCasts(member) ?: continue
                members += MethodFlowFunctionUtils.Access(canonical, null)
                members += derivedByCanonical[canonical].orEmpty()
            }
            members.takeIf { it.isNotEmpty() }
        }

        if (perGroup.isEmpty()) return emptyMap()

        // Step 2: union groups that share any base (transitive closure across
        // sea-dsa cells that our CIR-side enrichment connected).
        val n = perGroup.size
        val parent = IntArray(n) { it }
        val rank = IntArray(n)

        fun find(x: Int): Int {
            var cur = x
            while (parent[cur] != cur) {
                parent[cur] = parent[parent[cur]]
                cur = parent[cur]
            }
            return cur
        }

        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra == rb) return
            when {
                rank[ra] < rank[rb] -> parent[ra] = rb
                rank[ra] > rank[rb] -> parent[rb] = ra
                else -> { parent[rb] = ra; rank[ra]++ }
            }
        }

        val groupsContainingBase = HashMap<AccessPathBase, MutableList<Int>>()
        for ((i, group) in perGroup.withIndex()) {
            for (acc in group) acc.base?.let { groupsContainingBase.getOrPut(it) { ArrayList() }.add(i) }
        }
        for ((_, indices) in groupsContainingBase) {
            if (indices.size < 2) continue
            val anchor = indices[0]
            for (k in 1 until indices.size) union(anchor, indices[k])
        }

        // Step 3: collect merged accesses by representative.
        val merged = HashMap<Int, MutableSet<MethodFlowFunctionUtils.Access>>()
        for ((i, group) in perGroup.withIndex()) {
            merged.getOrPut(find(i)) { hashSetOf() }.addAll(group)
        }

        // Step 4: project to (base -> accesses) for query API; skip alias
        // classes that collapsed to <2 members after expansion.
        val result = HashMap<AccessPathBase, Set<MethodFlowFunctionUtils.Access>>()
        for ((_, accesses) in merged) {
            if (accesses.size < 2) continue
            for (acc in accesses) acc.base?.let { result[it] = accesses }
        }
        return result
    }

    /**
     * Walk every `CIRAssignInst` whose rhs is a transparent cast and bucket the
     * lhs base by the canonical anchor it ultimately reaches. Casts of casts
     * are folded transitively. Closures of `cir.get_member` / `cir.ptr_stride`
     * are recorded as field/element accesses on the lhs base so that taint
     * stored through such an access can be replayed onto sea-dsa peers.
     */
    private fun computeDerivedByCanonical(): Map<AccessPathBase, Set<MethodFlowFunctionUtils.Access>> {
        val out = HashMap<AccessPathBase, MutableSet<MethodFlowFunctionUtils.Access>>()
        for (inst in function.allInstructions.filterIsInstance<CIRAssignInst>()) {
            val rhv = inst.rhv
            val lhvBase = MethodFlowFunctionUtils.accessPathBase(inst.lhv) ?: continue
            val lhvAccess = mkBaseAccess(inst.lhv)
            when (rhv) {
                is CIRCastOpExpr -> {
                    if (!rhv.kind.isTransparentForAlias()) continue
                    val canonicalBase = propagateBaseThroughCasts(rhv.src) ?: continue
                    out.getOrPut(canonicalBase) { hashSetOf() }.add(lhvAccess)
                }
                is CIRDynamicCastOpExpr -> {
                    if (!rhv.kind.isTransparentForAlias()) continue
                    val canonicalBase = propagateBaseThroughCasts(rhv.src) ?: continue
                    out.getOrPut(canonicalBase) { hashSetOf() }.add(lhvAccess)
                }
                is CIRPtrStrideOpExpr -> {
                    out.getOrPut(lhvBase) { hashSetOf() }.add(mkArrayAccess(rhv.base))
                }
                is CIRGetMemberOpExpr -> {
                    out.getOrPut(lhvBase) { hashSetOf() }.add(
                        mkFieldAccess(rhv.addr, rhv.name.value, rhv.result)
                    )
                }
                else -> Unit
            }
        }
        return out
    }

    /**
     * Strip a chain of transparent `cir.cast`s to reach the underlying anchor
     * (alloca / load / arg / non-cast op) and return its [AccessPathBase].
     * Returns null only when the chain leaves the supported value space
     * (e.g. constants) or contains a cycle.
     */
    private fun propagateBaseThroughCasts(value: MLIRValue): AccessPathBase? {
        val seen = HashSet<Long>()
        var current: MLIRValue = value
        while (true) {
            when (val cur = current) {
                is MLIRValueRef -> current = cur.value
                is MLIROpValue -> {
                    val opId = cur.opIndex.id
                    if (!seen.add(opId)) return null
                    val inst = instById[opId] as? CIRAssignInst
                        ?: return MethodFlowFunctionUtils.accessPathBase(cur)
                    when (val rhv = inst.rhv) {
                        is CIRCastOpExpr -> {
                            if (!rhv.kind.isTransparentForAlias()) {
                                return MethodFlowFunctionUtils.accessPathBase(cur)
                            }
                            current = rhv.src
                        }
                        is CIRDynamicCastOpExpr -> {
                            if (!rhv.kind.isTransparentForAlias()) {
                                return MethodFlowFunctionUtils.accessPathBase(cur)
                            }
                            current = rhv.src
                        }
                        else -> return MethodFlowFunctionUtils.accessPathBase(cur)
                    }
                }
                else -> return MethodFlowFunctionUtils.accessPathBase(cur)
            }
        }
    }

    private fun CIRCastKind.isTransparentForAlias(): Boolean = when (this) {
        CIRCastKind.Bitcast,
        CIRCastKind.ArrayToPtrdecay,
        CIRCastKind.AddressSpace -> true

        else -> false
    }

    private fun CIRDynamicCastKind.isTransparentForAlias(): Boolean = when (this) {
        CIRDynamicCastKind.Ptr -> true

        else -> false
    }
}
