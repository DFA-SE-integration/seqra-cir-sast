package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.configuration.core.And
import org.seqra.dataflow.configuration.core.Argument
import org.seqra.dataflow.configuration.core.ConstantEq
import org.seqra.dataflow.configuration.core.ConstantIntValue
import org.seqra.dataflow.configuration.core.ContainsMark
import org.seqra.dataflow.configuration.core.Not
import org.seqra.dataflow.configuration.core.TaintMark
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRClasspathFeature
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRCallingConv
import org.seqra.ir.api.cir.cfg.CIRConstantOpExpr
import org.seqra.ir.api.cir.cfg.CIRExtraFuncAttributesAttr
import org.seqra.ir.api.cir.cfg.CIRFuncOp
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRFunctionParameter
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.CIRGlobalLinkageKind
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRInstLocation
import org.seqra.ir.api.cir.cfg.CIRIntAttr
import org.seqra.ir.api.cir.cfg.CIRVisibilityAttr
import org.seqra.ir.api.cir.cfg.CIRVisibilityKind
import org.seqra.ir.api.cir.cfg.MLIRDictionaryAttr
import org.seqra.ir.api.cir.cfg.MLIROpID
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIRStringAttr
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRUnknownLoc
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.util.Maybe
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CIRMarkAwareConditionRewriterTest {

    private val moduleId = MLIRModuleID("test-mod")
    private val voidTy = MLIRTypeID(moduleId, "void")
    private val intTy = MLIRTypeID(moduleId, "i32")
    private val extraAttrs = CIRExtraFuncAttributesAttr(MLIRDictionaryAttr(arrayListOf()))

    private inner class StubClasspath : CIRClasspath {
        override val db: CIRDatabase get() = error("unused")
        override val registeredLocations: List<RegisteredLocation> = emptyList()
        override val registeredLocationIds: Set<Long> = emptySet()
        override val features: List<CIRClasspathFeature> = emptyList()
        override val moduleNames: List<String> = listOf(moduleId.id)
        override fun findFunctionOrNull(functionID: CIRFunctionID): CIRFunction? = null
        override fun findFunctionBySymbolName(symbolName: String): CIRFunction? = null
        override fun findTypeOrNull(typeID: MLIRTypeID) = null
        override fun findGlobalOrNull(globalID: CIRGlobalID) = null
        override fun getGlobalConstructors(): List<CIRFunctionID> = emptyList()
        override fun getGlobalDestructors(): List<CIRFunctionID> = emptyList()
        override fun close() = Unit
    }

    private fun stubFuncOp(functionTypeValue: MLIRTypeID): CIRFuncOp = CIRFuncOp(
        symName = MLIRStringAttr("stub", null),
        globalVisibility = CIRVisibilityAttr(CIRVisibilityKind.Default),
        functionType = org.seqra.ir.api.cir.cfg.MLIRTypeAttr(functionTypeValue),
        builtin = null,
        coroutine = null,
        lambda = null,
        noProto = null,
        dsolocal = null,
        linkage = CIRGlobalLinkageKind.ExternalLinkage,
        callingConv = CIRCallingConv.C,
        extraAttrs = extraAttrs,
        symVisibility = null,
        comdat = null,
        argAttrs = null,
        resAttrs = null,
        aliasee = null,
        globalCtor = null,
        globalDtor = null,
        annotations = null,
        ast = null,
    )

    private inner class StubFunction(
        override val id: CIRFunctionID,
        override val classpath: CIRClasspath,
        override val parameters: List<CIRFunctionParameter>,
        override val returnType: MLIRTypeID,
        override val blocks: CIRBlockList,
        private val funcOp: CIRFuncOp,
        override val allInstructions: List<CIRInst>,
    ) : CIRFunction {
        override val info: CIRFuncOp get() = funcOp
        override val assignInstByLhv: Map<MLIRValue, CIRAssignInst>
            get() = allInstructions.filterIsInstance<CIRAssignInst>().associateBy { it.lhv }

        override fun <T> withIRNode(body: (ByteArray?) -> T): T = body(null)
        override fun flowGraph() = error("unused")

        fun withInstructions(instructions: List<CIRInst>): StubFunction =
            StubFunction(id, classpath, parameters, returnType, blocks, funcOp, instructions)
    }

    private class FixedValueResolver(
        private val value: MLIRValue,
    ) : org.seqra.dataflow.configuration.core.PositionResolver<Maybe<MLIRValue>> {
        override fun resolve(position: org.seqra.dataflow.configuration.core.Position): Maybe<MLIRValue> = Maybe.some(value)
    }

    private fun emptyFunction(cp: CIRClasspath): StubFunction = StubFunction(
        id = CIRFunctionID(moduleId, "f"),
        classpath = cp,
        parameters = emptyList(),
        returnType = voidTy,
        blocks = CIRBlockList(emptyList()),
        funcOp = stubFuncOp(voidTy),
        allInstructions = emptyList(),
    )

    private fun rewriter(method: CIRFunction, value: MLIRValue, cp: CIRClasspath = StubClasspath()) =
        CIRMarkAwareConditionRewriter(
            positionResolver = FixedValueResolver(value),
            method = method,
        )

    @Test
    fun `rewrite folds evaluated atoms and preserves mark literals`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val lhv = MLIROpValue(intTy, MLIROpID(0), 0L)
        val inst = CIRAssignInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            lhv = lhv,
            rhv = CIRConstantOpExpr(CIRIntAttr(null, BigInteger.valueOf(42)), intTy),
        )
        val fnWithInst = fn.withInstructions(listOf(inst))
        val markCondition = ContainsMark(Argument(0), TaintMark("source"))

        val result = rewriter(fnWithInst, lhv, cp).rewrite(
            And(
                listOf(
                    ConstantEq(Argument(0), ConstantIntValue(42)),
                    markCondition,
                )
            )
        )

        assertFalse(result.isTrue)
        assertFalse(result.isFalse)
        assertEquals(CIRMarkAwareConditionExpr.Literal(markCondition, negated = false), result.expr)
    }

    @Test
    fun `rewrite negates contains mark literals under not`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val lhv = MLIROpValue(intTy, MLIROpID(1), 0L)
        val markCondition = ContainsMark(Argument(0), TaintMark("source"))

        val result = rewriter(fn, lhv, cp).rewrite(Not(markCondition))

        assertFalse(result.isTrue)
        assertFalse(result.isFalse)
        assertEquals(CIRMarkAwareConditionExpr.Literal(markCondition, negated = true), result.expr)
    }

    @Test
    fun `rewrite short circuits and to false when one atom is false`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val lhv = MLIROpValue(intTy, MLIROpID(2), 0L)
        val inst = CIRAssignInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(2),
            lhv = lhv,
            rhv = CIRConstantOpExpr(CIRIntAttr(null, BigInteger.valueOf(42)), intTy),
        )
        val fnWithInst = fn.withInstructions(listOf(inst))

        val result = rewriter(fnWithInst, lhv, cp).rewrite(
            And(
                listOf(
                    ConstantEq(Argument(0), ConstantIntValue(7)),
                    ContainsMark(Argument(0), TaintMark("source")),
                )
            )
        )

        assertTrue(result.isFalse)
    }
}
