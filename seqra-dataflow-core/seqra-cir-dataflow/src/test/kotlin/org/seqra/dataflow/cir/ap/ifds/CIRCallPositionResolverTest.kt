package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.EmptyMethodContext
import org.seqra.dataflow.ap.ifds.ExclusionSet
import org.seqra.dataflow.ap.ifds.FactTypeChecker
import org.seqra.dataflow.ap.ifds.MethodEntryPoint
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.seqra.dataflow.ap.ifds.access.ApManager
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.seqra.dataflow.ap.ifds.taint.TaintAnalysisUnitStorage
import org.seqra.dataflow.ap.ifds.taint.TaintSinkTracker
import org.seqra.dataflow.configuration.core.Argument
import org.seqra.dataflow.configuration.core.ClassStatic
import org.seqra.dataflow.configuration.core.PositionAccessor
import org.seqra.dataflow.configuration.core.PositionWithAccess
import org.seqra.dataflow.configuration.core.Result
import org.seqra.dataflow.configuration.core.TaintCleaner
import org.seqra.dataflow.configuration.core.TaintEntryPointSource
import org.seqra.dataflow.configuration.core.TaintMethodEntrySink
import org.seqra.dataflow.configuration.core.TaintMethodSink
import org.seqra.dataflow.configuration.core.TaintPassThrough
import org.seqra.dataflow.configuration.core.This
import org.seqra.cir.graph.CApplicationGraph
import org.seqra.dataflow.cir.ap.ifds.CIRFactTypeChecker
import org.seqra.dataflow.cir.ap.ifds.CIRLanguageManager
import org.seqra.dataflow.cir.ap.ifds.CIRLocalVariableReachability
import org.seqra.dataflow.cir.ap.ifds.analysis.CIRMethodAnalysisContext
import org.seqra.dataflow.cir.ap.ifds.analysis.CIRMethodCallFlowFunction
import org.seqra.dataflow.cir.ap.ifds.analysis.CIRMethodStartFlowFunction
import org.seqra.dataflow.cir.ap.ifds.taint.CIRTaintRulesProvider
import org.seqra.dataflow.configuration.core.TaintMethodSource
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRClasspathFeature
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.cir.cfg.CIRCalleeRef
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRConstantOpExpr
import org.seqra.ir.api.cir.cfg.CIRFuncOp
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRFunctionParameter
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.CIRGraph
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRInstLocation
import org.seqra.ir.api.cir.cfg.CIRGlobalLinkageKind
import org.seqra.ir.api.cir.cfg.CIRMethodType
import org.seqra.ir.api.cir.cfg.CIRReturnOpInst
import org.seqra.ir.api.cir.cfg.CIRSingleType
import org.seqra.ir.api.cir.cfg.CIRVisibilityAttr
import org.seqra.ir.api.cir.cfg.CIRVisibilityKind
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRCallingConv
import org.seqra.ir.api.cir.cfg.CIRExtraFuncAttributesAttr
import org.seqra.ir.api.cir.cfg.CIRPtrStrideOpExpr
import org.seqra.ir.api.cir.cfg.MLIRDictionaryAttr
import org.seqra.ir.api.cir.cfg.MLIRFlatSymbolRefAttr
import org.seqra.ir.api.cir.cfg.MLIRIntegerAttr
import org.seqra.ir.api.cir.cfg.MLIROpID
import org.seqra.ir.api.cir.cfg.MLIRStringAttr
import org.seqra.ir.api.cir.cfg.MLIRType
import org.seqra.ir.api.cir.cfg.MLIRTypeAttr
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIRUnknownLoc
import org.seqra.ir.api.cir.cfg.MLIRBasicBlock
import org.seqra.ir.api.cir.cfg.MLIRBlockID
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.cfg.CommonInst
import org.seqra.ir.impl.cfg.instListOf
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class CIRCallPositionResolverTest {

    private val moduleId = MLIRModuleID("test-mod")
    private val voidTy = MLIRTypeID(moduleId, "void")
    private val intTy = MLIRTypeID(moduleId, "i32")
    private val floatTy = MLIRTypeID(moduleId, "f64")
    private val extraAttrs = CIRExtraFuncAttributesAttr(MLIRDictionaryAttr(arrayListOf()))

    private inner class StubClasspath(
        private val types: MutableMap<MLIRTypeID, MLIRType> = linkedMapOf(),
        private val functions: MutableMap<String, CIRFunction> = linkedMapOf(),
    ) : CIRClasspath {
        override val db: CIRDatabase get() = error("unused")
        override val registeredLocations: List<RegisteredLocation> = emptyList()
        override val registeredLocationIds: Set<Long> = emptySet()
        override val features: List<CIRClasspathFeature> = emptyList()
        override val moduleNames: List<String> = listOf(moduleId.id)
        override fun findFunctionOrNull(functionID: CIRFunctionID): CIRFunction? = functions[functionID.id]
        override fun findFunctionBySymbolName(symbolName: String): CIRFunction? = functions[symbolName]
        override fun findTypeOrNull(typeID: MLIRTypeID): MLIRType? = types[typeID]
        override fun findGlobalOrNull(globalID: CIRGlobalID) = null
        override fun getGlobalConstructors(): List<CIRFunctionID> = emptyList()
        override fun getGlobalDestructors(): List<CIRFunctionID> = emptyList()
        override fun close() = Unit

        fun register(t: MLIRTypeID, ty: MLIRType) {
            types[t] = ty
        }

        fun register(function: CIRFunction) {
            functions[function.id.id] = function
        }
    }

    private inner class StubApplicationGraph(
        override val cp: CIRClasspath,
    ) : CApplicationGraph {
        override fun predecessors(node: CIRInst) = emptySequence<CIRInst>()
        override fun successors(node: CIRInst) = emptySequence<CIRInst>()
        override fun callees(node: CIRInst) = emptySequence<CIRFunction>()
        override fun callers(method: CIRFunction) = emptySequence<CIRInst>()
        override fun entryPoints(method: CIRFunction) = method.flowGraph().entries.asSequence()
        override fun exitPoints(method: CIRFunction) = method.flowGraph().exits.asSequence()
        override fun methodOf(node: CIRInst): CIRFunction = node.location.method
        override fun statementsOf(method: CIRFunction) = method.allInstructions.asSequence()
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
        override val assignInstByLhv: Map<org.seqra.ir.api.cir.cfg.MLIRValue, org.seqra.ir.api.cir.cfg.CIRAssignInst>
            get() = allInstructions
                .filterIsInstance<org.seqra.ir.api.cir.cfg.CIRAssignInst>()
                .associateBy { it.lhv }

        private val syntheticReturn: CIRReturnOpInst by lazy {
            CIRReturnOpInst(CIRInstLocation(this, 0, MLIRUnknownLoc), MLIROpID(0), emptyList())
        }

        override fun <T> withIRNode(body: (ByteArray?) -> T): T = body(null)

        override fun flowGraph(): CIRGraph = object : CIRGraph {
            override val function get() = this@StubFunction
            override val entry get() = syntheticReturn
            override val instructions get() = listOf(syntheticReturn)
            override val entries get() = listOf(syntheticReturn)
            override val exits get() = listOf(syntheticReturn)

            override fun successors(node: CIRInst) = emptySet<CIRInst>()
            override fun predecessors(node: CIRInst) = emptySet<CIRInst>()
            override fun throwers(node: CIRInst) = emptySet<CIRInst>()
            override fun catchers(node: CIRInst) = emptySet<CIRInst>()
        }
    }

    private data class StubFinalFactAp(
        override val base: AccessPathBase,
        override val exclusions: ExclusionSet = ExclusionSet.Empty,
    ) : FinalFactAp {
        override val size: Int = 0
        override fun rebase(newBase: AccessPathBase): FinalFactAp = copy(base = newBase)
        override fun exclude(accessor: Accessor): FinalFactAp = this
        override fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp = copy(exclusions = exclusions)
        override fun isAbstract(): Boolean = false
        override fun startsWithAccessor(accessor: Accessor): Boolean = false
        override fun readAccessor(accessor: Accessor): FinalFactAp? = null
        override fun prependAccessor(accessor: Accessor): FinalFactAp = this
        override fun clearAccessor(accessor: Accessor): FinalFactAp? = this
        override fun removeAbstraction(): FinalFactAp? = this
        override fun delta(other: InitialFactAp): List<FinalFactAp.Delta> = emptyList()
        override fun concat(typeChecker: FactTypeChecker, delta: FinalFactAp.Delta): FinalFactAp? = this
        override fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp? = this
        override fun contains(factAp: InitialFactAp): Boolean = base == factAp.base
    }

    private data class StubInitialFactAp(
        override val base: AccessPathBase,
        override val exclusions: ExclusionSet = ExclusionSet.Empty,
    ) : InitialFactAp {
        override val size: Int = 0
        override fun rebase(newBase: AccessPathBase): InitialFactAp = copy(base = newBase)
        override fun exclude(accessor: Accessor): InitialFactAp = this
        override fun replaceExclusions(exclusions: ExclusionSet): InitialFactAp = copy(exclusions = exclusions)
        override fun startsWithAccessor(accessor: Accessor): Boolean = false
        override fun getAllAccessors(): List<Accessor> = emptyList()
        override fun readAccessor(accessor: Accessor): InitialFactAp? = null
        override fun prependAccessor(accessor: Accessor): InitialFactAp = this
        override fun clearAccessor(accessor: Accessor): InitialFactAp? = this
        override fun splitDelta(other: FinalFactAp): List<Pair<InitialFactAp, InitialFactAp.Delta>> = emptyList()
        override fun concat(delta: InitialFactAp.Delta): InitialFactAp = this
        override fun contains(factAp: InitialFactAp): Boolean = base == factAp.base
    }

    private val stubApManager = object : ApManager {
        override fun initialFactAbstraction(methodInitialStatement: org.seqra.ir.api.common.cfg.CommonInst) = error("unused")
        override fun methodEdgesFinalApSet(methodInitialStatement: org.seqra.ir.api.common.cfg.CommonInst, maxInstIdx: Int, languageManager: org.seqra.dataflow.ap.ifds.LanguageManager) = error("unused")
        override fun methodEdgesInitialToFinalApSet(methodInitialStatement: org.seqra.ir.api.common.cfg.CommonInst, maxInstIdx: Int, languageManager: org.seqra.dataflow.ap.ifds.LanguageManager) = error("unused")
        override fun methodEdgesNDInitialToFinalApSet(methodInitialStatement: org.seqra.ir.api.common.cfg.CommonInst, maxInstIdx: Int, languageManager: org.seqra.dataflow.ap.ifds.LanguageManager) = error("unused")
        override fun accessPathSubscription() = error("unused")
        override fun sideEffectRequirementApStorage() = error("unused")
        override fun methodFinalApSummariesStorage(methodInitialStatement: org.seqra.ir.api.common.cfg.CommonInst) = error("unused")
        override fun methodInitialToFinalApSummariesStorage(methodInitialStatement: org.seqra.ir.api.common.cfg.CommonInst) = error("unused")
        override fun methodNDInitialToFinalApSummariesStorage(methodInitialStatement: org.seqra.ir.api.common.cfg.CommonInst) = error("unused")
        override fun mostAbstractInitialAp(base: AccessPathBase) = error("unused")
        override fun mostAbstractFinalAp(base: AccessPathBase) = error("unused")
        override fun createFinalAp(base: AccessPathBase, exclusions: ExclusionSet) = error("unused")
        override fun createAbstractAp(base: AccessPathBase, exclusions: ExclusionSet) = error("unused")
        override fun createFinalInitialAp(base: AccessPathBase, exclusions: ExclusionSet) = error("unused")
        override fun createSerializer(context: org.seqra.dataflow.ap.ifds.serialization.SummarySerializationContext) = error("unused")
    }

    private val emptyTaintRulesProvider = object : CIRTaintRulesProvider {
        override fun entryPointRulesForMethod(method: CommonMethod): Iterable<TaintEntryPointSource> = emptyList()
        override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodSource> = emptyList()
        override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodSink> = emptyList()
        override fun sinkRulesForMethodEntry(method: CommonMethod): Iterable<TaintMethodEntrySink> = emptyList()
        override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintPassThrough> = emptyList()
        override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintCleaner> = emptyList()
    }

    private fun emptyTaintContext(cp: CIRClasspath) = TaintAnalysisContext(
        taintConfig = emptyTaintRulesProvider,
        taintSinkTracker = TaintSinkTracker(TaintAnalysisUnitStorage(stubApManager, CIRLanguageManager(cp))),
    )

    @Test
    fun `call Argument resolves to MLIR argument value`() {
        val cp = StubClasspath()
        val calleeFn = StubFunction(
            id = CIRFunctionID(moduleId, "callee"),
            classpath = cp,
            parameters = emptyList(),
            returnType = voidTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        val argVal = MLIRBlockValue(intTy, MLIRBlockID(9), 2L)
        val call = CIRCallOpInst(
            location = CIRInstLocation(calleeFn, 0, MLIRUnknownLoc),
            id = MLIROpID(1),
            arg_ops = listOf(argVal),
            exception = null,
            callee = MLIRFlatSymbolRefAttr(MLIRStringAttr("x", null)),
            callingConv = CIRCallingConv.C,
            extraAttrs = extraAttrs,
            result = null,
            calleeRef = CIRCalleeRef("x", cp),
        )
        val r = CallPositionToCIRValueResolver(call, null).resolve(Argument(0))
        assertTrue(r.isSome)
        assertEquals(argVal, r.getOrThrow())
    }

    @Test
    fun `call Result uses explicit returnValue then synthesized op result`() {
        val cp = StubClasspath()
        val calleeFn = StubFunction(
            id = CIRFunctionID(moduleId, "m"),
            classpath = cp,
            parameters = emptyList(),
            returnType = voidTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        val explicit = MLIRBlockValue(floatTy, MLIRBlockID(1), 0L)
        val call = CIRCallOpInst(
            location = CIRInstLocation(calleeFn, 0, MLIRUnknownLoc),
            id = MLIROpID(7),
            arg_ops = emptyList(),
            exception = null,
            callee = MLIRFlatSymbolRefAttr(MLIRStringAttr("y", null)),
            callingConv = CIRCallingConv.C,
            extraAttrs = extraAttrs,
            result = intTy,
            calleeRef = CIRCalleeRef("y", cp),
        )
        assertEquals(
            explicit,
            CallPositionToCIRValueResolver(call, explicit).resolve(Result).getOrThrow(),
        )
        assertEquals(
            MLIROpValue(intTy, MLIROpID(7), 0L),
            CallPositionToCIRValueResolver(call, null).resolve(Result).getOrThrow(),
        )
    }

    @Test
    fun `call Result none when no result and void call`() {
        val cp = StubClasspath()
        val calleeFn = StubFunction(
            id = CIRFunctionID(moduleId, "m"),
            classpath = cp,
            parameters = emptyList(),
            returnType = voidTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        val call = CIRCallOpInst(
            location = CIRInstLocation(calleeFn, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            arg_ops = emptyList(),
            exception = null,
            callee = MLIRFlatSymbolRefAttr(MLIRStringAttr("z", null)),
            callingConv = CIRCallingConv.C,
            extraAttrs = extraAttrs,
            result = null,
            calleeRef = CIRCalleeRef("z", cp),
        )
        assertTrue(CallPositionToCIRValueResolver(call, null).resolve(Result).isNone)
    }

    @Test
    fun `callee Argument maps parameter to entry block argument`() {
        val cp = StubClasspath()
        val entryBlock = MLIRBasicBlock(
            MLIRBlockID(0),
            instListOf<CIRInst>(),
            arguments = listOf(intTy),
        )
        val p0 = StubParameter(0, intTy)
        val fn = StubFunction(
            id = CIRFunctionID(moduleId, "f"),
            classpath = cp,
            parameters = listOf(p0),
            returnType = voidTy,
            blocks = CIRBlockList(listOf(entryBlock)),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        p0.holder = fn
        val expected = MLIRBlockValue(intTy, entryBlock.id, 0L)
        assertEquals(
            expected,
            CalleePositionToCIRValueResolver(fn).resolve(Argument(0)).getOrThrow(),
        )
    }

    @Test
    fun `type resolver Argument Result This and nested access`() {
        val cp = StubClasspath()
        cp.register(intTy, CIRSingleType(intTy))
        cp.register(floatTy, CIRSingleType(floatTy))
        val methodTy = MLIRTypeID(moduleId, "method-fn-ty")
        val clsTy = MLIRTypeID(moduleId, "cls")
        cp.register(methodTy, CIRMethodType(methodTy, voidTy, clsTy))
        cp.register(clsTy, CIRSingleType(clsTy))

        val p0 = StubParameter(0, intTy)
        val fn = StubFunction(
            id = CIRFunctionID(moduleId, "g"),
            classpath = cp,
            parameters = listOf(p0),
            returnType = floatTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(methodTy),
            allInstructions = emptyList(),
        )
        p0.holder = fn

        val tr = CIRMethodPositionBaseTypeResolver(fn)
        assertEquals(CIRSingleType(intTy), tr.resolve(Argument(0)))
        assertEquals(CIRSingleType(floatTy), tr.resolve(Result))
        assertNull(tr.resolve(ClassStatic("X")))
        assertEquals(CIRSingleType(clsTy), tr.resolve(This))
        assertEquals(
            CIRSingleType(intTy),
            tr.resolve(PositionWithAccess(Argument(0), PositionAccessor.ElementAccessor)),
        )
    }

    @Test
    fun `unresolved branches throw Not Implemented`() {
        val cp = StubClasspath()
        val fn = StubFunction(
            id = CIRFunctionID(moduleId, "f"),
            classpath = cp,
            parameters = emptyList(),
            returnType = voidTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        val call = CIRCallOpInst(
            location = CIRInstLocation(fn, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            arg_ops = emptyList(),
            exception = null,
            callee = MLIRFlatSymbolRefAttr(MLIRStringAttr("z", null)),
            callingConv = CIRCallingConv.C,
            extraAttrs = extraAttrs,
            result = null,
            calleeRef = CIRCalleeRef("z", cp),
        )
        val callResolver = CallPositionToCIRValueResolver(call, null)
        assertTrue(callResolver.resolve(This).isNone)
        assertFailsWith<RuntimeException> {
            callResolver.resolve(
                PositionWithAccess(Argument(0), PositionAccessor.FieldAccessor("C", "f", "t")),
            )
        }
        assertFailsWith<RuntimeException> {
            CalleePositionToCIRValueResolver(fn).resolve(Result)
        }.also { assertEquals("Not Implemented", it.message) }
    }

    @Test
    fun `fact mapper maps call argument fact to callee argument base`() {
        val cp = StubClasspath()
        val callee = StubFunction(
            id = CIRFunctionID(moduleId, "callee"),
            classpath = cp,
            parameters = emptyList(),
            returnType = voidTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        cp.register(callee)
        cp.register(intTy, CIRSingleType(intTy))
        val argVal = MLIRBlockValue(intTy, MLIRBlockID(3), 1L)
        val call = CIRCallOpInst(
            location = CIRInstLocation(callee, 0, MLIRUnknownLoc),
            id = MLIROpID(11),
            arg_ops = listOf(argVal),
            exception = null,
            callee = MLIRFlatSymbolRefAttr(MLIRStringAttr("callee", null)),
            callingConv = CIRCallingConv.C,
            extraAttrs = extraAttrs,
            result = null,
            calleeRef = CIRCalleeRef("callee", cp),
        )
        val fact = StubFinalFactAp(AccessPathBase.Argument(1))

        val mapped = mutableListOf<Pair<FinalFactAp, AccessPathBase>>()
        CIRMethodCallFactMapper.mapMethodCallToStartFlowFact(callee, call, fact, object : FactTypeChecker {
            override fun filterFactByLocalType(actualType: org.seqra.ir.api.common.CommonType?, factAp: FinalFactAp): FinalFactAp = factAp
            override fun accessPathFilter(accessPath: List<Accessor>): FactTypeChecker.FactApFilter = FactTypeChecker.AlwaysAcceptFilter
        }) { mappedFact, startFactBase ->
            mapped += mappedFact to startFactBase
        }

        assertEquals(listOf<Pair<FinalFactAp, AccessPathBase>>(fact to AccessPathBase.Argument(0)), mapped)
        assertTrue(CIRMethodCallFactMapper.factIsRelevantToMethodCall(null, call, fact))
    }

    @Test
    fun `fact mapper treats zero ptr_stride argument as aliasing stride base LocalVar`() {
        val cp = StubClasspath()
        cp.register(intTy, CIRSingleType(intTy))
        val ptrTy = MLIRTypeID(moduleId, "!cir.ptr<struct>")
        cp.register(ptrTy, CIRSingleType(ptrTy))

        val basePtr = MLIROpValue(ptrTy, MLIROpID(10), 0L)
        val zeroOp = MLIROpValue(intTy, MLIROpID(11), 0L)
        val stridedPtr = MLIROpValue(ptrTy, MLIROpID(12), 0L)

        val placeholderFn = StubFunction(
            id = CIRFunctionID(moduleId, "placeholder"),
            classpath = cp,
            parameters = emptyList(),
            returnType = voidTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        cp.register(placeholderFn)

        val sinkParam = StubParameter(0, ptrTy)
        val callerFn = StubFunction(
            id = CIRFunctionID(moduleId, "caller"),
            classpath = cp,
            parameters = emptyList(),
            returnType = voidTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(voidTy),
            allInstructions = listOf(
                CIRAssignInst(
                    CIRInstLocation(placeholderFn, 0, MLIRUnknownLoc),
                    MLIROpID(11),
                    zeroOp,
                    CIRConstantOpExpr(MLIRIntegerAttr(null, BigInteger.ZERO), intTy),
                ),
                CIRAssignInst(
                    CIRInstLocation(placeholderFn, 0, MLIRUnknownLoc),
                    MLIROpID(12),
                    stridedPtr,
                    CIRPtrStrideOpExpr(basePtr, zeroOp, ptrTy),
                ),
            ),
        )
        cp.register(callerFn)

        val sinkCallee = StubFunction(
            id = CIRFunctionID(moduleId, "sinkFn"),
            classpath = cp,
            parameters = listOf(sinkParam),
            returnType = voidTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        sinkParam.holder = sinkCallee
        cp.register(sinkCallee)

        val call = CIRCallOpInst(
            location = CIRInstLocation(callerFn, 0, MLIRUnknownLoc),
            id = MLIROpID(20),
            arg_ops = listOf(stridedPtr),
            exception = null,
            callee = MLIRFlatSymbolRefAttr(MLIRStringAttr("sinkFn", null)),
            callingConv = CIRCallingConv.C,
            extraAttrs = extraAttrs,
            result = null,
            calleeRef = CIRCalleeRef("sinkFn", cp),
        )

        val factOnBasePointer = StubFinalFactAp(AccessPathBase.LocalVar(10))
        assertTrue(CIRMethodCallFactMapper.factIsRelevantToMethodCall(null, call, factOnBasePointer, null))

        val mapped = mutableListOf<Pair<FinalFactAp, AccessPathBase>>()
        CIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
            sinkCallee,
            call,
            factOnBasePointer,
            object : FactTypeChecker {
                override fun filterFactByLocalType(
                    actualType: org.seqra.ir.api.common.CommonType?,
                    factAp: FinalFactAp,
                ): FinalFactAp = factAp

                override fun accessPathFilter(accessPath: List<Accessor>): FactTypeChecker.FactApFilter =
                    FactTypeChecker.AlwaysAcceptFilter
            },
        ) { mappedFact, startFactBase ->
            mapped += mappedFact to startFactBase
        }

        assertEquals(
            listOf<Pair<FinalFactAp, AccessPathBase>>(factOnBasePointer to AccessPathBase.Argument(0)),
            mapped,
        )
    }

    @Test
    fun `factIsRelevantToMethodCall with null alias analysis uses direct bases only for LocalVar`() {
        val cp = StubClasspath()
        val callee = StubFunction(
            id = CIRFunctionID(moduleId, "callee"),
            classpath = cp,
            parameters = emptyList(),
            returnType = voidTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        cp.register(callee)
        val call = CIRCallOpInst(
            location = CIRInstLocation(callee, 0, MLIRUnknownLoc),
            id = MLIROpID(11),
            arg_ops = listOf(MLIRBlockValue(intTy, MLIRBlockID(3), 1L)),
            exception = null,
            callee = MLIRFlatSymbolRefAttr(MLIRStringAttr("callee", null)),
            callingConv = CIRCallingConv.C,
            extraAttrs = extraAttrs,
            result = null,
            calleeRef = CIRCalleeRef("callee", cp),
        )
        val unrelatedLocal = StubFinalFactAp(AccessPathBase.LocalVar(999))
        assertFalse(CIRMethodCallFactMapper.factIsRelevantToMethodCall(null, call, unrelatedLocal))
        assertFalse(CIRMethodCallFactMapper.factIsRelevantToMethodCall(null, call, unrelatedLocal, null))
    }

    @Test
    fun `call flow emits call-to-start fact for relevant CIR argument fact`() {
        val cp = StubClasspath()
        val callee = StubFunction(
            id = CIRFunctionID(moduleId, "callee"),
            classpath = cp,
            parameters = emptyList(),
            returnType = voidTy,
            blocks = CIRBlockList(emptyList()),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        cp.register(callee)
        cp.register(intTy, CIRSingleType(intTy))
        val call = CIRCallOpInst(
            location = CIRInstLocation(callee, 0, MLIRUnknownLoc),
            id = MLIROpID(12),
            arg_ops = listOf(MLIRBlockValue(intTy, MLIRBlockID(0), 0L)),
            exception = null,
            callee = MLIRFlatSymbolRefAttr(MLIRStringAttr("callee", null)),
            callingConv = CIRCallingConv.C,
            extraAttrs = extraAttrs,
            result = null,
            calleeRef = CIRCalleeRef("callee", cp),
        )
        val lm = CIRLanguageManager(cp)
        val graph = StubApplicationGraph(cp)
        val context = CIRMethodAnalysisContext(
            MethodEntryPoint(EmptyMethodContext, call),
            CIRFactTypeChecker(cp),
            CIRLocalVariableReachability(callee, graph, lm),
            null,
            emptyTaintContext(cp),
        )
        val fact = StubFinalFactAp(AccessPathBase.Argument(0))

        val result = CIRMethodCallFlowFunction(stubApManager, context, null, call, call).propagateZeroToFact(fact)

        val startFact = result.single()
        assertIs<MethodCallFlowFunction.CallToStartZFact>(startFact)
        assertEquals(AccessPathBase.Argument(0), startFact.startFactBase)
        assertEquals(fact, startFact.callerFactAp)
    }

    @Test
    fun `start flow propagateFact filters unsupported this facts and keeps argument facts`() {
        val cp = StubClasspath()
        val entryBlock = MLIRBasicBlock(
            MLIRBlockID(0),
            instListOf<CIRInst>(),
            arguments = listOf(intTy),
        )
        val p0 = StubParameter(0, intTy)
        val fn = StubFunction(
            id = CIRFunctionID(moduleId, "entry"),
            classpath = cp,
            parameters = listOf(p0),
            returnType = voidTy,
            blocks = CIRBlockList(listOf(entryBlock)),
            funcOp = stubFuncOp(voidTy),
            allInstructions = emptyList(),
        )
        p0.holder = fn
        val lm = CIRLanguageManager(cp)
        val graph = StubApplicationGraph(cp)
        val context = CIRMethodAnalysisContext(
            MethodEntryPoint(EmptyMethodContext, fn.flowGraph().entry),
            CIRFactTypeChecker(cp),
            CIRLocalVariableReachability(fn, graph, lm),
            null,
            emptyTaintContext(cp),
        )
        val flow = CIRMethodStartFlowFunction(stubApManager, context)

        val argFacts = flow.propagateFact(StubFinalFactAp(AccessPathBase.Argument(0)))
        val thisFacts = flow.propagateFact(StubFinalFactAp(AccessPathBase.This))

        assertEquals(1, argFacts.size)
        assertTrue(thisFacts.isEmpty())
    }
}
