package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkArrayAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkBaseAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkFieldAccess
import org.seqra.ir.api.cir.cfg.*

private typealias Access = MethodFlowFunctionUtils.Access

/**
 * Flow-insensitive alias groups derived from SeaDSA (embedded in the protocir module message).
 *
 * SeaDSA runs over LLVM IR, where transparent pointer casts (`bitcast`,
 * `array_to_ptrdecay`, `address_space`) collapse via `stripPointerCasts`
 * inside `Graph::mkCell`/`Graph::getCell`. On-disk groups therefore carry
 * canonical anchors (allocas / loads / arguments), not cast results. This
 * analysis expands each group with every CIR SSA value that reaches a member
 * through transparent `cir.cast`s, and records `cir.get_member` /
 * `cir.ptr_stride` lhs-shaped accesses so taint can replay onto SeaDSA peers.
 *
 * Any two SeaDSA-derived sets that share an [AccessPathBase] after expansion
 * are merged (same as unioning fallback load-address groups): see
 * [mergeAccessGroupLists].
 *
 * **Note:** IFDS flow helpers such as `resolveExprAccess` still treat every
 * `cir.cast` as forwarding to `src` for fact shape; only this class applies
 * cast-kind transparency when canonicalizing for alias equivalence (see TODOs
 * on those call sites).
 *
 * ## Complexity (per enclosing function, preprocessing once)
 *
 * Let `I` = instruction count, `M` = total SeaDSA group members, `C` = max length
 * of a transparent cast chain, `D` = total derived [Access] entries stored in
 * [derivedByCanonical], `A` = total [Access] entries across merged groups,
 * `B` = distinct [AccessPathBase] keys touched by union-find.
 *
 * - **Time:** `O(I + M·C + A·α(B))` — scans over instructions; each SeaDSA member
 *   is canonicalized through up to `C` cast steps; union-find merges are
 *   inverse-Ackermann in `B`. Derived and fallback scans are `O(I)`.
 * - **Space:** `O(I + D + A + B)` — instruction index, derived map, group sets,
 *   union-find parent map, and the final alias map (each base → frozen group).
 * - **Queries:** [findAliases] is `O(1)` expected; [areAliased] is `O(C)` per
 *   operand for canonicalization plus `O(|group|)` to test membership of
 *   `baseB` in `aliasGroupByBase[baseA]`.
 */
