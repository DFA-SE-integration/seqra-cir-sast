package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkArrayAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkBaseAccess
import org.seqra.dataflow.cir.ap.ifds.MethodFlowFunctionUtils.mkFieldAccess
import org.seqra.ir.api.cir.CIRFunctionAliasData
import org.seqra.ir.api.cir.cfg.CIRAllocaOpInst
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRCastKind
import org.seqra.ir.api.cir.cfg.CIRCastOpExpr
import org.seqra.ir.api.cir.cfg.CIRDynamicCastKind
import org.seqra.ir.api.cir.cfg.CIRDynamicCastOpExpr
import org.seqra.ir.api.cir.cfg.CIRExpr
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRGetMemberOpExpr
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRLoadOpInst
import org.seqra.ir.api.cir.cfg.CIRPtrStrideOpExpr
import org.seqra.ir.api.cir.cfg.CIRStoreOpInst
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef

/**
 * Pointer alias / points-to graph for a single CIR function.
 *
 * Replaces the ad-hoc indices in [CIRLocalAliasAnalysis] (`derivedByCanonical`, `derefAddrByLoadedBase`,
 * `aliasGroupByBase`, the per-query backward `instById` walks) with two explicit relations:
 *
 * - **must-alias-on-address** ([uf]): pointer-typed SSAs that may name the same memory cell. Seeded from
 *   SeaDSA groups (pointer-typed members only — non-pointer scalars sharing a SeaDSA cell are dropped
 *   structurally) and from transparent-cast equivalences.
 *
 * - **points-to** ([pointsToDirect] + [pointedByClosure]): `addr_class → {loaded values}` from `cir.load`
 *   and `cir.store`. [pointedByClosure] is the inverse, precomputed through derived-access edges so that
 *   a loop interior pointer derived via `ptr_stride`/`get_member`/cast still points back at its load slot.
 *
 * - **derived-access** ([derivedAccess]): `derivedValue → {(anchor, accessor)}` for `ptr_stride`,
 *   `get_member`, and transparent casts. Used both for fact rebase (preserves field/element offsets)
 *   and to compute the [pointedByClosure].
 *
 * Pointer-likeness is decided by **op-kind** (alloca, ptr_stride, get_member produce pointers; cast
 * propagates from source) plus a type-prefix fallback for ambiguous cases (load result, argument).
 * This avoids the previous string-match-only heuristic and keeps non-pointer SeaDSA cell members out
 * of alias classes structurally.
 */
