import SeqraConfigurationDependency.seqraRulesCore
import SeqraIrDependency.seqra_ir_api_cir
import SeqraIrDependency.seqra_ir_core_cir
import SeqraUtilDependency.seqraUtilCommon
import SeqraUtilDependency.seqraUtilCir
import org.seqra.common.KotlinDependency
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    `maven-publish`
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra.seqra-dataflow-core"
version = rootProperties.getProperty("seqraBuildVersion")

dependencies {
    api(project(":seqra-dataflow"))
    implementation(seqraUtilCommon)
    implementation(seqraUtilCir)
    implementation(seqraRulesCore)

    implementation(seqra_ir_api_cir)
    testImplementation(seqra_ir_core_cir)

    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.reflect)
}

publishing {
    publications {
        create<MavenPublication>("cir-dataflow") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
