import CIRProjectEntity.PROJECT_MALFORMED_CIR_COMMANDS_PATH
import CIRProjectEntity.PROJECT_WITHOUT_CIR_COMMANDS_PATH
import org.seqra.ir.api.cir.TargetID
import org.seqra.ir.impl.CIRProjectImpl
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CIRProjectTest {
    @Test
    fun fromCompileAndLinkCommandsTest() {
        val linkCommandsFile =
            Paths.get(CIRProjectEntity.PROJECT_JULIET_USE_AFTER_FREE_PATH, "link_commands.json").toFile()
        val cirCommandsFile =
            Paths.get(CIRProjectEntity.PROJECT_JULIET_USE_AFTER_FREE_PATH, "cir_commands.json").toFile()
        val project = CIRProjectImpl(cirCompileCommands = cirCommandsFile, cirLinkCommands = linkCommandsFile)

        val target = project.findTarget(project.targets.first())
        assertNotNull(target)
    }

    @Test
    fun fromDirectoryTest() {
        val project = CIRProjectImpl(File(CIRProjectEntity.PROJECT_JULIET_USE_AFTER_FREE_PATH))
        assertTrue(project.targets.isNotEmpty())
    }

    @Test
    fun findExistingTargetTest() {
        val project = CIRProjectImpl(File(CIRProjectEntity.PROJECT_JULIET_USE_AFTER_FREE_PATH))

        val target = project.findTarget(project.targets.first())
        assertNotNull(target)
    }

    @Test
    fun findNonExistingTargetTest() {
        val project = CIRProjectImpl(File(CIRProjectEntity.PROJECT_JULIET_USE_AFTER_FREE_PATH))

        val nonExistingTargetID = TargetID("non-existing target", project)
        val target = project.findTarget(nonExistingTargetID)

        assertTrue(target.internalDependencies.isEmpty())

        // External dependencies should contain just specified target
        assertEquals(1, target.externalDependencies.size)
        assertEquals(nonExistingTargetID.name, target.externalDependencies.single())
    }

    @Test
    fun multilevelLinkCommandsTest() {
        val project = CIRProjectImpl(File(CIRProjectEntity.PROJECT_MULTILEVEL_LINK_COMMANDS_PATH))

        val someObjectTargetID = TargetID(
            name = File("src/test/resources/juliet/some_object.o").absolutePath, project = project
        )
        val someObjectTarget = project.findTarget(someObjectTargetID)
        assertTrue(someObjectTarget.externalDependencies.isEmpty())

        val supportTargetID = TargetID(
            name = File("src/test/resources/juliet/support/support.a").absolutePath, project = project
        )
        val supportTarget = project.findTarget(supportTargetID)

        assertEquals(2, supportTarget.externalDependencies.size)
        assertTrue(supportTarget.internalDependencies.isEmpty())

        val libraryTargetID = TargetID(
            name = File("src/test/resources/juliet/library.a").absolutePath, project = project
        )
        val libraryTarget = project.findTarget(libraryTargetID)

        assertEquals(1, libraryTarget.internalDependencies.size)
        assertEquals(2, libraryTarget.externalDependencies.size)
    }

    /** It is possible test
     *  One may try to analyse project which consist only from
     * CMake linking several libraries */
    @Test
    fun projectWithoutCirCommands() {
        val project = CIRProjectImpl(File(PROJECT_WITHOUT_CIR_COMMANDS_PATH))

        val libraryTargetID = TargetID(File("src/test/resources/juliet/library.a").absolutePath, project)
        val libraryTarget = project.findTarget(libraryTargetID)

        // some_object.o, io.o, std_thread.o
        assertEquals(3, libraryTarget.externalDependencies.size)
    }

    @Test
    fun projectWithoutLinkCommands() {
        val project = CIRProjectImpl(File(PROJECT_WITHOUT_CIR_COMMANDS_PATH))

        val libraryTargetID = TargetID(File("src/test/resources/juliet/library.a").absolutePath, project)
        val libraryTarget = project.findTarget(libraryTargetID)

        assertEquals(3, libraryTarget.externalDependencies.size)
    }

    @Test
    fun projectMalformedCirCommands() {
        val project = CIRProjectImpl(File(PROJECT_MALFORMED_CIR_COMMANDS_PATH))

        val libraryTargetID = TargetID(File("src/test/resources/juliet/library.a").absolutePath, project)
        val libraryTarget = project.findTarget(libraryTargetID)

        // some_object.o, io.o, std_thread.o
        assertEquals(3, libraryTarget.externalDependencies.size)
    }
}

private object CIRProjectEntity {
    const val PROJECT_JULIET_USE_AFTER_FREE_PATH = "src/test/resources/juliet/CWE416_Use_After_Free"
    const val PROJECT_MULTILEVEL_LINK_COMMANDS_PATH = "src/test/resources/projectsWithoutSources/multiLevelLinkCommands"
    const val PROJECT_MALFORMED_CIR_COMMANDS_PATH = "src/test/resources/projectsWithoutSources/malformedCirCommands"
    const val PROJECT_WITHOUT_CIR_COMMANDS_PATH = "src/test/resources/projectsWithoutSources/withoutCirCommands"
}