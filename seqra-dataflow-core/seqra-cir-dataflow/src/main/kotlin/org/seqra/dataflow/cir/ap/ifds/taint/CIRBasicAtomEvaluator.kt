package org.seqra.dataflow.cir.ap.ifds.taint

import org.seqra.dataflow.configuration.core.And
import org.seqra.dataflow.configuration.core.ConditionVisitor
import org.seqra.dataflow.configuration.core.ConstantBooleanValue
import org.seqra.dataflow.configuration.core.ConstantEq
import org.seqra.dataflow.configuration.core.ConstantGt
import org.seqra.dataflow.configuration.core.ConstantIntValue
import org.seqra.dataflow.configuration.core.ConstantLt
import org.seqra.dataflow.configuration.core.ConstantMatches
import org.seqra.dataflow.configuration.core.ConstantStringValue
import org.seqra.dataflow.configuration.core.ConstantTrue
import org.seqra.dataflow.configuration.core.ConstantValue
import org.seqra.dataflow.configuration.core.ContainsMark
import org.seqra.dataflow.configuration.core.IsConstant
import org.seqra.dataflow.configuration.core.Not
import org.seqra.dataflow.configuration.core.Or
import org.seqra.dataflow.configuration.core.PositionResolver
import org.seqra.dataflow.configuration.core.TypeMatches
import org.seqra.dataflow.configuration.core.TypeMatchesPattern
import org.seqra.dataflow.cir.ap.ifds.CIRFactTypeChecker
import org.seqra.ir.api.cir.cfg.CIRBoolAttr
import org.seqra.ir.api.cir.cfg.CIRConstantOpExpr
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRIntAttr
import org.seqra.ir.api.cir.cfg.MLIRAttribute
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRIntegerAttr
import org.seqra.ir.api.cir.cfg.MLIRStringAttr
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef
import org.seqra.util.Maybe
import org.seqra.util.onSome
import java.math.BigInteger

