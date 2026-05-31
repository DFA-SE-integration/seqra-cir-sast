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
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.cir.cfg.MLIRIntegerAttr
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef
import org.seqra.ir.api.cir.cfg.CIRPtrStrideOpExpr

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

}
