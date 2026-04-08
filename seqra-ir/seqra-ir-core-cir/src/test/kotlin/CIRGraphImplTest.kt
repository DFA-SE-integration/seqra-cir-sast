import org.seqra.ir.api.cir.CIRBitCodeLocation
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRClasspathFeature
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.cir.cfg.CIRBrOpInst
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRExtraFuncAttributesAttr
import org.seqra.ir.api.cir.cfg.CIRFuncOp
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRFunctionParameter
import org.seqra.ir.api.cir.cfg.CIRGraph
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRInstLocation
import org.seqra.ir.api.cir.cfg.CIRReturnOpInst
import org.seqra.ir.api.cir.cfg.CIRSwitchFlatOpInst
import org.seqra.ir.api.cir.cfg.CIRTryCallOpInst
import org.seqra.ir.api.cir.cfg.CIRCallingConv
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.MLIRArrayAttr
import org.seqra.ir.api.cir.cfg.MLIRBlockID
import org.seqra.ir.api.cir.cfg.MLIRDictionaryAttr
import org.seqra.ir.api.cir.cfg.MLIRDenseI32ArrayAttr
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIROpID
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRUnknownLoc
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRBasicBlock
import org.seqra.ir.impl.cfg.CIRGraphImpl
import org.seqra.ir.impl.cfg.instListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CIRGraphImplTest {
    private val moduleId = MLIRModuleID("test-module")
    private val voidType = MLIRTypeID(moduleId, "void")
    private val extraAttrs = CIRExtraFuncAttributesAttr(MLIRDictionaryAttr(arrayListOf()))

    @Test
    fun `graph reuses canonical allInstructions list`() {
        val entryBlockId = MLIRBlockID(0)
        val exitBlockId = MLIRBlockID(1)
        val function = FakeFunction("reuse")

        val branch = CIRBrOpInst(
            location = CIRInstLocation(function, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            destOperands = emptyList(),
            dest = exitBlockId,
        )
        val ret = CIRReturnOpInst(
            location = CIRInstLocation(function, 1, MLIRUnknownLoc),
            id = MLIROpID(1),
            input = emptyList(),
        )

        function.blockList = CIRBlockList(
            listOf(
                MLIRBasicBlock(entryBlockId, instListOf(branch), emptyList()),
                MLIRBasicBlock(exitBlockId, instListOf(ret), emptyList()),
            )
        )
        function.instructionsList = listOf(branch, ret)

        val graph = CIRGraphImpl(function)

        assertSame(function.allInstructions, graph.instructions)
    }

    @Test
    fun `try call has both continuation and landing pad successors`() {
        val entryBlockId = MLIRBlockID(0)
        val contBlockId = MLIRBlockID(1)
        val landingPadBlockId = MLIRBlockID(2)
        val function = FakeFunction("try-call")

        val tryCall = CIRTryCallOpInst(
            location = CIRInstLocation(function, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            contOperands = emptyList(),
            landingPadOperands = emptyList(),
            arg_ops = emptyList(),
            callee = null,
            callingConv = CIRCallingConv.C,
            extraAttrs = extraAttrs,
            cont = contBlockId,
            landingPad = landingPadBlockId,
            result = null,
            calleeRef = null,
        )
        val contReturn = CIRReturnOpInst(
            location = CIRInstLocation(function, 1, MLIRUnknownLoc),
            id = MLIROpID(1),
            input = emptyList(),
        )
        val landingPadReturn = CIRReturnOpInst(
            location = CIRInstLocation(function, 2, MLIRUnknownLoc),
            id = MLIROpID(2),
            input = emptyList(),
        )

        function.blockList = CIRBlockList(
            listOf(
                MLIRBasicBlock(entryBlockId, instListOf(tryCall), emptyList()),
                MLIRBasicBlock(contBlockId, instListOf(contReturn), emptyList()),
                MLIRBasicBlock(landingPadBlockId, instListOf(landingPadReturn), emptyList()),
            )
        )
        function.instructionsList = listOf(tryCall, contReturn, landingPadReturn)

        val graph = CIRGraphImpl(function)

        assertEquals(setOf(contReturn, landingPadReturn), graph.successors(tryCall))
        assertEquals(setOf(tryCall), graph.predecessors(contReturn))
        assertEquals(setOf(tryCall), graph.predecessors(landingPadReturn))
    }

    @Test
    fun `switch has successors for default and every case destination`() {
        val entryBlockId = MLIRBlockID(0)
        val defaultBlockId = MLIRBlockID(1)
        val firstCaseBlockId = MLIRBlockID(2)
        val secondCaseBlockId = MLIRBlockID(3)
        val function = FakeFunction("switch")

        val switch = CIRSwitchFlatOpInst(
            location = CIRInstLocation(function, 0, MLIRUnknownLoc),
            id = MLIROpID(0),
            condition = MLIRValue(voidType),
            defaultOperands = emptyList(),
            caseOperands = emptyList(),
            caseValues = MLIRArrayAttr(arrayListOf()),
            caseOperandSegments = MLIRDenseI32ArrayAttr(0, arrayListOf()),
            defaultDestination = defaultBlockId,
            caseDestinations = listOf(firstCaseBlockId, secondCaseBlockId),
        )
        val defaultReturn = CIRReturnOpInst(
            location = CIRInstLocation(function, 1, MLIRUnknownLoc),
            id = MLIROpID(1),
            input = emptyList(),
        )
        val firstCaseReturn = CIRReturnOpInst(
            location = CIRInstLocation(function, 2, MLIRUnknownLoc),
            id = MLIROpID(2),
            input = emptyList(),
        )
        val secondCaseReturn = CIRReturnOpInst(
            location = CIRInstLocation(function, 3, MLIRUnknownLoc),
            id = MLIROpID(3),
            input = emptyList(),
        )

        function.blockList = CIRBlockList(
            listOf(
                MLIRBasicBlock(entryBlockId, instListOf(switch), emptyList()),
                MLIRBasicBlock(defaultBlockId, instListOf(defaultReturn), emptyList()),
                MLIRBasicBlock(firstCaseBlockId, instListOf(firstCaseReturn), emptyList()),
                MLIRBasicBlock(secondCaseBlockId, instListOf(secondCaseReturn), emptyList()),
            )
        )
        function.instructionsList = listOf(switch, defaultReturn, firstCaseReturn, secondCaseReturn)

        val graph = CIRGraphImpl(function)

        assertEquals(setOf(defaultReturn, firstCaseReturn, secondCaseReturn), graph.successors(switch))
        assertEquals(setOf(switch), graph.predecessors(defaultReturn))
        assertEquals(setOf(switch), graph.predecessors(firstCaseReturn))
        assertEquals(setOf(switch), graph.predecessors(secondCaseReturn))
    }

    private inner class FakeFunction(name: String) : CIRFunction {
        override val id = CIRFunctionID(moduleId, name)
        override lateinit var blocks: CIRBlockList
        override lateinit var allInstructions: List<CIRInst>
        override val info: CIRFuncOp
            get() = error("Not used in test")
        override val classpath: CIRClasspath = FakeClasspath()
        override val parameters: List<CIRFunctionParameter> = emptyList()
        override val returnType: MLIRTypeID = voidType

        var blockList: CIRBlockList
            get() = blocks
            set(value) {
                blocks = value
            }

        var instructionsList: List<CIRInst>
            get() = allInstructions
            set(value) {
                allInstructions = value
            }

        override fun <T> withIRNode(body: (ByteArray?) -> T): T = body(null)

        override fun flowGraph(): CIRGraph = CIRGraphImpl(this)
    }

    private inner class FakeClasspath : CIRClasspath {
        override val db: CIRDatabase
            get() = error("Not used in test")
        override val registeredLocations: List<RegisteredLocation> = emptyList()
        override val registeredLocationIds: Set<Long> = emptySet()
        override val features: List<CIRClasspathFeature> = emptyList()
        override val moduleNames: List<String> = emptyList()

        override fun findFunctionOrNull(functionID: CIRFunctionID): CIRFunction? = null
        override fun findFunctionBySymbolName(symbolName: String): CIRFunction? = null
        override fun findTypeOrNull(typeID: MLIRTypeID) = null
        override fun findGlobalOrNull(globalID: org.seqra.ir.api.cir.cfg.CIRGlobalID) = null
        override fun getGlobalConstructors(): List<CIRFunctionID> = emptyList()
        override fun getGlobalDestructors(): List<CIRFunctionID> = emptyList()
        override fun close() = Unit
    }
}
