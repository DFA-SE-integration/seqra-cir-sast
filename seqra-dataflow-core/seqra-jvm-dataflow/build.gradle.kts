import SeqraConfigurationDependency.seqraRulesCore
import SeqraIrDependency.seqra_ir_api_jvm
import SeqraIrDependency.seqra_ir_api_storage
import SeqraIrDependency.seqra_ir_core
import SeqraIrDependency.seqra_ir_storage
import SeqraUtilDependency.seqraUtilCommon
import SeqraUtilDependency.seqraUtilJvm
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
    api(project(":seqra-dataflow"))
    implementation(seqraUtilCommon)
    implementation(seqraUtilJvm)
    implementation(seqraRulesCore)

    implementation(seqra_ir_api_jvm)
    implementation(seqra_ir_core)
    implementation(seqra_ir_api_storage)
    implementation(seqra_ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.reflect)

    implementation(Libs.fastutil)

    implementation(Libs.sarif4k)
}


publishing {
    publications {
        create<MavenPublication>("jvm-dataflow") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
