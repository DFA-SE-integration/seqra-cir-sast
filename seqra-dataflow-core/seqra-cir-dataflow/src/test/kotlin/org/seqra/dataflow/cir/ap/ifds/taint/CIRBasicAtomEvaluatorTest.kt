package org.seqra.dataflow.cir.ap.ifds.taint

import org.seqra.dataflow.cir.ap.ifds.CalleePositionToCIRValueResolver
import org.seqra.dataflow.cir.ap.ifds.CIRFactTypeChecker
import org.seqra.dataflow.configuration.core.Argument
import org.seqra.dataflow.configuration.core.ConstantBooleanValue
import org.seqra.dataflow.configuration.core.ConstantEq
import org.seqra.dataflow.configuration.core.ConstantIntValue
import org.seqra.dataflow.configuration.core.ConstantStringValue
import org.seqra.dataflow.configuration.core.IsConstant
import org.seqra.dataflow.configuration.core.Position
import org.seqra.dataflow.configuration.core.PositionResolver
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRClasspathFeature
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.cir.cfg.CIRAbsOpExpr
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRBoolAttr
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRConstantOpExpr
import org.seqra.ir.api.cir.cfg.CIRFuncOp
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRFunctionParameter
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.CIRGlobalLinkageKind
import org.seqra.ir.api.cir.cfg.CIRCallingConv
import org.seqra.ir.api.cir.cfg.CIRExtraFuncAttributesAttr
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRInstLocation
import org.seqra.ir.api.cir.cfg.CIRIntAttr
import org.seqra.ir.api.cir.cfg.CIRVisibilityAttr
import org.seqra.ir.api.cir.cfg.CIRVisibilityKind
import org.seqra.ir.api.cir.cfg.MLIRBasicBlock
import org.seqra.ir.api.cir.cfg.MLIRBlockID
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.cir.cfg.MLIRDictionaryAttr
import org.seqra.ir.api.cir.cfg.MLIROpID
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRIntegerAttr
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIRStringAttr
import org.seqra.ir.api.cir.cfg.MLIRTypeAttr
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRUnknownLoc
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef
import org.seqra.ir.impl.cfg.instListOf
import org.seqra.util.Maybe
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CIRBasicAtomEvaluatorTest {

    private val moduleId = MLIRModuleID("test-mod")
    private val voidTy = MLIRTypeID(moduleId, "void")
    private val intTy = MLIRTypeID(moduleId, "i32")
    private val strTy = MLIRTypeID(moduleId, "!cir.char")
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
        functionType = MLIRTypeAttr(functionTypeValue),
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

    private inner class StubParameter(
        override val index: Int,
        override val type: MLIRTypeID,
    ) : CIRFunctionParameter {
        lateinit var holder: CIRFunction
        override val method: CIRFunction get() = holder
    }

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
        override fun flowGraph() = error("unused in atom evaluator tests")

        fun withInstructions(instructions: List<CIRInst>): StubFunction =
            StubFunction(id, classpath, parameters, returnType, blocks, funcOp, instructions)
    }

    private class FixedValueResolver(
        private val value: MLIRValue,
    ) : PositionResolver<Maybe<MLIRValue>> {
        override fun resolve(position: Position): Maybe<MLIRValue> = Maybe.some(value)
    }

    private fun evaluator(
        method: CIRFunction,
        value: MLIRValue,
        cp: CIRClasspath = StubClasspath(),
    ) = CIRBasicAtomEvaluator(
        positionResolver = FixedValueResolver(value),
        method = method,
    )

    private fun emptyFunction(cp: CIRClasspath): StubFunction = StubFunction(
        id = CIRFunctionID(moduleId, "f"),
        classpath = cp,
        parameters = emptyList(),
        returnType = voidTy,
        blocks = CIRBlockList(emptyList()),
        funcOp = stubFuncOp(voidTy),
        allInstructions = emptyList(),
    )

    @Test
    fun `ConstantEq int matches CIRIntAttr`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val constLhv = MLIROpValue(intTy, MLIROpID(0), 0L)
        val constInst = CIRAssignInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            lhv = constLhv,
            rhv = CIRConstantOpExpr(CIRIntAttr(null, BigInteger.valueOf(42)), intTy),
        )
        val fnWithInst = fn.withInstructions(listOf(constInst))
        assertTrue(
            ConstantEq(Argument(0), ConstantIntValue(42)).accept(evaluator(fnWithInst, constLhv, cp)),
        )
        assertFalse(
            ConstantEq(Argument(0), ConstantIntValue(7)).accept(evaluator(fnWithInst, constLhv, cp)),
        )
    }

    @Test
    fun `ConstantEq int matches MLIRIntegerAttr`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val constLhv = MLIROpValue(intTy, MLIROpID(1), 0L)
        val constInst = CIRAssignInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(1),
            lhv = constLhv,
            rhv = CIRConstantOpExpr(MLIRIntegerAttr(intTy, BigInteger.valueOf(99)), intTy),
        )
        val fnWithInst = fn.withInstructions(listOf(constInst))
        assertTrue(
            ConstantEq(Argument(0), ConstantIntValue(99)).accept(evaluator(fnWithInst, constLhv, cp)),
        )
    }

    @Test
    fun `ConstantEq bool matches CIRBoolAttr`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val lhv = MLIROpValue(intTy, MLIROpID(0), 0L)
        val inst = CIRAssignInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            lhv = lhv,
            rhv = CIRConstantOpExpr(CIRBoolAttr(intTy, true), intTy),
        )
        val fnWithInst = fn.withInstructions(listOf(inst))
        assertTrue(
            ConstantEq(Argument(0), ConstantBooleanValue(true)).accept(evaluator(fnWithInst, lhv, cp)),
        )
        assertFalse(
            ConstantEq(Argument(0), ConstantBooleanValue(false)).accept(evaluator(fnWithInst, lhv, cp)),
        )
    }

    @Test
    fun `ConstantEq bool from nonzero CIRIntAttr`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val lhv = MLIROpValue(intTy, MLIROpID(0), 0L)
        val inst = CIRAssignInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            lhv = lhv,
            rhv = CIRConstantOpExpr(CIRIntAttr(null, BigInteger.ONE), intTy),
        )
        val fnWithInst = fn.withInstructions(listOf(inst))
        assertTrue(
            ConstantEq(Argument(0), ConstantBooleanValue(true)).accept(evaluator(fnWithInst, lhv, cp)),
        )
        assertFalse(
            ConstantEq(Argument(0), ConstantBooleanValue(false)).accept(evaluator(fnWithInst, lhv, cp)),
        )
    }

    @Test
    fun `ConstantEq string matches MLIRStringAttr`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val lhv = MLIROpValue(strTy, MLIROpID(0), 0L)
        val inst = CIRAssignInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            lhv = lhv,
            rhv = CIRConstantOpExpr(MLIRStringAttr("hi", null), strTy),
        )
        val fnWithInst = fn.withInstructions(listOf(inst))
        assertTrue(
            ConstantEq(Argument(0), ConstantStringValue("hi")).accept(evaluator(fnWithInst, lhv, cp)),
        )
        assertFalse(
            ConstantEq(Argument(0), ConstantStringValue("no")).accept(evaluator(fnWithInst, lhv, cp)),
        )
    }

    @Test
    fun `IsConstant true for CIRConstantOpExpr rhs`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val lhv = MLIROpValue(intTy, MLIROpID(0), 0L)
        val inst = CIRAssignInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            lhv = lhv,
            rhv = CIRConstantOpExpr(CIRIntAttr(null, BigInteger.TEN), intTy),
        )
        val fnWithInst = fn.withInstructions(listOf(inst))
        assertTrue(IsConstant(Argument(0)).accept(evaluator(fnWithInst, lhv, cp)))
    }

    @Test
    fun `IsConstant false for non constant rhs`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val dummyArg = MLIRBlockValue(intTy, MLIRBlockID(0), 0L)
        val lhv = MLIROpValue(intTy, MLIROpID(0), 0L)
        val inst = CIRAssignInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            lhv = lhv,
            rhv = CIRAbsOpExpr(dummyArg, poison = null, result = intTy),
        )
        val fnWithInst = fn.withInstructions(listOf(inst))
        assertFalse(IsConstant(Argument(0)).accept(evaluator(fnWithInst, lhv, cp)))
    }

    @Test
    fun `IsConstant false for orphan MLIROpValue`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val orphan = MLIROpValue(intTy, MLIROpID(99), 0L)
        assertFalse(IsConstant(Argument(0)).accept(evaluator(fn, orphan, cp)))
        assertFalse(
            ConstantEq(Argument(0), ConstantIntValue(1)).accept(evaluator(fn, orphan, cp)),
        )
    }

    @Test
    fun `MLIRValueRef unwrap`() {
        val cp = StubClasspath()
        val fn = emptyFunction(cp)
        val lhv = MLIROpValue(intTy, MLIROpID(0), 0L)
        val inst = CIRAssignInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            lhv = lhv,
            rhv = CIRConstantOpExpr(CIRIntAttr(null, BigInteger.valueOf(3)), intTy),
        )
        val fnWithInst = fn.withInstructions(listOf(inst))
        val wrapped = MLIRValueRef(lhv)
        assertTrue(
            ConstantEq(Argument(0), ConstantIntValue(3)).accept(evaluator(fnWithInst, wrapped, cp)),
        )
    }

    @Test
    fun `CalleePositionToCIRValueResolver block argument is not constant literal`() {
        val cp = StubClasspath()
        val entryBlock = MLIRBasicBlock(
            MLIRBlockID(0),
            instListOf<CIRInst>(),
            arguments = listOf(intTy),
        )
        val p0 = StubParameter(0, intTy)
        val fn = StubFunction(
            id = CIRFunctionID(moduleId, "g"),
            classpath = cp,
            parameters = listOf(p0),
            returnType = voidTy,
            blocks = CIRBlockList(listOf(entryBlock)),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        p0.holder = fn
        val expectedArg = MLIRBlockValue(intTy, entryBlock.id, 0L)
        assertEquals(expectedArg, CalleePositionToCIRValueResolver(fn).resolve(Argument(0)).getOrThrow())
        val ev = CIRBasicAtomEvaluator(
            positionResolver = CalleePositionToCIRValueResolver(fn),
            method = fn,
        )
        assertFalse(IsConstant(Argument(0)).accept(ev))
        assertFalse(ConstantEq(Argument(0), ConstantIntValue(0)).accept(ev))
    }
}