internal class AliasGraph private constructor(
    private val canonical: Map<AccessPathBase, AccessPathBase>,
    private val uf: UnionFind,
    private val pointsToDirect: Map<AccessPathBase, Set<AccessPathBase>>,
    private val pointedByClosure: Map<AccessPathBase, Set<AccessPathBase>>,
    private val derivedAccess: Map<AccessPathBase, Set<MethodFlowFunctionUtils.Access>>,
) {
    /** Canonical class representative of [b]. Returns [b] itself if unknown. */
    fun classOf(b: AccessPathBase): AccessPathBase {
        val c = canonical[b] ?: b
        return uf.find(c)
    }

    /** True iff [a] and [b] resolve to the same equivalence class. */
    fun inSameClass(a: AccessPathBase, b: AccessPathBase): Boolean =
        a == b || classOf(a) == classOf(b)

    /**
     * Aliases of [b] for fact rebase. Returns notations of the same memory cell:
     * - equivalence-class peers as `Access(peer, null)` (fact at b → fact at peer);
     * - derived-access entries `Access(anchor, accessor)` for every `b = derive(anchor, accessor)`
     *   (fact at b → fact at `anchor.[accessor]`).
     *
     * The accessor on the derived side is critical: `lhv = ptr_stride(rhvBase)` means fact at lhv
     * is fact at `rhvBase.[ElementAccessor]`, not fact at `rhvBase`.
     */
    fun aliasesOf(b: AccessPathBase): Set<MethodFlowFunctionUtils.Access> {
        val root = classOf(b)
        val out = LinkedHashSet<MethodFlowFunctionUtils.Access>()
        for (member in uf.classMembers(root)) {
            if (member == b) continue
            out += MethodFlowFunctionUtils.Access(member, null)
        }
        derivedAccess[b]?.let { out.addAll(it) }
        // also surface derived-access edges that hang off any equivalence-class peer
        for (member in uf.classMembers(root)) {
            if (member == b) continue
            derivedAccess[member]?.let { out.addAll(it) }
        }
        return out
    }

    /**
     * Closure of "slots from which [b] (or any of its derivation-ancestors) was loaded". Used by the
     * loop-interior-pointer bridge: `slot in pointedByOf(loopPtr)` iff `loopPtr` is reachable backwards
     * via `ptr_stride`/`get_member`/cast from a `load(slot-or-alias)`.
     */
    fun pointedByOf(b: AccessPathBase): Set<AccessPathBase> {
        val root = classOf(b)
        return pointedByClosure[root].orEmpty()
    }

    /** Strip transparent casts. Equivalent to the old `canonicalAccessPathBase`. */
    fun canonicalBase(value: MLIRValue): AccessPathBase? {
        val raw = accessPathBase(value) ?: return null
        return canonical[raw] ?: raw
    }

    // --- builder ---

    companion object {
        fun build(
            function: CIRFunction,
            instById: Map<Long, CIRInst>,
            aliasData: CIRFunctionAliasData?,
        ): AliasGraph {
            val canonical = buildCanonical(function, instById)
            val pointerLike = buildPointerLike(function, canonical)
            val uf = UnionFind()

            // Seed UF: union members of each SeaDSA group, but only pointer-typed canonicals.
            aliasData?.aliasGroups?.forEach { group ->
                val members = group.members.mapNotNull { m ->
                    val raw = accessPathBase(m) ?: return@mapNotNull null
                    val c = canonical[raw] ?: raw
                    c.takeIf { it in pointerLike }
                }
                if (members.size >= 2) {
                    val head = members[0]
                    for (i in 1 until members.size) uf.union(head, members[i])
                }
            }

            val derivedAccess = buildDerivedAccess(function, canonical, pointerLike)
            val pointsToDirect = buildPointsToDirect(function, canonical, pointerLike, uf)

            // Also union loaded-values that share a single load-address class — fallback for fixtures
            // where SeaDSA is absent and the same alloca is loaded several times.
            for ((_, loaded) in pointsToDirect) {
                if (loaded.size < 2) continue
                val head = loaded.first()
                for (m in loaded) if (m != head) uf.union(head, m)
            }

            val pointedByClosure = buildPointedByClosure(pointsToDirect, derivedAccess, canonical, uf)

            return AliasGraph(
                canonical = canonical,
                uf = uf,
                pointsToDirect = pointsToDirect,
                pointedByClosure = pointedByClosure,
                derivedAccess = derivedAccess,
            )
        }

        /**
         * Walk transparent cast chains and record `LocalVar(castResult) -> canonical anchor`. Anchors
         * are always alloca / load / argument / non-cast assign results, never another cast.
         */
        private fun buildCanonical(
            function: CIRFunction,
            instById: Map<Long, CIRInst>,
        ): Map<AccessPathBase, AccessPathBase> {
            val out = HashMap<AccessPathBase, AccessPathBase>()
            for (inst in function.allInstructions) {
                if (inst !is CIRAssignInst) continue
                val lhv = inst.lhv as? MLIROpValue ?: continue
                val lhvBase = accessPathBase(lhv) ?: continue
                val anchor = stripTransparentCasts(lhvBase, instById) ?: continue
                if (anchor != lhvBase) out[lhvBase] = anchor
            }
            return out
        }

        private fun stripTransparentCasts(
            start: AccessPathBase,
            instById: Map<Long, CIRInst>,
        ): AccessPathBase? {
            val seen = HashSet<AccessPathBase>()
            var cur: AccessPathBase = start
            while (cur is AccessPathBase.LocalVar) {
                if (!seen.add(cur)) return null
                val def = instById[cur.idx.toLong()] as? CIRAssignInst ?: return cur
                val src = transparentCastSrc(def.rhv) ?: return cur
                val next = accessPathBase(src) ?: return cur
                if (next == cur) return cur
                cur = next
            }
            return cur
        }

        private fun transparentCastSrc(expr: CIRExpr): MLIRValue? = when (expr) {
            is CIRCastOpExpr -> if (expr.kind.transparent()) expr.src else null
            is CIRDynamicCastOpExpr -> if (expr.kind.transparent()) expr.src else null
            else -> null
        }

        private fun CIRCastKind.transparent(): Boolean = when (this) {
            CIRCastKind.Bitcast,
            CIRCastKind.ArrayToPtrdecay,
            CIRCastKind.AddressSpace -> true
            else -> false
        }

        private fun CIRDynamicCastKind.transparent(): Boolean = this == CIRDynamicCastKind.Ptr

        /**
         * Pointer-like APBases: structurally producing-pointer ops (alloca, ptr_stride, get_member),
         * propagated through transparent casts, plus argument/load with `!cir.ptr` / `!llvm.ptr` type.
         * Used to drop non-pointer SeaDSA cell pollution (e.g. `!u64i` size operands of `operator delete`).
         */
        private fun buildPointerLike(
            function: CIRFunction,
            canonical: Map<AccessPathBase, AccessPathBase>,
        ): Set<AccessPathBase> {
            val out = HashSet<AccessPathBase>()

            for (p in function.parameters) {
                if (looksLikePointer(p.type)) out += AccessPathBase.Argument(p.index)
            }
            // Defensive defaults for special bases (used by `this` / static / return relays).
            out += AccessPathBase.This
            out += AccessPathBase.Return
            out += AccessPathBase.Exception

            for (inst in function.allInstructions) {
                when (inst) {
                    is CIRAllocaOpInst -> {
                        out += AccessPathBase.LocalVar(inst.id.id.toInt())
                    }
                    is CIRLoadOpInst -> {
                        if (looksLikePointer(inst.result)) {
                            out += AccessPathBase.LocalVar(inst.id.id.toInt())
                        }
                    }
                    is CIRAssignInst -> {
                        val lhv = inst.lhv as? MLIROpValue ?: continue
                        val base = accessPathBase(lhv) ?: continue
                        val isPtr = when (val rhv = inst.rhv) {
                            is CIRPtrStrideOpExpr, is CIRGetMemberOpExpr -> true
                            is CIRCastOpExpr -> if (rhv.kind.transparent()) true else looksLikePointer(lhv.type)
                            is CIRDynamicCastOpExpr -> if (rhv.kind.transparent()) true else looksLikePointer(lhv.type)
                            is MLIRValueRef -> looksLikePointer(lhv.type)
                            else -> looksLikePointer(lhv.type)
                        }
                        if (isPtr) out += base
                    }
                    else -> Unit
                }
            }

            // Anything that ever appears as the address operand of a load or store must be a pointer
            // — useful when SeaDSA groups reference an argument we missed by name.
            for (inst in function.allInstructions) {
                val addrs: List<MLIRValue> = when (inst) {
                    is CIRLoadOpInst -> listOf(inst.addr)
                    is CIRStoreOpInst -> listOf(inst.addr)
                    is CIRAssignInst -> listOfNotNull(
                        (inst.rhv as? MLIRValueRef)?.value,   // lowered load
                        (inst.lhv as? MLIRValueRef)?.value,   // lowered store
                    )
                    else -> emptyList()
                }
                for (addr in addrs) {
                    val a = accessPathBase(addr) ?: continue
                    out += (canonical[a] ?: a)
                }
            }

            val canonicalized = HashSet<AccessPathBase>(out.size)
            for (b in out) canonicalized += (canonical[b] ?: b)
            return canonicalized
        }

        private fun looksLikePointer(type: MLIRTypeID): Boolean {
            val id = type.id
            return id.startsWith("!cir.ptr") || id.startsWith("!llvm.ptr")
        }

        /**
         * Edges `lhv -> Access(anchor, accessor)` for `lhv = derived(anchor, accessor)`. Reading direction:
         * "fact at lhv equals fact at anchor.[accessor]". Covers `ptr_stride` (ElementAccessor),
         * `get_member` (FieldAccessor), and transparent casts (null accessor — pure rename).
         * Only entries with pointer-like anchors are retained.
         */
        private fun buildDerivedAccess(
            function: CIRFunction,
            canonical: Map<AccessPathBase, AccessPathBase>,
            pointerLike: Set<AccessPathBase>,
        ): Map<AccessPathBase, Set<MethodFlowFunctionUtils.Access>> {
            val out = HashMap<AccessPathBase, MutableSet<MethodFlowFunctionUtils.Access>>()
            for (inst in function.allInstructions) {
                if (inst !is CIRAssignInst) continue
                val lhv = inst.lhv as? MLIROpValue ?: continue
                val lhvBase = accessPathBase(lhv) ?: continue
                when (val rhv = inst.rhv) {
                    is CIRPtrStrideOpExpr -> {
                        val anchorRaw = accessPathBase(rhv.base) ?: continue
                        val anchor = canonical[anchorRaw] ?: anchorRaw
                        if (anchor !in pointerLike) continue
                        out.getOrPut(lhvBase) { hashSetOf() }.add(mkArrayAccess(rhv.base))
                    }
                    is CIRGetMemberOpExpr -> {
                        val anchorRaw = accessPathBase(rhv.addr) ?: continue
                        val anchor = canonical[anchorRaw] ?: anchorRaw
                        if (anchor !in pointerLike) continue
                        out.getOrPut(lhvBase) { hashSetOf() }.add(mkFieldAccess(rhv.addr, rhv.name.value, rhv.result))
                    }
                    is CIRCastOpExpr -> {
                        if (!rhv.kind.transparent()) continue
                        val anchorRaw = accessPathBase(rhv.src) ?: continue
                        val anchor = canonical[anchorRaw] ?: anchorRaw
                        if (anchor !in pointerLike) continue
                        out.getOrPut(lhvBase) { hashSetOf() }.add(mkBaseAccess(rhv.src))
                    }
                    is CIRDynamicCastOpExpr -> {
                        if (!rhv.kind.transparent()) continue
                        val anchorRaw = accessPathBase(rhv.src) ?: continue
                        val anchor = canonical[anchorRaw] ?: anchorRaw
                        if (anchor !in pointerLike) continue
                        out.getOrPut(lhvBase) { hashSetOf() }.add(mkBaseAccess(rhv.src))
                    }
                    else -> Unit
                }
            }
            return out.mapValues { it.value.toSet() }
        }

        /**
         * `addr_class -> {loaded values}` from loads (CIRLoadOpInst and the lowered `lhv = MLIRValueRef(addr)`
         * form). Stores feed the same map: after `store v at addr`, `*addr` includes `v`.
         */
        private fun buildPointsToDirect(
            function: CIRFunction,
            canonical: Map<AccessPathBase, AccessPathBase>,
            pointerLike: Set<AccessPathBase>,
            uf: UnionFind,
        ): Map<AccessPathBase, Set<AccessPathBase>> {
            val out = HashMap<AccessPathBase, MutableSet<AccessPathBase>>()
            fun addEdge(addr: MLIRValue, value: MLIRValue) {
                val addrRaw = accessPathBase(addr) ?: return
                val valRaw = accessPathBase(value) ?: return
                val addrC = canonical[addrRaw] ?: addrRaw
                val valC = canonical[valRaw] ?: valRaw
                // Require only the *addr* side to be pointer-like — the addr must name a memory slot.
                // The stored/loaded value may be of any type (`*p = malloc()` stores a pointer whose
                // producer is a call we don't structurally classify; `*p = 5` stores a scalar). We
                // record both because points-to is consumed by taint/UAF logic, not by the alias UF.
                if (addrC !in pointerLike) return
                val addrRoot = uf.find(addrC)
                out.getOrPut(addrRoot) { hashSetOf() }.add(valC)
            }
            for (inst in function.allInstructions) {
                when (inst) {
                    is CIRLoadOpInst -> {
                        val loadedOp = MLIROpValue(inst.result, inst.id, 0L)
                        addEdge(inst.addr, loadedOp)
                    }
                    is CIRStoreOpInst -> {
                        addEdge(inst.addr, inst.value)
                    }
                    is CIRAssignInst -> {
                        val lhv = inst.lhv
                        val rhv = inst.rhv
                        when {
                            // Lowered load: `lhv = ref(addr)`.
                            rhv is MLIRValueRef && lhv is MLIROpValue ->
                                addEdge(rhv.value, lhv)
                            // Lowered store: `ref(addr) = value`. CIRStoreOpInst is rewritten to this
                            // shape by CIRLoadStoreFeature; without store edges, `*p = malloc()` is
                            // invisible to points-to and the UAF mark cannot reach by-reference args.
                            lhv is MLIRValueRef && rhv is MLIRValue ->
                                addEdge(lhv.value, rhv)
                        }
                    }
                    else -> Unit
                }
            }
            return out.mapValues { it.value.toSet() }
        }

        /**
         * `valueClass -> {addr classes from which any derivation-ancestor of valueClass was loaded}`.
         * Built by: for each direct `addr -> value` edge in [pointsToDirect], propagate the addr through
         * the derived-access graph forward (so descendants via `ptr_stride`/`get_member`/cast inherit it).
         */
        private fun buildPointedByClosure(
            pointsToDirect: Map<AccessPathBase, Set<AccessPathBase>>,
            derivedAccess: Map<AccessPathBase, Set<MethodFlowFunctionUtils.Access>>,
            canonical: Map<AccessPathBase, AccessPathBase>,
            uf: UnionFind,
        ): Map<AccessPathBase, Set<AccessPathBase>> {
            // derivedAccess: lhv -> {(anchor, _)}. Forward derivation edge: anchor -> lhv.
            // Build anchor_class -> {descendant_class}.
            val forward = HashMap<AccessPathBase, MutableSet<AccessPathBase>>()
            for ((lhv, accs) in derivedAccess) {
                val lhvR = uf.find(canonical[lhv] ?: lhv)
                for (a in accs) {
                    val anchor = a.base ?: continue
                    val anchorR = uf.find(canonical[anchor] ?: anchor)
                    if (anchorR == lhvR) continue
                    forward.getOrPut(anchorR) { hashSetOf() }.add(lhvR)
                }
            }
            val out = HashMap<AccessPathBase, MutableSet<AccessPathBase>>()
            for ((addrRoot, loadedClasses) in pointsToDirect) {
                for (loaded in loadedClasses) {
                    val loadedRoot = uf.find(canonical[loaded] ?: loaded)
                    val visited = HashSet<AccessPathBase>()
                    val q = ArrayDeque<AccessPathBase>()
                    q += loadedRoot
                    while (q.isNotEmpty()) {
                        val n = q.removeFirst()
                        if (!visited.add(n)) continue
                        out.getOrPut(n) { hashSetOf() }.add(addrRoot)
                        forward[n]?.forEach { if (it !in visited) q += it }
                    }
                }
            }
            return out.mapValues { it.value.toSet() }
        }
    }

    // --- union-find ---

    internal class UnionFind {
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
            if (ra == rb) return
            parent[ra] = rb
        }

        fun classMembers(root: AccessPathBase): Set<AccessPathBase> {
            val r = find(root)
            val out = LinkedHashSet<AccessPathBase>()
            for (node in parent.keys) {
                if (find(node) == r) out += node
            }
            return out
        }
    }
}
