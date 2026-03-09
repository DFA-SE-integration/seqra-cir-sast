import org.seqra.common.KotlinDependency
import SeqraIrDependency.seqra_ir_api_jvm
import SeqraIrDependency.seqra_ir_approximations
import SeqraIrDependency.seqra_ir_core
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    `maven-publish`
}

dependencies {
    api(project(":common-util"))

    implementation(seqra_ir_api_jvm)
    implementation(seqra_ir_core)
    implementation(seqra_ir_approximations)

    implementation(KotlinDependency.Libs.reflect)
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra.utils"
version = rootProperties.getProperty("seqraUtilVersion")

publishing {
    publications {
        create<MavenPublication>("seqra-jvm-util") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
