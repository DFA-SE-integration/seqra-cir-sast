import SeqraIrDependency.seqra_ir_api_cir
import org.jetbrains.kotlin.konan.properties.loadProperties
import org.seqra.common.KotlinDependency

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }
val cirDataflowVersion = rootProperties.getProperty("seqraBuildVersion")

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(seqra_ir_api_cir)
    implementation("org.seqra.seqra-dataflow-core:seqra-dataflow:${cirDataflowVersion}")

    implementation(KotlinDependency.Libs.kotlin_logging)
}

tasks.withType<Test> {
    jvmArgs = listOf(
        "-Xmx4g",
        "--add-opens",
        "java.base/java.nio=ALL-UNNAMED",
        "--add-opens",
        "java.base/sun.nio.ch=ALL-UNNAMED",
    )

    listOf("CIRTAC_COMPILER", "SEQRA_CWE416_FIXTURE_FILTER", "CIR_TAINT_DEBUG").forEach { name ->
        System.getenv(name)?.let { environment(name, it) }
    }

    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}
