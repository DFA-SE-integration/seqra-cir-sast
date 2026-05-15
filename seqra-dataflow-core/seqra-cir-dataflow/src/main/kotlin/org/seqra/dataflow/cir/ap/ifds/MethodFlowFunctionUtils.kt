package org.seqra.dataflow.cir.ap.ifds

import java.math.BigInteger
import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.FieldAccessor
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRConstantOpExpr
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRLoadOpInst
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.cir.cfg.MLIRIntegerAttr
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef
import org.seqra.ir.api.cir.cfg.CIRPtrStrideOpExpr
import org.seqra.ir.api.cir.cfg.CIRReturnOpInst
import org.seqra.ir.api.cir.cfg.CIRStoreOpInst

object MethodFlowFunctionUtils {
    data class Access(val base: AccessPathBase?, val accessor: Accessor?)

    fun mkBaseAccess(base: MLIRValue): Access = Access(accessPathBase(base), null)
    fun mkArrayAccess(base: MLIRValue): Access = Access(accessPathBase(base), ElementAccessor)
    fun mkFieldAccess(base: MLIRValue, fieldName: String, fieldType: MLIRTypeID) = run {
        val accessor =
            FieldAccessor(
                "",
                fieldName,
                CIRFieldTypeEncoding.encode(fieldType)
            )

        Access(accessPathBase(base), accessor)
    }

    fun InitialFactAp.excludeField(field: Accessor) = exclude(field)

    fun FinalFactAp.excludeField(field: Accessor) = exclude(field)

    fun FinalFactAp.mayReadField(base: AccessPathBase?, field: Accessor): Boolean = when {
        this.base != base -> false
        startsWithAccessor(field) -> true
        else -> isAbstract() && field !in exclusions
    }

    fun FinalFactAp.mayRemoveAfterWrite(base: AccessPathBase?, field: Accessor): Boolean = when {
        this.base != base -> false
        startsWithAccessor(field) -> true
        else -> isAbstract() && field !in exclusions
    }

    fun FinalFactAp.readFieldTo(newBase: AccessPathBase, field: Accessor): FinalFactAp =
        readAccessor(field)?.rebase(newBase) ?: error("Can't drop field")

    fun FinalFactAp.writeToField(newBase: AccessPathBase, field: Accessor): FinalFactAp =
        prependAccessor(field).rebase(newBase)

    fun FinalFactAp.clearField(field: Accessor): FinalFactAp? = clearAccessor(field)

    fun accessPathBase(value: MLIRValue): AccessPathBase? =
        when (value) {
            is MLIRValueRef -> accessPathBase(value.value)
            is MLIRBlockValue -> AccessPathBase.Argument(value.argIndex.toInt())
            is MLIROpValue -> AccessPathBase.LocalVar(value.opIndex.id.toInt())
            else -> null
        }

    /**
     * If [value] is the SSA result of `lhv = cir.ptr_stride(base, stride)` in [function] with a
     * constant integer zero [stride], returns [accessPathBase] of [CIRPtrStrideOpExpr.base];
     * otherwise null. Used so marks on the stride base reach calls that pass the strided SSA.
     */
    fun zeroStridePtrStrideRhsBase(function: CIRFunction, value: MLIRValue): AccessPathBase? {
        val op = value as? MLIROpValue ?: return null
        val assign =
            function.allInstructions.asSequence()
                .filterIsInstance<CIRAssignInst>()
                .firstOrNull { (it.lhv as? MLIROpValue)?.opIndex?.id == op.opIndex.id }
                ?: return null
        val rhv = assign.rhv as? CIRPtrStrideOpExpr ?: return null
        if (!mlirValueIsConstantZeroInteger(function, rhv.stride)) return null
        return accessPathBase(rhv.base)
    }

    private fun mlirValueIsConstantZeroInteger(function: CIRFunction, value: MLIRValue): Boolean =
        when (value) {
            is MLIROpValue -> {
                val a =
                    function.allInstructions.asSequence()
                        .filterIsInstance<CIRAssignInst>()
                        .firstOrNull { (it.lhv as? MLIROpValue)?.opIndex?.id == value.opIndex.id }
                (a?.rhv as? CIRConstantOpExpr)?.value?.let { it as? MLIRIntegerAttr }?.value == BigInteger.ZERO
            }
            is MLIRValueRef -> mlirValueIsConstantZeroInteger(function, value.value)
            else -> false
        }

