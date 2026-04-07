import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.LinkCommands
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.impl.CIRProjectImpl
import org.seqra.ir.impl.CIRSettings
import org.seqra.ir.impl.CIRXodusKvErsSettings
import org.seqra.ir.impl.cirDatabase
import org.seqra.ir.impl.sources.findCompileCommands
import org.seqra.ir.impl.sources.findLinkCommands
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class JulietTest {
    private val settings = CIRSettings().apply {
        persistenceImpl(CIRXodusKvErsSettings)
    }

    private var db: CIRDatabase = cirDatabase(settings)

    @BeforeEach
    fun setUpDatabase() {
        db = cirDatabase(settings)
    }

    // @Test
    fun useAfterFree() {
        val useAfterFreeFolder = File("src/test/resources/juliet/CWE416_Use_After_Free")
        val supportFolder = File("src/test/resources/juliet/support")

        val supportProject = CIRProjectImpl(supportFolder)
        val project = CIRProjectImpl(useAfterFreeFolder)

        db.loadProjects(listOf(supportProject, project))
        val cp = db.classpath(project.targets.first())

        val CWE416_Use_After_Free__malloc_free_struct_10 = cp.findFunctionOrNull(
            CIRFunctionID(
                MLIRModuleID("/home/ladisgin/git_proj/juliet-c/testcases/CWE416_Use_After_Free/CWE416_Use_After_Free__malloc_free_struct_10.c"),
                "CWE416_Use_After_Free__malloc_free_struct_10_bad"
            )
        )!!

        println(CWE416_Use_After_Free__malloc_free_struct_10)
        assertTrue(CWE416_Use_After_Free__malloc_free_struct_10.blocks.blocks.isNotEmpty())

        val printStructLine = cp.findFunctionOrNull(
            CIRFunctionID(
                MLIRModuleID("/home/ladisgin/git_proj/juliet-c/testcases/CWE416_Use_After_Free/CWE416_Use_After_Free__malloc_free_struct_10.c"),
                "printStructLine"
            )
        )!!
        println(printStructLine)
        assertTrue(printStructLine.blocks.blocks.isNotEmpty())
    }
}
