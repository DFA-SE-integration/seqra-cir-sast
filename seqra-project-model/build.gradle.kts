import org.seqra.common.KotlinDependency
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

val kotlinVersion = "2.0.21"

dependencies {
    implementation(KotlinDependency.Libs.kotlinx_serialization_core)
    implementation(KotlinDependency.Libs.kaml)
}

val rootProperties = layout.projectDirectory.file("gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra.project"
version = rootProperties.getProperty("seqraProjectVersion")

publishing {
    publications {
        create<MavenPublication>("root") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}