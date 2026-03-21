import org.seqra.ir.impl.CIRSettings
import org.seqra.ir.impl.CIRXodusKvErsSettings
import org.seqra.ir.impl.jacodb
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class ModuleTest {
    private val settings = CIRSettings().apply {
        persistenceImpl(CIRXodusKvErsSettings)
    }

    // @Test
    fun loadGlobalCtors() {
        val db = jacodb(settings)

        val sourceFile = File("src/test/resources/globalCtors/test.protocir")
        db.loadFiles(sourceFile)

        val cp = db.classpath(listOf(sourceFile))
        val globalCtors = cp.getGlobalConstructors()

        assertEquals(1, globalCtors.size)
        for (ctor in globalCtors) {
            val ctorFunction = cp.findFunctionOrNull(ctor)!!
            println(ctorFunction)
        }
    }
}