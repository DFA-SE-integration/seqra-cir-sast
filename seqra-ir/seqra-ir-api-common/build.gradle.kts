import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    `maven-publish`
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra"
version = rootProperties.getProperty("seqraIrVersion")

publishing {
    publications {
        create<MavenPublication>("api-common") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}