import SeqraConfigurationDependency.seqraRulesCore
import SeqraIrDependency.seqra_ir_api_cir
import SeqraIrDependency.seqra_ir_api_storage
import SeqraIrDependency.seqra_ir_core_cir
import SeqraIrDependency.seqra_ir_storage
import SeqraProjectDependency.seqraProject
import SeqraUtilDependency.seqraUtilCli
import SeqraUtilDependency.seqraUtilCir
import org.seqra.common.JunitDependencies
import org.seqra.common.KotlinDependency
import org.jetbrains.kotlin.konan.properties.loadProperties

val rootProperties = layout.projectDirectory.file("gradle.properties").asFile.absolutePath.let { loadProperties(it) }
val cirDataflowVersion = rootProperties.getProperty("seqraBuildVersion")

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    shadowPlugin().apply(false)
}

dependencies {
    implementation(seqraUtilCir)
    implementation(seqraUtilCli)
    implementation(seqraProject)
    implementation(seqraRulesCore)

    implementation("org.seqra.seqra-dataflow-core:seqra-cir-dataflow:${cirDataflowVersion}")
    implementation(project(":seqra-cir-sast-dataflow"))

    implementation(seqra_ir_api_cir)
    implementation(seqra_ir_core_cir)
    implementation(seqra_ir_api_storage)
    implementation(seqra_ir_storage)

    implementation(KotlinDependency.Libs.kotlinx_serialization_json)
    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.kaml)

    implementation(Libs.sarif4k)
    implementation(Libs.clikt)
    implementation(Libs.zt_exec)

    testImplementation(Libs.mockk)
    testImplementation(JunitDependencies.Libs.junit_jupiter_params)
    implementation(Libs.logback)
    implementation(Libs.jdot)
}
