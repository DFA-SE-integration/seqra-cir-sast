import org.seqra.common.KotlinDependency
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    `maven-publish`
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

dependencies {
    api(project(":seqra-ir-api-common"))
    api(project(":seqra-ir-api-storage"))

    api(KotlinDependency.Libs.kotlinx_coroutines_core)
    implementation(KotlinDependency.Libs.kotlinx_serialization_json)

    api(Libs.jooq)
}

group = "org.seqra"
version = rootProperties.getProperty("seqraIrVersion")

publishing {
    publications {
        create<MavenPublication>("api-cir") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
