import org.gradle.api.Project
import org.seqra.common.SeqraDependency

object SeqraIrDependency : SeqraDependency {
    override val seqraRepository: String = "seqra-ir"
    override val versionProperty: String = "seqraIrVersion"

    val Project.seqra_ir_core_cir
        get() = propertyDep(
            group = "org.seqra",
            name = "seqra-ir-core-cir"
        )

    val Project.seqra_ir_api_common
        get() = propertyDep(
            group = "org.seqra",
            name = "seqra-ir-api-common"
        )

    val Project.seqra_ir_api_cir
        get() = propertyDep(
            group = "org.seqra",
            name = "seqra-ir-api-cir"
        )

    val Project.seqra_ir_api_storage
        get() = propertyDep(
            group = "org.seqra",
            name = "seqra-ir-api-storage"
        )

    val Project.seqra_ir_storage
        get() = propertyDep(
            group = "org.seqra",
            name = "seqra-ir-storage"
        )
}
