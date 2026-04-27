import org.gradle.api.Project
import org.seqra.common.SeqraDependency

object SeqraUtilDependency : SeqraDependency {
    override val seqraRepository: String = "seqra-utils"
    override val versionProperty: String = "seqraUtilVersion"

    val Project.seqraUtilCir: String
        get() = propertyDep(group = "org.seqra.utils", name = "seqra-cir-util")

    val Project.seqraUtilCli: String
            get() = propertyDep(group = "org.seqra.utils", name = "cli-util")
}
