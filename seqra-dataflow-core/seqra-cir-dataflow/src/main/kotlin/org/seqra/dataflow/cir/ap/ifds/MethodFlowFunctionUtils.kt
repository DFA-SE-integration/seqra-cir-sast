package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.FieldAccessor
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef

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
}
