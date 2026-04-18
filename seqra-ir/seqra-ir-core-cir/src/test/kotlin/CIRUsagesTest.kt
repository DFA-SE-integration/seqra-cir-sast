import kotlinx.coroutines.runBlocking
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.impl.CIRSettings
import org.seqra.ir.impl.CIRXodusKvErsSettings
import org.seqra.ir.impl.cirDatabase
import org.seqra.ir.impl.features.CIRUsages
import org.seqra.ir.impl.features.CIRUsageFeatureRequest
import org.seqra.ir.impl.features.usagesExt
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import samples.singleModuleFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CIRUsagesTest {
    private val settings = CIRSettings().apply {
        persistenceImpl(CIRXodusKvErsSettings)
        installFeatures(CIRUsages)
    }

    private lateinit var db: org.seqra.ir.api.cir.CIRDatabase

    @BeforeEach
    fun setup() {
        db = cirDatabase(settings)
    }

    @Test
    fun findUsagesReturnsCallersAndOffsetsMatchAllInstructions() {
        db.loadFiles(singleModuleFile)
        val cp = db.classpath(listOf(singleModuleFile))
        val moduleId = MLIRModuleID("singleModule")

        val sink = cp.findFunctionOrNull(CIRFunctionID(moduleId, "sink"))!!
        val main = cp.findFunctionOrNull(CIRFunctionID(moduleId, "main"))!!

        val callers = runBlocking { cp.usagesExt() }.findUsages(sink).toList()
        assertTrue(callers.any { it.name == "main" }, "expected main to call sink")

        val responses = CIRUsages.syncQuery(cp, CIRUsageFeatureRequest(setOf(sink.name))).toList()
        val mainResponse = responses.single { it.source.functionID.id == "main" }
        val mainResolved = cp.findFunctionOrNull(mainResponse.source.functionID)!!
        assertEquals(main.name, mainResolved.name)
        assertTrue(mainResponse.offsets.isNotEmpty())
        val instructions = mainResolved.allInstructions
        assertTrue(
            mainResponse.offsets.all { idx ->
                val i = idx.toInt()
                i in instructions.indices && instructions[i].location.index == i
            }
        )
    }
}
