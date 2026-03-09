import SeqraIrDependency.seqra_ir_api_jvm
import SeqraIrDependency.seqra_ir_core
import org.seqra.common.KotlinDependency
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    `maven-publish`
}

dependencies {
    api(project(":configuration-rules-common"))

    implementation(seqra_ir_api_jvm)
    implementation(seqra_ir_core)

    implementation(KotlinDependency.Libs.kotlinx_serialization_core)
    implementation(KotlinDependency.Libs.kaml)
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra.configuration"
version = rootProperties.getProperty("seqraConfigRulesVersion")

publishing {
    publications {
        create<MavenPublication>("rules-jvm") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
