import org.seqra.common.KotlinDependency
import SeqraIrDependency.seqra_ir_api_cir
import SeqraIrDependency.seqra_ir_core_cir
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    `maven-publish`
}

dependencies {
    api(project(":common-util"))

    implementation(seqra_ir_api_cir)
    implementation(seqra_ir_core_cir)

    implementation(KotlinDependency.Libs.reflect)
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra.utils"
version = rootProperties.getProperty("seqraUtilVersion")

publishing {
    publications {
        create<MavenPublication>("seqra-cir-util") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
