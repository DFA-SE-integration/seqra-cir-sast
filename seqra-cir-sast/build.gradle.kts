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
    implementation(project(":seqra-cir-sast-se"))

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

tasks.withType<Test> {
    // Big real-world TUs (Wireshark) need far more heap than the Juliet fixtures; override with
    // SEQRA_TEST_XMX when even this is not enough. Test JVMs inherit the launching process env
    // by default, so SEQRA_BIGPROJ_*, CIR_KLEE_RESULTS_TSV, SEQRA_SE_MODE, CIRTAC_*/KLEE_BIN
    // (set by big_projects.sh / stats.sh) reach the analyzer without explicit forwarding.
    maxHeapSize = System.getenv("SEQRA_TEST_XMX")?.takeIf { it.isNotBlank() } ?: "12g"
    jvmArgs(
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    )
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}
