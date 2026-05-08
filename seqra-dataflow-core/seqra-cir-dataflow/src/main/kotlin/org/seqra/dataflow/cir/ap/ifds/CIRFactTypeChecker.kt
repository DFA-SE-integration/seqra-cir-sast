package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.AnyAccessor
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.FactTypeChecker
import org.seqra.dataflow.ap.ifds.FactTypeChecker.AlwaysRejectFilter
import org.seqra.dataflow.ap.ifds.FactTypeChecker.FactApFilter
import org.seqra.dataflow.ap.ifds.FactTypeChecker.FilterResult
import org.seqra.dataflow.ap.ifds.FieldAccessor
import org.seqra.dataflow.ap.ifds.FinalAccessor
import org.seqra.dataflow.ap.ifds.ReferenceAccessor
import org.seqra.dataflow.ap.ifds.TaintMarkAccessor
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.cfg.CIRArrayType
import org.seqra.ir.api.cir.cfg.CIRDirectCall
import org.seqra.ir.api.cir.cfg.CIRPointerType
import org.seqra.ir.api.cir.cfg.CIRStructType
import org.seqra.ir.api.cir.cfg.MLIRType
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRVectorType
import org.seqra.ir.api.common.CommonType
import java.util.concurrent.atomic.LongAdder

class CIRFactTypeChecker(private val cp: CIRClasspath) : FactTypeChecker {
    val localFactsTotal = LongAdder()
    val localFactsRejected = LongAdder()

    private fun Boolean.logLocalFactCheck(): Boolean = also { isCorrect ->
        localFactsTotal.increment()
        if (!isCorrect) localFactsRejected.increment()
    }

    val accessTotal = LongAdder()
    val accessRejected = LongAdder()

    private fun Boolean.logAccessCheck(): Boolean = also { isCorrect ->
        accessTotal.increment()
        if (!isCorrect) accessRejected.increment()
    }

//    After fact transition should check that fact is applicable to this type of value
//    e.g. Int s = source();
//      sink(s[0]); // типологически бессмысленно
    override fun filterFactByLocalType(actualType: CommonType?, factAp: FinalFactAp): FinalFactAp? {
        if (actualType == null) return factAp
        cirDowncast<MLIRType>(actualType)

//    - isLocalCheck = true — фильтр используется в filterFactByLocalType(...)
//    Это сценарий “проверяем весь taint fact относительно локального реального типа базы”.
//    - isLocalCheck = false — фильтр используется в accessPathFilter(...)
//    Это сценарий “проверяем access path как путь доступа”.
        val filter = AccessorFilter(actualType, isLocalCheck = true)
        return factAp.filterFact(filter)
    }

    override fun accessPathFilter(accessPath: List<Accessor>): FactApFilter {
        val actualType = accessorActualType(accessPath) ?: return AlwaysRejectFilter
        return AccessorFilter(actualType, isLocalCheck = false)
    }

    private fun fieldAccessorType(accessor: FieldAccessor): MLIRType? {
        return resolveFieldTypeIdOrNull(accessor.fieldType)?.let { cp.findTypeOrNull(it) }
    }

    private fun accessorActualType(accessPath: List<Accessor>): MLIRType? {
        val accessor = accessPath.lastOrNull() ?: return null
        return when (accessor) {
            is FieldAccessor -> fieldAccessorType(accessor)
            ElementAccessor -> {
                val prevAccessors = accessPath.subList(0, accessPath.size - 1)
                accessorActualType(prevAccessors)?.elementAccessorType()
            }

            is TaintMarkAccessor, FinalAccessor, AnyAccessor, ReferenceAccessor -> null
        }
    }

    /**
     * cir.get_element (::cir::GetElementOp)
     * operation ::= `cir.get_element` $base `[` $index `]` `:` `(` qualified(type($base)) `,` qualified(type($index)) `)`
     *               `->` qualified(type($result)) attr-dict
     * Operand	Description
     * base	    pointer to array type
    */
    private fun MLIRType.elementAccessorType(): MLIRType? = when (this) {
//        is CIRArrayType -> cp.findTypeOrNull(eltType)
        is CIRPointerType -> (cp.findTypeOrNull(pointee) as? CIRArrayType)?.let { cp.findTypeOrNull(it.eltType) }
        else -> null
    }

