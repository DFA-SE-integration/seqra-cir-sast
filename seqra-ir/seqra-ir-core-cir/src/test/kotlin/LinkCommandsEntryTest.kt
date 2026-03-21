import org.seqra.ir.api.cir.LinkCommands
import org.seqra.ir.api.cir.LinkCommandsEntry
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LinkCommandsEntryTest {
    @Test
    fun parseLinkCommandsTest() {
        val pathToLinkCommands = "src/test/resources/link_commands.json"
        val file = File(pathToLinkCommands)

        assert(file.exists())

        val linkCommands = LinkCommands.fromFile(file)
        assertNotNull(linkCommands)
        assertEquals(1, linkCommands.finalTargets.size)
    }
}