    private fun unwrapToMlirOpValue(value: MLIRValue): MLIROpValue? =
        when (value) {
            is MLIROpValue -> value
            is MLIRValueRef -> unwrapToMlirOpValue(value.value)
            else -> null
        }

    /**
     * Juliet / ClangIR often lowers `return expr` through a hidden `__retval` alloca:
     * `store %v, %__retval` then `%r = load %__retval` then `return %r`.
     * IFDS facts may still be keyed on `%v` while [accessPathBase] of the return operand is `%r`.
     * When the return value is produced by a single load, return the RHS of the **closest preceding**
     * store to that load's address so return-flow can match the freed pointer SSA name.
     *
     * With [org.seqra.ir.impl.features.CIRLoadStoreFeature], loads/stores become [CIRAssignInst]
     * (`%r = ref(addr)` for load; `ref(addr) = %v` for store), so we must resolve both shapes.
     */
    fun returnOperandLoadNearestStoreSource(
        function: CIRFunction,
        retInst: CIRReturnOpInst,
    ): MLIRValue? {
        val flat = function.allInstructions
        val retIdx =
            flat.indexOfFirst { it === retInst || (it is CIRReturnOpInst && it.id.id == retInst.id.id) }
        if (retIdx > 0) {
            val prev = flat[retIdx - 1] as? CIRAssignInst
            val rhvRef = prev?.rhv as? MLIRValueRef
            if (rhvRef != null && prev.lhv is MLIROpValue) {
                // ClangIR `__retval`: the instruction immediately before `cir.return` is the lowered
                // `load` from the hidden slot. Using layout here avoids relying on SSA `opIndex`
                // identity between the return operand and the load assign (protobuf / result # drift).
                nearestStoreValueBeforeIndex(flat, retIdx, rhvRef.value)?.let { return it }
            }
        }

        val retInput = retInst.input.singleOrNull() ?: return null
        val retOp = unwrapToMlirOpValue(retInput) ?: return null

        val loadAsAssign =
            flat
                .filterIsInstance<CIRAssignInst>()
                .firstOrNull { a ->
                    val lhv = a.lhv as? MLIROpValue
                    lhv != null && lhv.opIndex.id == retOp.opIndex.id && a.rhv is MLIRValueRef
                }
        if (loadAsAssign != null) {
            val loadAddr = (loadAsAssign.rhv as MLIRValueRef).value
            val loadPos = flat.indexOf(loadAsAssign)
            if (loadPos >= 0) {
                nearestStoreValueBeforeIndex(flat, loadPos, loadAddr)?.let { return it }
            }
        }

        val load =
            flat
                .filterIsInstance<CIRLoadOpInst>()
                .firstOrNull { it.id.id == retOp.opIndex.id }
                ?: return null
        val loadPos = flat.indexOf(load)
        return if (loadPos >= 0) {
            nearestStoreValueBeforeIndex(flat, loadPos, load.addr)
        } else {
            null
        }
    }

    /**
     * Walks backwards in flattened instruction order and returns the first value stored to
     * [loadAddr] before the load at [loadExclusiveIndex]. This matches the usual `store …;
     * load …; return` lowering more reliably than taking the global max of [CIRInst.location.index]
     * (fused locations can collide; unrelated blocks may store to the same slot earlier in the list).
     */
    private fun nearestStoreValueBeforeIndex(
        flat: List<CIRInst>,
        loadExclusiveIndex: Int,
        loadAddr: MLIRValue,
    ): MLIRValue? {
        for (i in loadExclusiveIndex - 1 downTo 0) {
            when (val inst = flat[i]) {
                is CIRAssignInst -> {
                    val ref = inst.lhv as? MLIRValueRef ?: continue
                    if (sameMemoryAddress(ref.value, loadAddr)) {
                        return inst.rhv as? MLIRValue
                    }
                }
                is CIRStoreOpInst -> {
                    if (sameMemoryAddress(inst.addr, loadAddr)) {
                        return inst.value
                    }
                }
            }
        }
        return null
    }

    private fun sameMemoryAddress(a: MLIRValue, b: MLIRValue): Boolean =
        normalizeValueRef(a) == normalizeValueRef(b)

    private fun normalizeValueRef(v: MLIRValue): MLIRValue =
        when (v) {
            is MLIRValueRef -> normalizeValueRef(v.value)
            else -> v
        }
}
