import SeqraConfigurationDependency.seqraRulesCommon
import SeqraIrDependency.seqra_ir_api_common
import SeqraUtilDependency.seqraUtilCommon
import org.seqra.common.KotlinDependency
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    `maven-publish`
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra.seqra-dataflow-core"
version = rootProperties.getProperty("seqraBuildVersion")

dependencies {
    implementation(seqraUtilCommon)
    implementation(seqraRulesCommon)

    implementation(KotlinDependency.Libs.kotlinx_coroutines_core)
    implementation(KotlinDependency.Libs.kotlin_logging)

    api(seqra_ir_api_common)
    api(Libs.sarif4k)

    implementation(KotlinDependency.Libs.kotlinx_collections)

    implementation(Libs.fastutil)
}

publishing {
    publications {
        create<MavenPublication>("dataflow") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
