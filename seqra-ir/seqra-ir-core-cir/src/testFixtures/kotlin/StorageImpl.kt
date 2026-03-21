import kotlinx.coroutines.runBlocking
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.CIRFeature
import org.seqra.ir.api.cir.CIRPersistenceImplSettings
import org.seqra.ir.impl.CIRXodusKvErsSettings
import org.seqra.ir.impl.jacodb
import java.nio.file.Files

class WithRestoredDB(vararg features: CIRFeature<*, *>) {
    private val location by lazy {
        Files.createTempDirectory("jcdb-").toFile().absolutePath
    }

    private var currDb = jacodb {
        persistent(
            location = location, implSettings = implSettings
        )
        installFeatures(*features)
    }

    fun restartDb(): CIRDatabase {
        currDb = newDB {
            currDb.close()
        }
        return currDb
    }

    private val implSettings: CIRPersistenceImplSettings get() = CIRXodusKvErsSettings

    private fun newDB(before: () -> Unit = {}): CIRDatabase {
        before()
        return runBlocking {
            jacodb {
                persistent(location = location, implSettings = implSettings)
                installFeatures(*features.toTypedArray())
            }
        }
    }
}