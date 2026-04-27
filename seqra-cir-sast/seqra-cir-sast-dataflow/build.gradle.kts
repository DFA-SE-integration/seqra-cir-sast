import SeqraConfigurationDependency.seqraRulesCore
import SeqraIrDependency.seqra_ir_api_cir
import SeqraIrDependency.seqra_ir_api_storage
import SeqraIrDependency.seqra_ir_core_cir
import SeqraIrDependency.seqra_ir_storage
import SeqraUtilDependency.seqraUtilCir
import org.jetbrains.kotlin.konan.properties.loadProperties
import org.seqra.common.JunitDependencies
import org.seqra.common.KotlinDependency

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }
val cirDataflowVersion = rootProperties.getProperty("seqraBuildVersion")

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    implementation(seqraRulesCore)
    implementation(seqraUtilCir)

    implementation(seqra_ir_api_cir)
    implementation(seqra_ir_core_cir)
    implementation(seqra_ir_api_storage)
    implementation(seqra_ir_storage)

    implementation("org.seqra.seqra-dataflow-core:seqra-dataflow:${cirDataflowVersion}")
    implementation("org.seqra.seqra-dataflow-core:seqra-cir-dataflow:${cirDataflowVersion}")

    implementation(KotlinDependency.Libs.kotlin_logging)

    testImplementation(JunitDependencies.Libs.junit_jupiter_params)
    testImplementation(Libs.mockk)
    testRuntimeOnly(Libs.logback)
}

tasks.withType<Test> {
    jvmArgs = listOf("-Xmx4g")

    listOf("CIRTAC_COMPILER", "SEQRA_CWE416_FIXTURE_FILTER", "CIR_TAINT_DEBUG").forEach { name ->
        System.getenv(name)?.let { environment(name, it) }
    }

    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}
