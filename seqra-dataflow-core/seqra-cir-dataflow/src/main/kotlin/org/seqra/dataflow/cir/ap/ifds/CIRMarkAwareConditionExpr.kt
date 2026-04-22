package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.configuration.core.ContainsMark
import org.seqra.dataflow.cir.ap.ifds.CIRMarkAwareConditionExpr.And
import org.seqra.dataflow.cir.ap.ifds.CIRMarkAwareConditionExpr.Literal
import org.seqra.dataflow.cir.ap.ifds.CIRMarkAwareConditionExpr.Or
import org.seqra.dataflow.util.cartesianProductMapTo

// 1:1 from JIRMarkAwareConditionExpr
// Formula over mark predicates
// То есть у нас есть отдельный FactAwareConditionEvaluator, BasicAtomEvaluator. Второй описывает
//    выражение в пропозициональной логике, первый видимо добавляет в символы языка еще метку Taint.
sealed interface CIRMarkAwareConditionExpr {
    class And(val args: Array<CIRMarkAwareConditionExpr>) : CIRMarkAwareConditionExpr {
        override fun toString(): String = "And(${args.contentToString()})"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is And) return false
            return args.contentEquals(other.args)
        }

        override fun hashCode(): Int = args.contentHashCode()
    }

    class Or(val args: Array<CIRMarkAwareConditionExpr>) : CIRMarkAwareConditionExpr {
        override fun toString(): String = "Or(${args.contentToString()})"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Or) return false
            return args.contentEquals(other.args)
        }

        override fun hashCode(): Int = args.contentHashCode()
    }

    data class Literal(val condition: ContainsMark, val negated: Boolean) : CIRMarkAwareConditionExpr
}

fun CIRMarkAwareConditionExpr.removeTrueLiterals(
    evalLiteral: (Literal) -> Boolean
): CIRMarkAwareConditionExpr? = removeTrueLiteralsFromExpr(this, evalLiteral)

private fun removeTrueLiteralsFromExpr(
    expr: CIRMarkAwareConditionExpr,
    evalLiteral: (Literal) -> Boolean
) = when (expr) {
    is Literal -> if (evalLiteral(expr)) null else expr
    is And -> removeTrueLiteralsFromAndExpr(expr, evalLiteral)
    is Or -> removeTrueLiteralsFromOrExpr(expr, evalLiteral)
}

private fun removeTrueLiteralsFromAndExpr(expr: And, evalLiteral: (Literal) -> Boolean): CIRMarkAwareConditionExpr? {
    return removeTrueLiteralsFromArray(
        expr.args, { removeTrueLiteralsFromExpr(it, evalLiteral) }, CIRMarkAwareConditionExpr::And,
        createDefault = { return null },
        processElement = { it }
    )
}

private fun removeTrueLiteralsFromOrExpr(expr: Or, evalLiteral: (Literal) -> Boolean): CIRMarkAwareConditionExpr? {
    return removeTrueLiteralsFromArray(
        expr.args, { removeTrueLiteralsFromExpr(it, evalLiteral) }, CIRMarkAwareConditionExpr::Or,
        createDefault = { error("impossible") },
        processElement = { it ?: return null }
    )
}

private inline fun removeTrueLiteralsFromArray(
    elements: Array<CIRMarkAwareConditionExpr>,
    removeTrueLiteralsFromExpr: (CIRMarkAwareConditionExpr) -> CIRMarkAwareConditionExpr?,
    create: (Array<CIRMarkAwareConditionExpr>) -> CIRMarkAwareConditionExpr,
    createDefault: () -> CIRMarkAwareConditionExpr?,
    processElement: (CIRMarkAwareConditionExpr?) -> CIRMarkAwareConditionExpr?,
): CIRMarkAwareConditionExpr? {
    val result = arrayOfNulls<CIRMarkAwareConditionExpr>(elements.size)
    var size = 0
    for (i in elements.indices) {
        val elementResult = removeTrueLiteralsFromExpr(elements[i])
        val elementExpr = processElement(elementResult) ?: continue
        result[size++] = elementExpr
    }

    if (size == 0) {
        return createDefault()
    }

    if (size == 1) {
        return result[0]
    }

    val resultExprs = result.copyOf(size)

    @Suppress("UNCHECKED_CAST")
    resultExprs as Array<CIRMarkAwareConditionExpr>

    return create(resultExprs)
}

data class CIRMarkAwareCube(val literals: Set<Literal>)

fun CIRMarkAwareConditionExpr.explodeToDNF(): List<CIRMarkAwareCube> = when (this) {
    is Literal -> listOf(CIRMarkAwareCube(setOf(this)))
    is Or -> args.flatMap { it.explodeToDNF() }
    is And -> {
        val result = mutableListOf<CIRMarkAwareCube>()
        val cubeLists = args.map { it.explodeToDNF() }
        cubeLists.cartesianProductMapTo { cubes ->
            val literals = hashSetOf<Literal>()
            cubes.flatMapTo(literals) { it.literals }
            result += CIRMarkAwareCube(literals)
        }
        result
    }
}
