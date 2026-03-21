import SeqraConfigurationDependency.seqraRulesJvm
import SeqraUtilDependency.seqraUtilJvm
import org.seqra.common.KotlinDependency
import SeqraIrDependency.seqra_ir_api_jvm
import SeqraIrDependency.seqra_ir_api_storage
import SeqraIrDependency.seqra_ir_core
import SeqraIrDependency.seqra_ir_storage
import SeqraIrDependency.seqra_ir_approximations
import org.jetbrains.kotlin.konan.properties.loadProperties

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }
val jvmDataflowVersion = rootProperties.getProperty("seqraBuildVersion")

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation("org.seqra.seqra-dataflow-core:seqra-jvm-dataflow:${jvmDataflowVersion}")
    implementation(seqraRulesJvm)
    implementation(seqraUtilJvm)

    implementation(seqra_ir_api_jvm)
    implementation(seqra_ir_core)
    implementation(seqra_ir_approximations)
    implementation(seqra_ir_api_storage)
    implementation(seqra_ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)
}
