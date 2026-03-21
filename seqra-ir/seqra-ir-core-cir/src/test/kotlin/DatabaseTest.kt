import builders.CIRCommandsBuilder
import builders.LinkCommandsBuilder
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.impl.CIRProjectImpl
import org.seqra.ir.impl.CIRSettings
import org.seqra.ir.impl.CIRXodusKvErsSettings
import org.seqra.ir.impl.cirDatabase
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertNull

class DatabaseTest {
    private lateinit var db: CIRDatabase

    @BeforeEach
    fun setup() {
        db = cirDatabase(CIRSettings().persistenceImpl(CIRXodusKvErsSettings))
    }

    // @Test
    fun refreshFileTest() {
        val sampleProjectFile = File("src/test/resources/doubleModuleWithLinkCommandsTypes")

        val tmpProjectFile = createTempDirectory().toFile()
        sampleProjectFile.copyRecursively(tmpProjectFile)

        val helperModuleCirFile = tmpProjectFile.resolve("helperModule.cir")
        val helperModuleProtocirFile = tmpProjectFile.resolve("helperModule.protocir")
        val mainModuleCirFile = tmpProjectFile.resolve("mainModule.cir")
        val mainModuleProtocirFile = tmpProjectFile.resolve("mainModule.protocir")

        CIRCommandsBuilder(tmpProjectFile.toPath()).append(
            CIRCommandsBuilder.CompilationTarget(
                helperModuleCirFile.toPath(), "helperModule.o"
            )
        ).append(
            CIRCommandsBuilder.CompilationTarget(
                mainModuleCirFile.toPath(), "mainModule.o"
            )
        ).buildToFile()

        LinkCommandsBuilder(tmpProjectFile.toPath()).append(
            LinkCommandsBuilder.LinkTarget(
                files = listOf(tmpProjectFile.resolve("mainModule.o"), tmpProjectFile.resolve("helperModule.o")),
                outputFile = tmpProjectFile.resolve("app.o")
            )
        ).buildToFile()

        val tmpProject = CIRProjectImpl(tmpProjectFile)
        db.loadProjects(tmpProject)

        val oldClasspath = db.classpath(tmpProject.targets.first())

        mainModuleCirFile.copyTo(helperModuleCirFile, overwrite = true)
        mainModuleProtocirFile.copyTo(helperModuleProtocirFile, overwrite = true)

        db.refresh()
        val newClasspath = db.classpath(tmpProject.targets.first())

        val moduleID =
            MLIRModuleID("/Users/sergey/Documents/seqra.ir/seqra.ir-core-cir/src/test/resources/doubleModuleWithLinkCommandsTypes/helperModule.c")
        val functionID = CIRFunctionID(moduleID, "source");

        assertNotNull(oldClasspath.findFunctionOrNull(functionID))
        assertNull(newClasspath.findFunctionOrNull(functionID))

        db.close()
    }
}