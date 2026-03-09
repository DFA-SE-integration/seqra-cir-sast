import org.seqra.common.JunitDependencies
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    `java-test-fixtures`
    `maven-publish`
}

dependencies {
    api(project(":seqra-ir-api-storage"))

    compileOnly(Libs.xodusEnvironment)
    compileOnly(Libs.lmdb_java)
    compileOnly(Libs.rocks_db)
    compileOnly(Libs.guava)
    compileOnly(Libs.xodusUtils)

    testImplementation(Libs.xodusEnvironment)
    testImplementation(Libs.lmdb_java)
    testImplementation(Libs.rocks_db)

    testFixturesImplementation(platform(JunitDependencies.Libs.junit_bom))
    testFixturesImplementation(JunitDependencies.Libs.junit_jupiter)
    testFixturesApi(Libs.xodusEnvironment)
    testFixturesApi(Libs.lmdb_java)
    testFixturesApi(Libs.rocks_db)
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra"
version = rootProperties.getProperty("seqraIrVersion")

publishing {
    publications {
        create<MavenPublication>("ir-storage") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}