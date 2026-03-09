import org.seqra.common.KotlinDependency
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    `maven-publish`
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

dependencies {
    api(KotlinDependency.Libs.kotlinx_collections)
}

group = "org.seqra"
version = rootProperties.getProperty("seqraIrVersion")

publishing {
    publications {
        create<MavenPublication>("api-storage") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}