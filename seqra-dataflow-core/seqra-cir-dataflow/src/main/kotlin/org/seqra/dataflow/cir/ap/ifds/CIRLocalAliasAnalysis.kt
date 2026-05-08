package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.ReferenceAccessor
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.MLIRValue

private typealias Access = MethodFlowFunctionUtils.Access

/**
 * Per-function pointer alias / points-to view over CIR.
 *
 * Thin facade over [AliasGraph], which holds the actual data:
 *
 * - **must-alias-on-address** — pointer-typed SSAs that may name the same memory cell. Seeded from
 *   SeaDSA groups (filtered to pointer-typed members so non-pointer cell pollution can't enter
 *   the equivalence class) and from transparent-cast chains.
 *
 * - **points-to** (`addr_class → loaded values`) and **pointed-by closure**
 *   (`value_class → load-ancestor slots through `ptr_stride` / `get_member` / cast`). Built from
 *   `cir.load` / `cir.store` (including the lowered `MLIRValueRef(addr) = value` shape produced
 *   by `CIRLoadStoreFeature`).
 *
 * - **derived-access** (`lhv → (anchor, accessor)`) for `ptr_stride`, `get_member`, and
 *   transparent casts. Used for fact rebase: a fact at the derived value rebases to
 *   `anchor.[accessor].{rest}`.
 *
 * Build is single-pass per function; queries are `O(class size)` for [findAliases], `O(1)` for the
 * rest. See [AliasGraph] for the internals.
 */
class CIRLocalAliasAnalysis(
    entryPoint: CIRInst,
    private val languageManager: CIRLanguageManager,
) {
    private val function: CIRFunction = entryPoint.location.method
    private val instById: Map<Long, CIRInst> = function.allInstructions.associateBy { it.id.id }

    internal val aliasGraph: AliasGraph =
        AliasGraph.build(function, instById, languageManager.cp.findFunctionAliasData(function.id))

    /**
     * Aliases of [base] for fact rebase. Returns `null` if [base] has no peers (empty class and no
     * derived-access entries) — matches the old contract used in `?.any` / `?.forEach` patterns.
     */
    fun findAliases(base: AccessPathBase): Set<Access>? =
        aliasGraph.aliasesOf(base).takeIf { it.isNotEmpty() }

    /** True iff [a] and [b] resolve to the same must-alias equivalence class. */
    fun basesAliasSymmetric(a: AccessPathBase, b: AccessPathBase): Boolean =
        aliasGraph.inSameClass(a, b)

    /**
     * [Access] entries `slot + ReferenceAccessor` such that fact at [base] (a loaded value) can be
     * rebased to `slot.[ReferenceAccessor]` — preserving the mark across a call summary that
     * exposes the slot as a by-reference argument.
     */
    fun derefAliasesOf(base: AccessPathBase): List<Access> =
        aliasGraph.pointedByOf(base).map { Access(it, ReferenceAccessor) }

    /** True iff both values canonicalize to the same class. */
    fun areAliased(a: MLIRValue, b: MLIRValue): Boolean {
        val ba = aliasGraph.canonicalBase(a) ?: return false
        val bb = aliasGraph.canonicalBase(b) ?: return false
        return aliasGraph.inSameClass(ba, bb)
    }

    /** SSA base after stripping transparent casts. */
    fun canonicalAccessPathBase(value: MLIRValue): AccessPathBase? =
        aliasGraph.canonicalBase(value)

    /**
     * True iff [slotAddressBase] (or an alias) is a slot from which [loadedValueBase] was loaded,
     * possibly through a `ptr_stride` / `get_member` / cast derivation chain. Excludes the
     * must-alias case: use [basesAliasSymmetric] for that.
     */
    fun loadedFromSlot(loadedValueBase: AccessPathBase, slotAddressBase: AccessPathBase): Boolean =
        aliasGraph.classOf(slotAddressBase) in aliasGraph.pointedByOf(loadedValueBase)

    /**
     * True if [pointerFactBase] is (in the same class as) [slotAddressBase], or was derived
     * (`ptr_stride` / `get_member` / cast) from some value loaded from [slotAddressBase] or an
     * alias. Bridges loop interior pointers vs the plain `load` used before `free` on the same
     * alloca slot.
     */
    fun pointerDerivedFromSameLoadedSlotAsAddress(
        pointerFactBase: AccessPathBase,
        slotAddressBase: AccessPathBase,
    ): Boolean =
        basesAliasSymmetric(pointerFactBase, slotAddressBase) ||
            loadedFromSlot(pointerFactBase, slotAddressBase)
}
