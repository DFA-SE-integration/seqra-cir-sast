import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    `maven-publish`
}

val kotlinVersion = "2.1.0"

val rootProperties = layout.projectDirectory.file("gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra"
version = rootProperties.getProperty("seqraBuildVersion")

publishing {
    publications {
        create<MavenPublication>("root") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
