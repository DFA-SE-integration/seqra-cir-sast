import SeqraIrDependency.seqra_ir_api_common
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    `maven-publish`
}

dependencies {
    implementation(seqra_ir_api_common)
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra.configuration"
version = rootProperties.getProperty("seqraConfigRulesVersion")

publishing {
    publications {
        create<MavenPublication>("rules-common") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
