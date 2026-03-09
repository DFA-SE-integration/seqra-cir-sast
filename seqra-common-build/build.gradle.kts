import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    `kotlin-dsl`
    `maven-publish`
}

val kotlinVersion = "2.0.21"

val rootProperties = layout.projectDirectory.file("gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra"
version = rootProperties.getProperty("seqraBuildVersion")

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
