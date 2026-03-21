import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.impl.CIRSettings
import org.seqra.ir.impl.CIRXodusKvErsSettings
import org.seqra.ir.impl.jacodb
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertNull

class DurabilityTest {
    private val settings = CIRSettings().apply {
        persistenceImpl(CIRXodusKvErsSettings)
    }

    private var db: CIRDatabase = jacodb(settings)

    @BeforeEach
    fun setUpDatabase() {
        db = jacodb(settings)
    }

    // @Test
    fun failedParsingTest() {
        val testFile = File("src/test/resources/failingParsingTest/helperModule.cir")

        val db = jacodb(settings)
        db.loadFiles(testFile)

        val cp = db.classpath(listOf(testFile))
        val module =
            MLIRModuleID("/home/sergey/Documents/seqra.ir/seqra.ir-core-cir/src/test/resources/failingParsingTest/helperModule.c")

        val sourceFunction = cp.findFunctionOrNull(CIRFunctionID(module, "source"))
        assertNull(sourceFunction)

        val sinkFunction = cp.findFunctionOrNull(CIRFunctionID(module, "sink"))
        assertNull(sinkFunction)
    }
}