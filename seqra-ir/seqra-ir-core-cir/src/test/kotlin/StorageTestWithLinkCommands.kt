import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.TargetID
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.impl.CIRProjectImpl
import org.seqra.ir.impl.CIRSettings
import org.seqra.ir.impl.CIRXodusKvErsSettings
import org.seqra.ir.impl.features.CIRLoadStoreFeature
import org.seqra.ir.impl.jacodb
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths

class StorageTestWithLinkCommands {
    private val settings = CIRSettings().apply {
        persistenceImpl(CIRXodusKvErsSettings)
    }

    private var db: CIRDatabase = jacodb(settings)

    @BeforeEach
    fun setUpDatabase() {
        db = jacodb(settings)
    }

    // @Test
    fun doubleModuleWithLinkCommands() {
        val projectDirectoryFile = File("src/test/resources/doubleModuleWithLinkCommands")
        val project = CIRProjectImpl(projectDirectoryFile)

        val db = jacodb(settings)
        db.loadFiles(projectDirectoryFile)

        val cp = db.classpath(
            target = project.targets.first(), features = listOf(CIRLoadStoreFeature)
        )

        val mainFunction = cp.findFunctionOrNull(
            CIRFunctionID(
                MLIRModuleID("/Users/sergey/Documents/seqra.ir/seqra.ir-core-cir/src/test/resources/doubleModuleWithLinkCommands/mainModule.c"),
                "main"
            )
        )!!
        println(mainFunction)

        cp.findFunctionOrNull(
            CIRFunctionID(
                MLIRModuleID("/Users/sergey/Documents/seqra.ir/seqra.ir-core-cir/src/test/resources/doubleModuleWithLinkCommands/mainModule.c"),
                "main"
            )
        )!!

        val sourceFunction = cp.findFunctionOrNull(
            CIRFunctionID(
                MLIRModuleID("/Users/sergey/Documents/seqra.ir/seqra.ir-core-cir/src/test/resources/doubleModuleWithLinkCommands/mainModule.c"),
                "source"
            )
        )!!
        println(sourceFunction)

        val sinkFunction = cp.findFunctionOrNull(
            CIRFunctionID(
                MLIRModuleID("/Users/sergey/Documents/seqra.ir/seqra.ir-core-cir/src/test/resources/doubleModuleWithLinkCommands/mainModule.c"),
                "sink"
            )
        )!!
        println(sinkFunction)
    }

    // @Test
    fun doubleModuleWithLinkCommandsTypes() {
        val projectDirectoryPath = "src/test/resources/doubleModuleWithLinkCommandsTypes"
        val project = CIRProjectImpl(File(projectDirectoryPath))

        val db = jacodb(settings)
        db.loadFiles(File(projectDirectoryPath))

        val cp = db.classpath(target = project.targets.first())

        val mainModuleID =
            MLIRModuleID("/Users/sergey/Documents/seqra.ir/seqra.ir-core-cir/src/test/resources/doubleModuleWithLinkCommandsTypes/mainModule.c")

        val mainFunction = cp.findFunctionOrNull(
            CIRFunctionID(mainModuleID, "main")
        )!!
        println(mainFunction)

        val sourceFunction = cp.findFunctionOrNull(
            CIRFunctionID(mainModuleID, "source")
        )!!
        println(sourceFunction)

        val sinkFunction = cp.findFunctionOrNull(
            CIRFunctionID(mainModuleID, "sink")
        )!!
        println(sinkFunction)

        val dataHolderType = cp.findTypeOrNull(
            MLIRTypeID(
                mainModuleID, "!cir.struct<struct \"data_holder\" {!cir.int<s, 32>} #cir.record.decl.ast>"
            )
        )!!
        println(dataHolderType)

        val globalVal = cp.findGlobalOrNull(
            CIRGlobalID(
                mainModuleID, "global_val"
            )
        )!!
        println(globalVal)

        cp.close()
    }

    // @Test
    fun doubleModuleWithLinkCommandsTypesClasspathFromSingleObjectFile() {
        val projectDirectoryPath = "src/test/resources/doubleModuleWithLinkCommandsTypes"
        val project = CIRProjectImpl(File(projectDirectoryPath))

        val db = jacodb(settings)
        db.loadFiles(File(projectDirectoryPath))

        val target = TargetID(
            name = Paths.get(projectDirectoryPath, "helperModule.o").toFile().absolutePath, project = project
        )

        val cp = db.classpath(target)
        val helperModuleID =
            MLIRModuleID("/Users/sergey/Documents/seqra.ir/seqra.ir-core-cir/src/test/resources/doubleModuleWithLinkCommandsTypes/helperModule.c")

        val sink = cp.findFunctionOrNull(functionID = CIRFunctionID(helperModuleID, "sink"))!!
        println(sink)
    }
}