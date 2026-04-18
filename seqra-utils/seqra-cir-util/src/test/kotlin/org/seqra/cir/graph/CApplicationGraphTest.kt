package org.seqra.cir.graph

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRClasspathFeature
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.cir.cfg.CIRCalleeRef
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRCallingConv
import org.seqra.ir.api.cir.cfg.CIRExtraFuncAttributesAttr
import org.seqra.ir.api.cir.cfg.CIRFuncOp
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRFunctionParameter
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.CIRGraph
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRInstLocation
import org.seqra.ir.api.cir.cfg.CIRReturnOpInst
import org.seqra.ir.api.cir.cfg.CIRTryCallOpInst
import org.seqra.ir.api.cir.cfg.MLIRArrayAttr
import org.seqra.ir.api.cir.cfg.MLIRBlockID
import org.seqra.ir.api.cir.cfg.MLIRDictionaryAttr
import org.seqra.ir.api.cir.cfg.MLIRFlatSymbolRefAttr
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIROpID
import org.seqra.ir.api.cir.cfg.MLIRType
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRUnknownLoc
import org.seqra.ir.impl.features.SyncCIRUsagesExtension
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CApplicationGraphTest {
    private val moduleId = MLIRModuleID("test-module")
    private val voidType = MLIRTypeID(moduleId, "void")
    private val extraAttrs = CIRExtraFuncAttributesAttr(MLIRDictionaryAttr(arrayListOf()))

    @Test
    fun `delegates graph structure and method boundaries to CIR methods`() {
        val cp = FakeClasspath()
        val usages = mockk<SyncCIRUsagesExtension>()
        val method = FakeFunction("main", cp)
        val first = returnInst(method, 0)
        val second = returnInst(method, 1)
        val graph = FakeCIRGraph(
            function = method,
            instructions = listOf(first, second),
            entries = listOf(first),
            exits = listOf(second),
            successors = mapOf(first to setOf(second)),
            predecessors = mapOf(second to setOf(first)),
        )
        method.graph = graph
        method.instructionsList = listOf(first, second)
        every { usages.findUsages(any()) } returns emptySequence()

        val appGraph = CApplicationGraphImpl(cp, usages)

        assertSame(cp, appGraph.cp)
        assertEquals(setOf(second), appGraph.successors(first).toSet())
        assertEquals(setOf(first), appGraph.predecessors(second).toSet())
        assertEquals(listOf(first), appGraph.entryPoints(method).toList())
        assertEquals(listOf(second), appGraph.exitPoints(method).toList())
        assertSame(method, appGraph.methodOf(first))
        assertEquals(listOf(first, second), appGraph.statementsOf(method).toList())
    }

    @Test
    fun `resolves callees and callers from call instructions`() {
        val cp = FakeClasspath()
        val usages = mockk<SyncCIRUsagesExtension>()
        val target = FakeFunction("sink", cp)
        val other = FakeFunction("helper", cp)
        val caller = FakeFunction("main", cp)

        cp.register(target)
        cp.register(other)
        cp.register(caller)

        val directCall = callInst(caller, 0, "sink", cp)
        val tryCall = tryCallInst(caller, 1, "sink", cp)
        val otherCall = callInst(caller, 2, "helper", cp)
        val plain = returnInst(caller, 3)

        caller.instructionsList = listOf(directCall, tryCall, otherCall, plain)
        caller.graph = FakeCIRGraph(
            function = caller,
            instructions = caller.instructionsList,
            entries = listOf(directCall),
            exits = listOf(plain),
            successors = emptyMap(),
            predecessors = emptyMap(),
        )
        every { usages.findUsages(target) } returns sequenceOf(caller)

        val appGraph = CApplicationGraphImpl(cp, usages)

        assertEquals(listOf(target), appGraph.callees(directCall).toList())
        assertEquals(listOf(target), appGraph.callees(tryCall).toList())
        assertTrue(appGraph.callees(plain).toList().isEmpty())
        assertEquals(listOf(directCall, tryCall), appGraph.callers(target).toList())
    }

    private fun returnInst(method: CIRFunction, index: Int): CIRReturnOpInst = CIRReturnOpInst(
        location = CIRInstLocation(method, index, MLIRUnknownLoc),
        id = MLIROpID(index.toLong()),
        input = emptyList(),
    )

    private fun callInst(method: CIRFunction, index: Int, calleeName: String, cp: CIRClasspath): CIRCallOpInst = CIRCallOpInst(
        location = CIRInstLocation(method, index, MLIRUnknownLoc),
        id = MLIROpID(index.toLong()),
        arg_ops = emptyList(),
        exception = null,
        callee = MLIRFlatSymbolRefAttr(rootReference = org.seqra.ir.api.cir.cfg.MLIRStringAttr(calleeName, null)),
        callingConv = CIRCallingConv.C,
        extraAttrs = extraAttrs,
        result = null,
        calleeRef = CIRCalleeRef(calleeName, cp),
    )

    private fun tryCallInst(method: CIRFunction, index: Int, calleeName: String, cp: CIRClasspath): CIRTryCallOpInst = CIRTryCallOpInst(
        location = CIRInstLocation(method, index, MLIRUnknownLoc),
        id = MLIROpID(index.toLong()),
        contOperands = emptyList(),
        landingPadOperands = emptyList(),
        arg_ops = emptyList(),
        callee = MLIRFlatSymbolRefAttr(rootReference = org.seqra.ir.api.cir.cfg.MLIRStringAttr(calleeName, null)),
        callingConv = CIRCallingConv.C,
        extraAttrs = extraAttrs,
        cont = MLIRBlockID(0),
        landingPad = MLIRBlockID(1),
        result = null,
        calleeRef = CIRCalleeRef(calleeName, cp),
    )

    private inner class FakeCIRGraph(
        override val function: CIRFunction,
        override val instructions: List<CIRInst>,
        override val entries: List<CIRInst>,
        override val exits: List<CIRInst>,
        private val successors: Map<CIRInst, Set<CIRInst>>,
        private val predecessors: Map<CIRInst, Set<CIRInst>>,
    ) : CIRGraph {
        override val entry: CIRInst = entries.first()

        override fun successors(node: CIRInst): Set<CIRInst> = successors[node].orEmpty()

        override fun predecessors(node: CIRInst): Set<CIRInst> = predecessors[node].orEmpty()

        override fun throwers(node: CIRInst): Set<CIRInst> = emptySet()

        override fun catchers(node: CIRInst): Set<CIRInst> = emptySet()
    }

    private inner class FakeFunction(name: String, cp: CIRClasspath) : CIRFunction {
        override val id: CIRFunctionID = CIRFunctionID(moduleId, name)
        override val classpath: CIRClasspath = cp
        override val parameters: List<CIRFunctionParameter> = emptyList()
        override val returnType: MLIRTypeID = voidType
        override val info: CIRFuncOp
            get() = error("Not used in test")
        override val blocks: CIRBlockList
            get() = error("Not used in test")
        override val allInstructions: List<CIRInst>
            get() = instructionsList

        lateinit var graph: CIRGraph
        var instructionsList: List<CIRInst> = emptyList()

        override fun <T> withIRNode(body: (ByteArray?) -> T): T = body(null)

        override fun flowGraph(): CIRGraph = graph
    }

    private inner class FakeClasspath : CIRClasspath {
        private val functionsByName = linkedMapOf<String, CIRFunction>()

        override val db: CIRDatabase
            get() = error("Not used in test")
        override val registeredLocations: List<RegisteredLocation> = emptyList()
        override val registeredLocationIds: Set<Long> = emptySet()
        override val features: List<CIRClasspathFeature> = emptyList()
        override val moduleNames: List<String>
            get() = functionsByName.values.map { it.id.moduleID.id }.distinct()

        fun register(function: CIRFunction) {
            functionsByName[function.name] = function
        }

        override fun findFunctionOrNull(functionID: CIRFunctionID): CIRFunction? =
            functionsByName[functionID.id]?.takeIf { it.id == functionID }

        override fun findFunctionBySymbolName(symbolName: String): CIRFunction? = functionsByName[symbolName]

        override fun findTypeOrNull(typeID: MLIRTypeID): MLIRType? = null

        override fun findGlobalOrNull(globalID: CIRGlobalID) = null

        override fun getGlobalConstructors(): List<CIRFunctionID> = emptyList()

        override fun getGlobalDestructors(): List<CIRFunctionID> = emptyList()

        override fun close() = Unit
    }
}