    /**
     * cir.get_member (::cir::GetMemberOp)
     * operation ::= `cir.get_member` $addr `[` $index `]` attr-dict
     *               `:` qualified(type($addr)) `->` qualified(type($result))
     * Operand	Description
     * addr	    CIR pointer type
     *
     * cir.get_runtime_member (::cir::GetRuntimeMemberOp)
     * operation ::= `cir.get_runtime_member` $addr `[` $member `:` qualified(type($member)) `]` attr-dict
     *               `:` qualified(type($addr)) `->` qualified(type($result))
     * Operand	Description
     * addr	    pointer to record type
     * member	CIR type that represents pointer-to-data-member type in C++
     */
    private fun resolveStructTypeOrNull(actualType: MLIRType): CIRStructType? = when (actualType) {
//        is CIRStructType -> actualType
        is CIRPointerType -> cp.findTypeOrNull(actualType.pointee) as? CIRStructType
        else -> null
    }

    private fun resolveFieldTypeIdOrNull(fieldType: String): MLIRTypeID? =
        CIRFieldTypeEncoding.decodeOrNull(fieldType)

    private inner class AccessorFilter(
        private val actualType: MLIRType,
        private val isLocalCheck: Boolean
    ) : FactApFilter {
        override fun check(accessor: Accessor): FilterResult = checkAccessor(accessor).also {
            val result = it !== FilterResult.Reject
            if (isLocalCheck) result.logLocalFactCheck() else result.logAccessCheck()
        }

        private fun checkAccessor(accessor: Accessor): FilterResult {
            when (accessor) {
                is TaintMarkAccessor, FinalAccessor, AnyAccessor, ReferenceAccessor -> return FilterResult.Accept
                is FieldAccessor -> {
//                    https://llvm.github.io/clangir/Dialect/ops.html
//                    cir.get_member (::cir::GetMemberOp)
//                      “gets the address of a particular named member from the input pointer to the base record”
//                      (!cir.ptr<!record_ty>) -> !cir.ptr<!member_ty>
//                    cir.get_runtime_member (::cir::GetRuntimeMemberOp)
//                      (!cir.ptr<!record_ty>) -> !cir.ptr<!member_ty>
                    val structType = resolveStructTypeOrNull(actualType) ?: return FilterResult.Reject
                    val fieldTypeId = resolveFieldTypeIdOrNull(accessor.fieldType) ?: return FilterResult.Reject
                    if (fieldTypeId !in structType.members) return FilterResult.Reject

                    val fieldType = cp.findTypeOrNull(fieldTypeId) ?: return FilterResult.Reject
                    return FilterResult.FilterNext(AccessorFilter(fieldType, isLocalCheck))
                }

                ElementAccessor -> {
//                    https://llvm.github.io/clangir/Dialect/ops.html
//                    cir.get_element (::cir::GetElementOp)
//                      “gets the address of a particular element from the base **array**”
//                      `!cir.ptr<!cir.array<elementType x size>> -> !cir.ptr<!s32i>`
                    val elementType = actualType.elementAccessorType() ?: return FilterResult.Reject
                    return FilterResult.FilterNext(AccessorFilter(elementType, isLocalCheck))
                }
            }
        }
    }

    fun MLIRType.mayBeArray(): Boolean = when (this) {
        is CIRArrayType -> true
        is MLIRVectorType -> true
        else -> false
    }

    fun callArgumentMayBeArray(call: CIRDirectCall, arg: AccessPathBase.Argument): Boolean {
        val argument = call.arg_ops.getOrNull(arg.idx) ?: return false
        val argType = call.method.classpath.findTypeOrNull(argument.type) ?: return false
        return argType.mayBeArray()
    }
}
