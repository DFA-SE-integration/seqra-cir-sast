import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.impl.CIRSettings
import org.seqra.ir.impl.CIRXodusKvErsSettings
import org.seqra.ir.impl.cirDatabase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import samples.doubleModuleHelperFile
import samples.doubleModuleMainFile
import samples.singleModuleFile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class SyntheticStorageTest {
    private val settings = CIRSettings().apply {
        persistenceImpl(CIRXodusKvErsSettings)
    }

    private var db: CIRDatabase = cirDatabase(settings)

    @BeforeEach
    fun setUpDatabase() {
        db = cirDatabase(settings)
    }

    @Test
    fun baseTest() {
        db.loadFiles(singleModuleFile)
        val cp = db.classpath(listOf(singleModuleFile))
        val singleModule = "singleModule"

        val mainFunction = cp.findFunctionOrNull(CIRFunctionID(MLIRModuleID(singleModule), "main"))!!
        println(mainFunction)
    }

    @Test
    fun doubleModuleTest() {
        val sources = listOf(doubleModuleMainFile, doubleModuleHelperFile)
        db.loadFiles(sources)

        val cp = db.classpath(sources)
        val mainModule = "mainModule"

        val mainFunction = cp.findFunctionOrNull(CIRFunctionID(MLIRModuleID(mainModule), "main"))!!
        println(mainFunction)
        assertEquals("main", mainFunction.name)
//        assertFalse(mainFunction.instList.isEmpty())

        println("\"${mainFunction.name}\": ${mainFunction.returnType} parameters")
        for (param in mainFunction.parameters) {
            println(param)
        }

        val sourceFunction = cp.findFunctionOrNull(CIRFunctionID(MLIRModuleID(mainModule), "source"))!!
        println(sourceFunction)
        assertEquals("source", sourceFunction.name)
//        assertFalse(sourceFunction.instList.isEmpty())

        println("\"${sourceFunction.name}\": ${sourceFunction.returnType} parameters")
        for (param in sourceFunction.parameters) {
            println(param)
        }

        val sinkFunction = cp.findFunctionOrNull(CIRFunctionID(MLIRModuleID(mainModule), "sink"))!!
        println(sinkFunction)
        assertEquals("sink", sinkFunction.name)
//        assertFalse(sinkFunction.instList.isEmpty())
        println("\"${sinkFunction.name}\": ${sinkFunction.returnType} parameters")
        for (param in sinkFunction.parameters) {
            println(param)
        }
    }

    @Test
    fun functionCachesFlattenedInstructionsAndFlowGraph() {
        db.loadFiles(listOf(doubleModuleMainFile, doubleModuleHelperFile))

        val cp = db.classpath(listOf(doubleModuleMainFile, doubleModuleHelperFile))
        val mainFunction = cp.findFunctionOrNull(CIRFunctionID(MLIRModuleID("mainModule"), "main"))!!

        assertSame(mainFunction.blocks, mainFunction.blocks)
        assertSame(mainFunction.allInstructions, mainFunction.allInstructions)
        assertSame(mainFunction.flowGraph(), mainFunction.flowGraph())

        val flattenedFromBlocks = mainFunction.blocks.blocks.flatMap { it.instructions.instructions }
        assertEquals(flattenedFromBlocks.size, mainFunction.allInstructions.size)
        mainFunction.allInstructions.forEachIndexed { index, instruction ->
            assertEquals(index, instruction.location.index)
            assertSame(flattenedFromBlocks[index], instruction)
        }
    }

    @Test
    fun symbolLookupPrefersConcreteDefinitionsAndResolvesCallSites() {
        db.loadFiles(listOf(doubleModuleMainFile, doubleModuleHelperFile))

        val cp = db.classpath(listOf(doubleModuleMainFile, doubleModuleHelperFile))
        val sourceFunction = assertNotNull(cp.findFunctionBySymbolName("source"))
        val sinkFunction = assertNotNull(cp.findFunctionBySymbolName("sink"))

        assertEquals("helperModule", sourceFunction.id.moduleID.id)
        assertEquals("helperModule", sinkFunction.id.moduleID.id)

        val mainFunction = cp.findFunctionOrNull(CIRFunctionID(MLIRModuleID("mainModule"), "main"))!!
        val resolvedCallees = mainFunction.allInstructions
            .filterIsInstance<CIRCallOpInst>()
            .mapNotNull { it.calleeRef?.function?.id }

        assertEquals(
            listOf(
                CIRFunctionID(MLIRModuleID("helperModule"), "source"),
                CIRFunctionID(MLIRModuleID("helperModule"), "sink"),
            ),
            resolvedCallees,
        )
    }
}
