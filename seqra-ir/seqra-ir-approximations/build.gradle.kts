import org.seqra.common.KotlinDependency
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    `maven-publish`
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

dependencies {
    implementation(project(":seqra-ir-api-jvm"))
    implementation(project(":seqra-ir-core"))
//    implementation(Libs.jooq)

    testImplementation(testFixtures(project(":seqra-ir-core")))
    testImplementation(testFixtures(project(":seqra-ir-storage")))
    testImplementation(KotlinDependency.Libs.kotlin_logging)
//    testRuntimeOnly(Libs.guava)
}

group = "org.seqra"
version = rootProperties.getProperty("seqraIrVersion")

publishing {
    publications {
        create<MavenPublication>("ir-approximations") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
