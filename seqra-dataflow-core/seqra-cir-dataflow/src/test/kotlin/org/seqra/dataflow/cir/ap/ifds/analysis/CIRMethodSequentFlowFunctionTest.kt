package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.TaintMarkAccessor
import org.seqra.dataflow.ap.ifds.EmptyMethodContext
import org.seqra.dataflow.ap.ifds.ExclusionSet
import org.seqra.dataflow.ap.ifds.FieldAccessor
import org.seqra.dataflow.ap.ifds.MethodEntryPoint
import org.seqra.dataflow.ap.ifds.access.automata.AutomataApManager
import org.seqra.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.seqra.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.seqra.dataflow.ap.ifds.taint.TaintAnalysisUnitStorage
import org.seqra.dataflow.ap.ifds.taint.TaintSinkTracker
import org.seqra.dataflow.cir.ap.ifds.taint.PositionAccess
import org.seqra.dataflow.cir.ap.ifds.taint.mkAccessPath
import org.seqra.dataflow.cir.ap.ifds.CIRFieldTypeEncoding
import org.seqra.cir.graph.CApplicationGraph
import org.seqra.dataflow.cir.ap.ifds.CIRFactTypeChecker
import org.seqra.dataflow.cir.ap.ifds.CIRLanguageManager
import org.seqra.dataflow.cir.ap.ifds.CIRLocalVariableReachability
import org.seqra.dataflow.cir.ap.ifds.taint.CIRTaintRulesProvider
import org.seqra.dataflow.configuration.core.TaintCleaner
import org.seqra.dataflow.configuration.core.TaintEntryPointSource
import org.seqra.dataflow.configuration.core.TaintMethodEntrySink
import org.seqra.dataflow.configuration.core.TaintMethodSink
import org.seqra.dataflow.configuration.core.TaintMethodSource
import org.seqra.dataflow.configuration.core.TaintPassThrough
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRClasspathFeature
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRCallingConv
import org.seqra.ir.api.cir.cfg.CIRExpr
import org.seqra.ir.api.cir.cfg.CIRExtraFuncAttributesAttr
import org.seqra.ir.api.cir.cfg.CIRFuncOp
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRFunctionParameter
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.CIRGlobalLinkageKind
import org.seqra.ir.api.cir.cfg.CIRGetMemberOpExpr
import org.seqra.ir.api.cir.cfg.CIRGraph
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRInstLocation
import org.seqra.ir.api.cir.cfg.CIRPtrStrideOpExpr
import org.seqra.ir.api.cir.cfg.CIRVisibilityAttr
import org.seqra.ir.api.cir.cfg.CIRVisibilityKind
import org.seqra.ir.api.cir.cfg.MLIRBasicBlock
import org.seqra.ir.api.cir.cfg.MLIRBlockID
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.cir.cfg.MLIRDictionaryAttr
import org.seqra.ir.api.cir.cfg.MLIRIntegerAttr
import org.seqra.ir.api.cir.cfg.MLIROpID
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIRStringAttr
import org.seqra.ir.api.cir.cfg.MLIRType
import org.seqra.ir.api.cir.cfg.MLIRTypeAttr
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRUnknownLoc
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.cfg.CommonInst
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CIRMethodSequentFlowFunctionTest {
    private val moduleId = MLIRModuleID("test-mod")
    private val voidTy = MLIRTypeID(moduleId, "void")
    private val ptrTy = MLIRTypeID(moduleId, "ptr.S")
    private val fieldPtrTy = MLIRTypeID(moduleId, "ptr.i32")
    private val indexTy = MLIRTypeID(moduleId, "i64")
    private val extraAttrs = CIRExtraFuncAttributesAttr(MLIRDictionaryAttr(arrayListOf()))

    private inner class StubClasspath : CIRClasspath {
        override val db: CIRDatabase get() = error("unused")
        override val registeredLocations: List<RegisteredLocation> = emptyList()
        override val registeredLocationIds: Set<Long> = emptySet()
        override val features: List<CIRClasspathFeature> = emptyList()
        override val moduleNames: List<String> = listOf(moduleId.id)
        override fun findFunctionOrNull(functionID: CIRFunctionID): CIRFunction? = null
        override fun findFunctionBySymbolName(symbolName: String): CIRFunction? = null
        override fun findTypeOrNull(typeID: MLIRTypeID): MLIRType? = null
        override fun findGlobalOrNull(globalID: CIRGlobalID) = null
        override fun getGlobalConstructors(): List<CIRFunctionID> = emptyList()
        override fun getGlobalDestructors(): List<CIRFunctionID> = emptyList()
        override fun close() = Unit
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

    private inner class StubFunction(
        override val id: CIRFunctionID,
        override val classpath: CIRClasspath,
        override val parameters: List<CIRFunctionParameter>,
        override val returnType: MLIRTypeID,
        override val blocks: CIRBlockList,
        private val funcOp: CIRFuncOp,
        override var allInstructions: List<CIRInst>,
    ) : CIRFunction {
        override val info: CIRFuncOp get() = funcOp
        override val assignInstByLhv: Map<org.seqra.ir.api.cir.cfg.MLIRValue, CIRAssignInst>
            get() = allInstructions.filterIsInstance<CIRAssignInst>().associateBy { it.lhv }

        override fun <T> withIRNode(body: (ByteArray?) -> T): T = body(null)
        override fun flowGraph(): CIRGraph = object : CIRGraph {
            override val function get() = this@StubFunction
            override val entry get() = error("unused")
            override val instructions: List<CIRInst> get() = allInstructions
            override val entries: List<CIRInst> get() = emptyList()
            override val exits: List<CIRInst> get() = emptyList()
            override fun successors(node: CIRInst) = emptySet<CIRInst>()
            override fun predecessors(node: CIRInst) = emptySet<CIRInst>()
            override fun throwers(node: CIRInst) = emptySet<CIRInst>()
            override fun catchers(node: CIRInst) = emptySet<CIRInst>()
        }

        fun withInstructions(instructions: List<CIRInst>) =
            StubFunction(id, classpath, parameters, returnType, blocks, funcOp, instructions)
    }

    private val emptyTaintRulesProvider = object : CIRTaintRulesProvider {
        override fun entryPointRulesForMethod(method: CommonMethod): Iterable<TaintEntryPointSource> = emptyList()
        override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodSource> = emptyList()
        override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodSink> = emptyList()
        override fun sinkRulesForMethodEntry(method: CommonMethod): Iterable<TaintMethodEntrySink> = emptyList()
        override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintPassThrough> = emptyList()
        override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintCleaner> = emptyList()
    }

    private fun emptyTaintContext(cp: CIRClasspath, apManager: AutomataApManager) = TaintAnalysisContext(
        taintConfig = emptyTaintRulesProvider,
        taintSinkTracker = TaintSinkTracker(TaintAnalysisUnitStorage(apManager, CIRLanguageManager(cp))),
    )

    private fun stubFunction(): StubFunction = StubFunction(
        id = CIRFunctionID(moduleId, "f"),
        classpath = StubClasspath(),
        parameters = emptyList(),
        returnType = voidTy,
        blocks = CIRBlockList(emptyList<MLIRBasicBlock>()),
        funcOp = stubFuncOp(voidTy),
        allInstructions = emptyList(),
    )

    private fun flowFor(
        fn: StubFunction,
        currentAssign: CIRAssignInst,
        allInstructions: List<CIRInst>,
    ): Pair<AutomataApManager, CIRMethodSequentFlowFunction> {
        val apManager = AutomataApManager()
        fn.allInstructions = allInstructions
        val lm = CIRLanguageManager(fn.classpath)
        val graph = StubApplicationGraph(fn.classpath)
        val context = CIRMethodAnalysisContext(
            methodEntryPoint = MethodEntryPoint(EmptyMethodContext, currentAssign),
            factTypeChecker = CIRFactTypeChecker(fn.classpath),
            localVariableReachability = CIRLocalVariableReachability(fn, graph, lm),
            aliasAnalysis = null,
            taint = emptyTaintContext(fn.classpath, apManager),
        )
        return apManager to CIRMethodSequentFlowFunction(apManager, context, currentAssign)
    }

    private fun flowForWithStorage(
        fn: StubFunction,
        currentAssign: CIRAssignInst,
        allInstructions: List<CIRInst>,
    ): Triple<AutomataApManager, CIRMethodSequentFlowFunction, TaintAnalysisUnitStorage> {
        val apManager = AutomataApManager()
        fn.allInstructions = allInstructions
        val lm = CIRLanguageManager(fn.classpath)
        val graph = StubApplicationGraph(fn.classpath)
        val storage = TaintAnalysisUnitStorage(apManager, lm)
        val context = CIRMethodAnalysisContext(
            methodEntryPoint = MethodEntryPoint(EmptyMethodContext, currentAssign),
            factTypeChecker = CIRFactTypeChecker(fn.classpath),
            localVariableReachability = CIRLocalVariableReachability(fn, graph, lm),
            aliasAnalysis = null,
            taint = TaintAnalysisContext(
                taintConfig = emptyTaintRulesProvider,
                taintSinkTracker = TaintSinkTracker(storage),
            ),
        )
        return Triple(apManager, CIRMethodSequentFlowFunction(apManager, context, currentAssign), storage)
    }

    private fun ptrStrideAssign(
        fn: StubFunction,
        instIndex: Int,
        lhv: MLIRValue,
        base: MLIRValue,
        stride: MLIRValue,
    ) = CIRAssignInst(
        location = CIRInstLocation(fn, instIndex, MLIRUnknownLoc),
        id = MLIROpID(instIndex.toLong()),
        lhv = lhv,
        rhv = CIRPtrStrideOpExpr(base = base, stride = stride, result = ptrTy),
    )

    private fun getMemberAssign(
        fn: StubFunction,
        instIndex: Int,
        lhv: MLIRValue,
        base: MLIRValue,
        fieldName: String,
    ) = CIRAssignInst(
        location = CIRInstLocation(fn, instIndex, MLIRUnknownLoc),
        id = MLIROpID(instIndex.toLong()),
        lhv = lhv,
        rhv = CIRGetMemberOpExpr(
            addr = base,
            name = MLIRStringAttr(fieldName, null),
            indexAttr = MLIRIntegerAttr(null, BigInteger.ZERO),
            result = fieldPtrTy,
        ),
    )

    private fun fieldAccessor(fieldName: String) =
        FieldAccessor("", fieldName, CIRFieldTypeEncoding.encode(fieldPtrTy))

    private fun assertZeroToFacts(sequents: Set<Sequent>): Set<org.seqra.dataflow.ap.ifds.access.FinalFactAp> =
        sequents.mapNotNullTo(linkedSetOf()) { (it as? Sequent.ZeroToFact)?.factAp }

    @Test
    fun `propagateZeroToFact treats current assignment lhs as base instead of self-recursing into rhs accessor`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val stride = MLIRBlockValue(indexTy, MLIRBlockID(0), 1L)
        val lhv = MLIROpValue(ptrTy, MLIROpID(7), 0L)
        val assign = ptrStrideAssign(fn, instIndex = 7, lhv = lhv, base = base, stride = stride)
        val (apManager, flow) = flowFor(fn, assign, listOf(assign))
        val sourceFact = apManager.createFinalAp(AccessPathBase.Argument(0), ExclusionSet.Empty)
            .prependAccessor(ElementAccessor)
        val expectedFact = apManager.createFinalAp(AccessPathBase.LocalVar(7), ExclusionSet.Empty)

        val sequents = flow.propagateZeroToFact(sourceFact)

        assertContains(sequents, Sequent.ZeroToFact(expectedFact))
    }

    @Test
    fun `propagateZeroToFact resolves prior ptr_stride assignment for accessor read`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val stride = MLIRBlockValue(indexTy, MLIRBlockID(0), 1L)
        val priorTmp = MLIROpValue(ptrTy, MLIROpID(5), 0L)
        val currentTmp = MLIROpValue(ptrTy, MLIROpID(7), 0L)
        val priorAssign = ptrStrideAssign(fn, instIndex = 5, lhv = priorTmp, base = base, stride = stride)
        val currentAssign = CIRAssignInst(
            location = CIRInstLocation(fn, 7, MLIRUnknownLoc),
            id = MLIROpID(7),
            lhv = currentTmp,
            rhv = priorTmp,
        )
        val (apManager, flow) = flowFor(fn, currentAssign, listOf(priorAssign, currentAssign))
        val sourceFact = apManager.createFinalAp(AccessPathBase.Argument(0), ExclusionSet.Empty)
            .prependAccessor(ElementAccessor)
        val expectedFact = apManager.createFinalAp(AccessPathBase.LocalVar(7), ExclusionSet.Empty)

        val sequents = flow.propagateZeroToFact(sourceFact)

        assertContains(sequents, Sequent.ZeroToFact(expectedFact))
    }

    @Test
    fun `propagateZeroToFact ignores future ptr_stride assignment when resolving rhs value`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val stride = MLIRBlockValue(indexTy, MLIRBlockID(0), 1L)
        val futureTmp = MLIROpValue(ptrTy, MLIROpID(9), 0L)
        val currentTmp = MLIROpValue(ptrTy, MLIROpID(7), 0L)
        val currentAssign = CIRAssignInst(
            location = CIRInstLocation(fn, 7, MLIRUnknownLoc),
            id = MLIROpID(7),
            lhv = currentTmp,
            rhv = futureTmp,
        )
        val futureAssign = ptrStrideAssign(fn, instIndex = 9, lhv = futureTmp, base = base, stride = stride)
        val (apManager, flow) = flowFor(fn, currentAssign, listOf(currentAssign, futureAssign))
        val sourceFact = apManager.createFinalAp(AccessPathBase.Argument(0), ExclusionSet.Empty)
            .prependAccessor(ElementAccessor)
        val unexpectedFact = apManager.createFinalAp(AccessPathBase.LocalVar(7), ExclusionSet.Empty)

        val sequents = flow.propagateZeroToFact(sourceFact)
        val zeroFacts = assertZeroToFacts(sequents)

        assertFalse(unexpectedFact in zeroFacts)
        assertEquals(setOf(Sequent.Unchanged), sequents)
    }

    @Test
    fun `propagateZeroToFact resolves prior ptr_stride assignment for store destination write`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val stride = MLIRBlockValue(indexTy, MLIRBlockID(0), 1L)
        val destPtr = MLIROpValue(ptrTy, MLIROpID(5), 0L)
        val sourceValue = MLIROpValue(indexTy, MLIROpID(6), 0L)
        val priorAssign = ptrStrideAssign(fn, instIndex = 5, lhv = destPtr, base = base, stride = stride)
        val currentAssign = CIRAssignInst(
            location = CIRInstLocation(fn, 7, MLIRUnknownLoc),
            id = MLIROpID(7),
            lhv = MLIRValueRef(destPtr),
            rhv = sourceValue,
        )
        val (apManager, flow) = flowFor(fn, currentAssign, listOf(priorAssign, currentAssign))
        val sourceFact = apManager.createFinalAp(AccessPathBase.LocalVar(6), ExclusionSet.Empty)
        val expectedWrittenFact = apManager.createFinalAp(AccessPathBase.Argument(0), ExclusionSet.Empty)
            .prependAccessor(ElementAccessor)

        val sequents = flow.propagateZeroToFact(sourceFact)

        assertContains(sequents, Sequent.Unchanged)
        assertContains(sequents, Sequent.ZeroToFact(expectedWrittenFact))
    }

    @Test
    fun `propagateZeroToFact treats current get_member assignment lhs as base instead of self-recursing into field accessor`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val lhv = MLIROpValue(fieldPtrTy, MLIROpID(11), 0L)
        val assign = getMemberAssign(fn, instIndex = 11, lhv = lhv, base = base, fieldName = "intOne")
        val (apManager, flow) = flowFor(fn, assign, listOf(assign))
        val sourceFact = apManager.createFinalAp(AccessPathBase.Argument(0), ExclusionSet.Empty)
            .prependAccessor(fieldAccessor("intOne"))
        val expectedFact = apManager.createFinalAp(AccessPathBase.LocalVar(11), ExclusionSet.Empty)

        val sequents = flow.propagateZeroToFact(sourceFact)

        assertContains(sequents, Sequent.ZeroToFact(expectedFact))
    }

    @Test
    fun `propagateZeroToFact resolves prior get_member assignment for field read`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val priorTmp = MLIROpValue(fieldPtrTy, MLIROpID(11), 0L)
        val currentTmp = MLIROpValue(fieldPtrTy, MLIROpID(13), 0L)
        val priorAssign = getMemberAssign(fn, instIndex = 11, lhv = priorTmp, base = base, fieldName = "intOne")
        val currentAssign = CIRAssignInst(
            location = CIRInstLocation(fn, 13, MLIRUnknownLoc),
            id = MLIROpID(13),
            lhv = currentTmp,
            rhv = priorTmp,
        )
        val (apManager, flow) = flowFor(fn, currentAssign, listOf(priorAssign, currentAssign))
        val sourceFact = apManager.createFinalAp(AccessPathBase.Argument(0), ExclusionSet.Empty)
            .prependAccessor(fieldAccessor("intOne"))
        val expectedFact = apManager.createFinalAp(AccessPathBase.LocalVar(13), ExclusionSet.Empty)

        val sequents = flow.propagateZeroToFact(sourceFact)

        assertContains(sequents, Sequent.ZeroToFact(expectedFact))
    }

    @Test
    fun `propagateZeroToFact ignores future get_member assignment when resolving rhs value`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val futureTmp = MLIROpValue(fieldPtrTy, MLIROpID(15), 0L)
        val currentTmp = MLIROpValue(fieldPtrTy, MLIROpID(13), 0L)
        val currentAssign = CIRAssignInst(
            location = CIRInstLocation(fn, 13, MLIRUnknownLoc),
            id = MLIROpID(13),
            lhv = currentTmp,
            rhv = futureTmp,
        )
        val futureAssign = getMemberAssign(fn, instIndex = 15, lhv = futureTmp, base = base, fieldName = "intOne")
        val (apManager, flow) = flowFor(fn, currentAssign, listOf(currentAssign, futureAssign))
        val sourceFact = apManager.createFinalAp(AccessPathBase.Argument(0), ExclusionSet.Empty)
            .prependAccessor(fieldAccessor("intOne"))
        val unexpectedFact = apManager.createFinalAp(AccessPathBase.LocalVar(13), ExclusionSet.Empty)

        val sequents = flow.propagateZeroToFact(sourceFact)
        val zeroFacts = assertZeroToFacts(sequents)

        assertFalse(unexpectedFact in zeroFacts)
        assertEquals(setOf(Sequent.Unchanged), sequents)
    }

    @Test
    fun `propagateZeroToFact resolves prior get_member assignment for store destination field write`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val destPtr = MLIROpValue(fieldPtrTy, MLIROpID(11), 0L)
        val sourceValue = MLIROpValue(indexTy, MLIROpID(12), 0L)
        val priorAssign = getMemberAssign(fn, instIndex = 11, lhv = destPtr, base = base, fieldName = "intTwo")
        val currentAssign = CIRAssignInst(
            location = CIRInstLocation(fn, 13, MLIRUnknownLoc),
            id = MLIROpID(13),
            lhv = MLIRValueRef(destPtr),
            rhv = sourceValue,
        )
        val (apManager, flow) = flowFor(fn, currentAssign, listOf(priorAssign, currentAssign))
        val sourceFact = apManager.createFinalAp(AccessPathBase.LocalVar(12), ExclusionSet.Empty)
        val expectedWrittenFact = apManager.createFinalAp(AccessPathBase.Argument(0), ExclusionSet.Empty)
            .prependAccessor(fieldAccessor("intTwo"))

        val sequents = flow.propagateZeroToFact(sourceFact)

        assertContains(sequents, Sequent.Unchanged)
        assertContains(sequents, Sequent.ZeroToFact(expectedWrittenFact))
    }

    @Test
    fun `propagateZeroToFact reports UAF sink on ptr_stride over marked array element`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val stride = MLIRBlockValue(indexTy, MLIRBlockID(0), 1L)
        val lhv = MLIROpValue(ptrTy, MLIROpID(7), 0L)
        val assign = ptrStrideAssign(fn, instIndex = 7, lhv = lhv, base = base, stride = stride)
        val (apManager, flow, storage) = flowForWithStorage(fn, assign, listOf(assign))

        val markedStub = apManager.createFinalAp(AccessPathBase.This, ExclusionSet.Universe)
            .prependAccessor(TaintMarkAccessor("use-after-free"))
        val uafFact = mkAccessPath(
            PositionAccess.Complex(PositionAccess.Simple(AccessPathBase.Argument(0)), ElementAccessor),
            markedStub,
            ExclusionSet.Empty,
        )

        flow.propagateZeroToFact(uafFact)

        val vulns = mutableListOf<TaintSinkTracker.TaintVulnerability>()
        storage.collectVulnerabilities(vulns)
        assertEquals(1, vulns.size)
        val v = vulns.single()
        assertTrue(v is TaintSinkTracker.TaintVulnerabilityWithFact)
        val withFact = v as TaintSinkTracker.TaintVulnerabilityWithFact
        assertEquals("use-after-free-deref", withFact.rule.id)
        assertEquals(assign, withFact.statement)
        assertEquals(TaintSinkTracker.VulnerabilityTriggerPosition.BEFORE_INST, withFact.vulnerabilityTriggerPosition)
        val sinkRule = withFact.rule as TaintMethodSink
        assertEquals(listOf(416), sinkRule.meta.cwe)
    }

    @Test
    fun `propagateZeroToFact does not report ptr_stride UAF when mark is only on pointer not array element`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val stride = MLIRBlockValue(indexTy, MLIRBlockID(0), 1L)
        val lhv = MLIROpValue(ptrTy, MLIROpID(7), 0L)
        val assign = ptrStrideAssign(fn, instIndex = 7, lhv = lhv, base = base, stride = stride)
        val (apManager, flow, storage) = flowForWithStorage(fn, assign, listOf(assign))

        val markedStub = apManager.createFinalAp(AccessPathBase.This, ExclusionSet.Universe)
            .prependAccessor(TaintMarkAccessor("use-after-free"))
        val factPointerOnly = mkAccessPath(
            PositionAccess.Simple(AccessPathBase.Argument(0)),
            markedStub,
            ExclusionSet.Empty,
        )

        flow.propagateZeroToFact(factPointerOnly)

        val vulns = mutableListOf<TaintSinkTracker.TaintVulnerability>()
        storage.collectVulnerabilities(vulns)
        assertEquals(0, vulns.size)
    }

    @Test
    fun `propagateZeroToFact does not report ptr_stride UAF without use-after-free mark`() {
        val fn = stubFunction()
        val base = MLIRBlockValue(ptrTy, MLIRBlockID(0), 0L)
        val stride = MLIRBlockValue(indexTy, MLIRBlockID(0), 1L)
        val lhv = MLIROpValue(ptrTy, MLIROpID(7), 0L)
        val assign = ptrStrideAssign(fn, instIndex = 7, lhv = lhv, base = base, stride = stride)
        val (apManager, flow, storage) = flowForWithStorage(fn, assign, listOf(assign))

        val factNoMark = apManager.createFinalAp(AccessPathBase.Argument(0), ExclusionSet.Empty)
            .prependAccessor(ElementAccessor)

        flow.propagateZeroToFact(factNoMark)

        val vulns = mutableListOf<TaintSinkTracker.TaintVulnerability>()
        storage.collectVulnerabilities(vulns)
        assertEquals(0, vulns.size)
    }
}