class CIRBasicAtomEvaluator(
    private val positionResolver: PositionResolver<Maybe<MLIRValue>>,
    private val method: CIRFunction,
) : ConditionVisitor<Boolean> {

    override fun visit(condition: Not): Boolean = error("Non-atomic condition")
    override fun visit(condition: And): Boolean = error("Non-atomic condition")
    override fun visit(condition: Or): Boolean = error("Non-atomic condition")

    override fun visit(condition: ContainsMark): Boolean {
        error("This visitor does not support condition $condition. Use FactAwareConditionEvaluator instead")
    }

    override fun visit(condition: ConstantTrue): Boolean {
        return true
    }

    override fun visit(condition: IsConstant): Boolean {
        positionResolver.resolve(condition.position).onSome {
            return isConstant(it)
        }
        return false
    }

    override fun visit(condition: ConstantEq): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return eqConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantLt): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return ltConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantGt): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return gtConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantMatches): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return matches(value, condition.pattern)
        }
        return false
    }

    override fun visit(condition: TypeMatches): Boolean {
        positionResolver.resolve(condition.position).onSome { value ->
            return typeMatches(value, condition)
        }
        return false
    }

    private val typeMatchesCache = hashMapOf<TypeMatchesPattern, Boolean>()

    override fun visit(condition: TypeMatchesPattern): Boolean = typeMatchesCache.computeIfAbsent(condition) {
        positionResolver.resolve(condition.position).onSome { value ->
            return@computeIfAbsent typeMatchesPattern(value, condition)
        }
        return@computeIfAbsent false
    }

    /*
    - int x = 42; → cir.const + cir.store
    - x = x + 1; → cir.load + cir.const 1 + cir.add + cir.store

    https://llvm.github.io/clangir/Dialect/ops.html
    - ACCEPT cir.const (::cir::ConstantOp)
        Create a CIR constant from a literal attribute
    - REJECT cir.alloca (::cir::AllocaOp) with const attr
        У const int x = foo(); alloca тоже будет const, но значение foo() не станет от этого literal constant.
    - REJECT cir.global (::cir::GlobalOp) with const attr
        По тестам clang/test/CIR:
        cir.global constant реально встречается в двух разных ролях:
        1. настоящие immutable payload’ы
           - CodeGen/amdgpu-address-spaces.cpp: @constGlobal = #cir.int<456>
           - CodeGen/constant-inits.cpp: #cir.const_record<...>, #cir.const_array<...>
           - CodeGen/string-literals.cpp: string literals как #cir.const_array<...>
        2. глобальные ссылки / views, а не литералы
           - CodeGenCXX/global-refs.cpp: cir.global constant ... = #cir.global_view<@globalInt>
           - CodeGenCXX/typeid.cpp: cir.global constant ... = #cir.global_view<@_ZTIi>
        Это важный контрпример: если cir.global constant может содержать #cir.global_view<...>, то это уже не
            “literal constant value” в смысле JIRConstant.
    */
    /*
     *  TODO reuse value.opIndex
     *   for now we cannot rely on value.opIndex to make API call and take related operation
     *   because in CIR domain we can garantee exclusive on on pair (CIRFunction, opIndex)
     *   .
     *   In future, we can reuse value by
     *   1. patch in seqra-ir-core, to put related FunctionId to MLIROpID structure.
     *   2. Then we can add API call to go through CIRDatabase and return operation
     *   I think it have same complexity with current approach but its persistent
     */
    private fun isConstant(value: MLIRValue): Boolean = constantExpr(value) != null

    private fun eqConstant(value: MLIRValue, constant: ConstantValue): Boolean =
        when (constant) {
            is ConstantBooleanValue ->
                booleanConstant(value)?.let { it == constant.value } ?: false
            is ConstantIntValue ->
                integerConstant(value)?.let { it == BigInteger.valueOf(constant.value.toLong()) } ?: false
            is ConstantStringValue -> {
                when (val attr = constantAttribute(value)) {
                    // TODO: if 'value' is not string, convert it to string and compare with 'constant.value'
                    is MLIRStringAttr -> attr.value == constant.value
                    else -> false
                }
            }
        }

    private fun ltConstant(value: MLIRValue, constant: ConstantValue): Boolean {
        return when (constant) {
            is ConstantIntValue ->
                integerConstant(value)?.let { it < BigInteger.valueOf(constant.value.toLong()) } ?: false

            else -> error("Unexpected constant: $constant")
        }
    }

    private fun gtConstant(value: MLIRValue, constant: ConstantValue): Boolean {
        return when (constant) {
            is ConstantIntValue ->
                integerConstant(value)?.let { it > BigInteger.valueOf(constant.value.toLong()) } ?: false

            else -> error("Unexpected constant: $constant")
        }
    }

    private fun matches(value: MLIRValue, pattern: Regex): Boolean {
        val s = value.toString()
        return pattern.matches(s)
    }

//    TODO
    private fun typeMatches(value: MLIRValue, condition: TypeMatches): Boolean
        = error("Not implemented for $condition")

//    TODO
    private fun typeMatchesPattern(value: MLIRValue, condition: TypeMatchesPattern): Boolean
        = error("Not implemented for $condition")

    private fun constantExpr(value: MLIRValue): CIRConstantOpExpr? = when (value) {
        is MLIRValueRef -> constantExpr(value.value)
        is MLIROpValue -> method.assignInstByLhv[value]?.rhv as? CIRConstantOpExpr
        else -> null
    }

    private fun constantAttribute(value: MLIRValue): MLIRAttribute? =
        constantExpr(value)?.value

    private fun integerConstant(value: MLIRValue): BigInteger? =
        when (val attr = constantAttribute(value)) {
            is CIRIntAttr -> attr.value
            is MLIRIntegerAttr -> attr.value
            else -> null
        }

    private fun booleanConstant(value: MLIRValue): Boolean? =
        when (val attr = constantAttribute(value)) {
            is CIRIntAttr -> (attr.value != BigInteger.ZERO)
            is MLIRIntegerAttr -> (attr.value != BigInteger.ZERO)
            is CIRBoolAttr -> attr.value
            else -> false
        }
}
