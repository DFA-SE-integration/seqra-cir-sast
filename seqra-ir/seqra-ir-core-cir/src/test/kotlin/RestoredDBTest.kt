import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.impl.CIRProjectImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertNotNull

class RestoredDBTest {
    // @Test
    fun restoredDbTest() {
        val handler = WithRestoredDB()

        var db = handler.restartDb()

        val projectDirectoryPath = "src/test/resources/doubleModuleWithLinkCommandsTypes"
        val project = CIRProjectImpl(File(projectDirectoryPath))
        val target = project.targets.first()

        val mainModule =
            MLIRModuleID("/Users/sergey/Documents/seqra.ir/seqra.ir-core-cir/src/test/resources/doubleModuleWithLinkCommandsTypes/mainModule.c")
        val mainFunction = CIRFunctionID(mainModule, "main")

        val helperModule =
            MLIRModuleID("/Users/sergey/Documents/seqra.ir/seqra.ir-core-cir/src/test/resources/doubleModuleWithLinkCommandsTypes/helperModule.c")
        val sourceFunction = CIRFunctionID(helperModule, "source")

        // Set up database and classpath
        db.loadProjects(project)
        val initialClasspath = db.classpath(target)

        val mainFunctionBeforeRestart = initialClasspath.findFunctionOrNull(mainFunction)
        assertNotNull(mainFunctionBeforeRestart)

        // Refresh base and check that the previous classpath is invalidated
        // Notice, that during to caching feature we can not request
        // `main` function.
        db = handler.restartDb()
        assertThrows<Throwable> { initialClasspath.findFunctionOrNull(sourceFunction) }

        // Refresh classpath
        val reloadedClasspath = db.classpath(target)
        val mainFunctionAfterRestart = reloadedClasspath.findFunctionOrNull(mainFunction)
        assertNotNull(mainFunctionAfterRestart)

        // Initial classpath still do not work
        assertThrows<Throwable> { initialClasspath.findFunctionOrNull(sourceFunction) }
    }
}