class CIRLocalAliasAnalysis(
    entryPoint: CIRInst,
    private val languageManager: CIRLanguageManager,
) {
    private val function: CIRFunction = entryPoint.location.method
    private val instById: Map<Long, CIRInst> = function.allInstructions.associateBy { it.id.id }

    private val derivedByCanonical: Map<AccessPathBase, Set<Access>> = buildDerivedByCanonical()

    private val aliasGroupByBase: Map<AccessPathBase, Set<Access>> =
        mergeAccessGroupLists(buildSeaDsaAliasGroups() + buildFallbackSameLoadAddressGroups())

    // --- public API ---

    fun findAliases(base: AccessPathBase): Set<Access>? = aliasGroupByBase[base]

    /**
     * True iff both values canonicalize to the same [AccessPathBase], or `b`'s
     * base appears in the alias class of `a`'s canonical base.
     */
    fun areAliased(a: MLIRValue, b: MLIRValue): Boolean {
        val baseA = canonicalBaseThroughTransparentCasts(a) ?: return false
        val baseB = canonicalBaseThroughTransparentCasts(b) ?: return false
        if (baseA == baseB) return true
        val group = aliasGroupByBase[baseA] ?: return false
        return group.any { it.base == baseB }
    }

    // --- SeaDSA and fallback group extraction ---

    /**
     * Expanded SeaDSA alias sets (empty when embedding is missing or yields no CIR peers).
     * Cross-group merging is delegated to [mergeAccessGroupLists] (same mechanism as fallback).
     */
    private fun buildSeaDsaAliasGroups(): List<Set<Access>> {
        val data = languageManager.cp.findFunctionAliasData(function.id) ?: return emptyList()
        return data.aliasGroups.mapNotNull { group ->
            val members = HashSet<Access>()
            for (member in group.members) {
                val canonical = canonicalBaseThroughTransparentCasts(member) ?: continue
                members += Access(canonical, null)
                members += derivedByCanonical[canonical].orEmpty()
            }
            members.takeIf { it.isNotEmpty() }?.toSet()
        }
    }

    /**
     * Loads lowered as `rhv = MLIRValueRef(addr)`: several loads of the same canonical
     * address must alias even if SeaDSA groups are absent (e.g. some wchar_t fixtures).
     */
    private fun buildFallbackSameLoadAddressGroups(): List<Set<Access>> {
        val addrKeyToLoadedBases = HashMap<AccessPathBase, MutableSet<AccessPathBase>>()
        for (inst in function.allInstructions.filterIsInstance<CIRAssignInst>()) {
            val rhv = inst.rhv
            if (rhv !is MLIRValueRef) continue
            if (inst.lhv !is MLIROpValue) continue
            val loadedBase = MethodFlowFunctionUtils.accessPathBase(inst.lhv) ?: continue
            val addrCanon = canonicalBaseThroughTransparentCasts(rhv.value) ?: continue
            addrKeyToLoadedBases.getOrPut(addrCanon) { mutableSetOf() }.add(loadedBase)
        }
        return addrKeyToLoadedBases.values
            .filter { it.size >= 2 }
            .map { bases -> bases.map { Access(it, null) }.toSet() }
    }

    // --- merging ---

    /**
     * Union every pair of bases that co-occur in the same input set, then attach all
     * [Access] entries of merged components to each representative root.
     */
    private fun mergeAccessGroupLists(groups: List<Set<Access>>): Map<AccessPathBase, Set<Access>> {
        val nonEmpty = groups.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyMap()

        val uf = AccessPathUnionFind()
        // Only cliques with 2+ bases contribute intra-group unions; singleton sets still attach below.
        for (g in nonEmpty) {
            val bases = g.mapNotNull { it.base }
            if (bases.size < 2) continue
            val head = bases[0]
            for (i in 1 until bases.size) uf.union(head, bases[i])
        }

        val rootToAccesses = LinkedHashMap<AccessPathBase, MutableSet<Access>>()
        for (g in nonEmpty) {
            val bases = g.mapNotNull { it.base }
            if (bases.isEmpty()) continue
            val root = uf.find(bases[0])
            rootToAccesses.getOrPut(root) { linkedSetOf() }.addAll(g)
        }

        val result = HashMap<AccessPathBase, Set<Access>>()
        for ((_, accesses) in rootToAccesses) {
            if (accesses.size < 2) continue
            val frozen = accesses.toSet()
            for (acc in accesses) acc.base?.let { result[it] = frozen }
        }
        return result
    }

    private class AccessPathUnionFind {
        private val parent = HashMap<AccessPathBase, AccessPathBase>()

        fun find(x: AccessPathBase): AccessPathBase {
            val p = parent[x]
            if (p == null) {
                parent[x] = x
                return x
            }
            if (p == x) return x
            val root = find(p)
            parent[x] = root
            return root
        }

        fun union(a: AccessPathBase, b: AccessPathBase) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
    }

    // --- derived index (casts / get_member / ptr_stride) ---

    private fun buildDerivedByCanonical(): Map<AccessPathBase, Set<Access>> {
        val out = HashMap<AccessPathBase, MutableSet<Access>>()
        for (inst in function.allInstructions.filterIsInstance<CIRAssignInst>()) {
            val rhv = inst.rhv
            val lhvBase = MethodFlowFunctionUtils.accessPathBase(inst.lhv) ?: continue
            val lhvAccess = mkBaseAccess(inst.lhv)
            when (rhv) {
                is CIRCastOpExpr,
                is CIRDynamicCastOpExpr,
                -> registerDerivedFromTransparentCastRhs(out, lhvAccess, rhv)

                is CIRPtrStrideOpExpr ->
                    out.getOrPut(lhvBase) { hashSetOf() }.add(mkArrayAccess(rhv.base))

                is CIRGetMemberOpExpr ->
                    out.getOrPut(lhvBase) { hashSetOf() }.add(
                        mkFieldAccess(rhv.addr, rhv.name.value, rhv.result),
                    )

                else -> Unit
            }
        }
        return out.mapValues { (_, v) -> v.toSet() }
    }

    private fun registerDerivedFromTransparentCastRhs(
        out: HashMap<AccessPathBase, MutableSet<Access>>,
        lhvAccess: Access,
        rhs: CIRExpr,
    ) {
        val src = transparentAliasCastSource(rhs) ?: return
        val canonicalBase = canonicalBaseThroughTransparentCasts(src) ?: return
        out.getOrPut(canonicalBase) { hashSetOf() }.add(lhvAccess)
    }

    // --- canonicalization ---

    /**
     * Strip transparent pointer casts (via assigning SSA ops) until a non-cast anchor.
     * Returns null on unsupported values or a cyclic cast chain.
     */
    private fun canonicalBaseThroughTransparentCasts(value: MLIRValue): AccessPathBase? {
        val seen = HashSet<Long>()
        var current: MLIRValue = value
        while (true) {
            when (val cur = current) {
                is MLIRValueRef -> current = cur.value
                is MLIROpValue -> {
                    val opId = cur.opIndex.id
                    if (!seen.add(opId)) return null
                    val assign = instById[opId] as? CIRAssignInst
                        ?: return MethodFlowFunctionUtils.accessPathBase(cur)
                    val src = transparentAliasCastSource(assign.rhv)
                    if (src != null) {
                        current = src
                    } else {
                        return MethodFlowFunctionUtils.accessPathBase(cur)
                    }
                }
                else -> return MethodFlowFunctionUtils.accessPathBase(cur)
            }
        }
    }

    /** Cast kinds that match LLVM `stripPointerCasts` / SeaDSA cell identity for pointers. */
    private fun transparentAliasCastSource(expr: CIRExpr): MLIRValue? = when (expr) {
        is CIRCastOpExpr ->
            expr.takeIf { it.kind.isTransparentForAlias() }?.src

        is CIRDynamicCastOpExpr ->
            expr.takeIf { it.kind.isTransparentForAlias() }?.src

        else -> null
    }

    private fun CIRCastKind.isTransparentForAlias(): Boolean = when (this) {
        CIRCastKind.Bitcast,
        CIRCastKind.ArrayToPtrdecay,
        CIRCastKind.AddressSpace,
        -> true

        else -> false
    }

    private fun CIRDynamicCastKind.isTransparentForAlias(): Boolean = when (this) {
        CIRDynamicCastKind.Ptr -> true
        else -> false
    }
}
