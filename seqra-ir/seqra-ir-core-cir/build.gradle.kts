import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import org.seqra.common.KotlinDependency
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    id("kotlin-conventions")
    id("com.google.protobuf") version "0.9.4"
    `java-test-fixtures`
    `maven-publish`
}

dependencies {
    api(project(":seqra-ir-api-cir"))
    api(project(":seqra-ir-storage"))

    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependencyExt.Libs.kotlin_metadata_jvm)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:${KotlinDependency.Versions.kotlinx_serialization}")
    implementation(Libs.jdot)
    implementation(Libs.guava)
    implementation(Libs.sqlite)
    implementation(Libs.hikaricp)
    implementation(Libs.xodusUtils)
    implementation(TestDependencies.Libs.slf4j_simple)

    implementation("com.google.protobuf:protobuf-java:4.28.3")

    testImplementation(project(":seqra-ir-storage"))
    testImplementation(kotlin("test"))
    api(Libs.xodusEnvironment)

    testFixturesImplementation("com.google.protobuf:protobuf-java:4.28.3")

    testFixturesImplementation(KotlinDependency.Libs.kotlinx_serialization_core)
    testFixturesImplementation(KotlinDependency.Libs.kotlinx_serialization_json)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.3"
    }
    generateProtoTasks {
        ofSourceSet("main")
    }
}

tasks.matching { it.name == "sourcesJar" || it.name == "kotlinSourcesJar" }
    .configureEach { dependsOn("generateProto") }

tasks {
    register("compileCirs") {
        group = "build"
        description = "Generates test resources from .c and .cpp files using clang."
        doLast {
            println("Generating .cir files from .c and .cpp files using clang...")
            val startTime = System.currentTimeMillis()

            val evnVarClangCompiler = "CLANG_PATH"
            val clangCompilerPath = rootDir.resolve(
                System.getenv(evnVarClangCompiler)
                    ?: throw FileNotFoundException("$evnVarClangCompiler does not set. Aborting...")
            )
            if (!clangCompilerPath.exists()) {
                throw FileNotFoundException(
                    "Path to clang compiler does not exist: '${clangCompilerPath.absolutePath}'. Did you forget to set the '$evnVarClangCompiler' environment variable? Current value is '${
                        System.getenv(
                            evnVarClangCompiler
                        )
                    }', current dir is '${File("").absolutePath}'."
                )
            }

            val resources = projectDir.resolve("src/test/resources")

            val allResources = resources.walk().toSet()
            val compiledResources = allResources.filter { file -> file.extension == "cir" }.toSet()

            allResources.forEach { file ->
                if (file.extension == "c" || file.extension == "cpp") {
                    val cir = Paths.get(file.parent, file.nameWithoutExtension + ".cir").toFile()

                    // .cir files have unique ids, so we do not want to recompile them
                    if (cir in compiledResources) {
                        println("$cir is already compiled")
                        return@forEach
                    }

                    val cmd: List<String> = listOf(
                        clangCompilerPath.toString(),
                        "-S",
                        "-Xclang",
                        "-emit-cir-flat",
                        file.absolutePath,
                        "-o",
                        cir.absolutePath,
                    )

                    println("Running: '${cmd.joinToString(" ")}'")
                    val process = ProcessBuilder(cmd).directory(resources).start()
                    val ok = process.waitFor(1, TimeUnit.MINUTES)

                    val stdout = process.inputStream.bufferedReader().readText().trim()
                    if (stdout.isNotBlank()) {
                        println("[STDOUT]:\n--------\n$stdout\n--------")
                    }
                    val stderr = process.errorStream.bufferedReader().readText().trim()
                    if (stderr.isNotBlank()) {
                        println("[STDERR]:\n--------\n$stderr\n--------")
                    }

                    if (!ok) {
                        println("Timeout!")
                        process.destroy()
                    }
                }
            }

            println("Done generating test resources in %.1fs".format((System.currentTimeMillis() - startTime) / 1000.0))
        }
    }

    register("compileProtocirs") {
        group = "build"
        description = "Generates .protocir sources from .cir files using cir-tac."
        doLast {
            val startTime = System.currentTimeMillis()

            val envVarCirTacPath = "CIRTAC_COMPILER"
            val cirTacPath = System.getenv(envVarCirTacPath) ?: run {
                logger.warn("$envVarCirTacPath is not set")
                ""
            }

            val cirTacCompilerFile = Paths.get(cirTacPath).toFile().let { envCompiler ->
                if (envCompiler.exists()) {
                    return@let envCompiler
                }

                logger.warn("Compiler from the $envVarCirTacPath was not found. Uploading compiler from the repository...")

                val envVarCirTacArtifactLink = "CIRTAC_LINK"
                val artifactUrl = System.getenv(envVarCirTacArtifactLink) ?: run {
                    val archName = System.getProperty("os.arch", "unknown").lowercase()

                    val defaultLink = when (archName) {
                        "amd64", "x86_64" -> "https://github.com/explyt/cir-tac/releases/download/v2.5.0/cir-tac-ubuntu-latest-X64.tar.gz"
                        "arm64", "aarch64" -> "https://github.com/explyt/cir-tac/releases/download/v2.5.0/cir-tac-macos-latest-ARM64.tar.gz"
                        else -> null
                    }

                    if (defaultLink == null) {
                        logger.warn("$envVarCirTacArtifactLink is not set. Attempted to use default, but failed to determine OS architecture")
                        error("Unknown architecture $archName")
                    } else {
                        logger.warn("$envVarCirTacArtifactLink is not set. Using default $defaultLink")
                    }

                    defaultLink
                }
                val compilerDirectory = Files.createTempDirectory("cir-tac-").toFile()

                val downloaderProcess = ProcessBuilder(
                    listOf(
                        "/bin/sh", "-c",
                        "wget -qO- $artifactUrl | gunzip | tar xvf - -C ${compilerDirectory.absolutePath}",
                    )
                ).start()
                val ok = downloaderProcess.waitFor(5, TimeUnit.MINUTES)
                val stderr = downloaderProcess.errorStream.bufferedReader().readText().trim()
                if (stderr.isNotBlank()) {
                    println("[STDERR]:\n--------\n$stderr\n--------")
                }
                if (!ok) {
                    println("Timeout!")
                    downloaderProcess.destroy()
                }

                compilerDirectory.resolve("cir-ser-proto").resolve("cir-ser-proto").also {
                    if (!it.exists() || !it.setExecutable(true)) {
                        throw RuntimeException("Failed to download cir-tac compiler. Aborting...")
                    }
                }
            }

            val resources = projectDir.resolve("src/test/resources")

            resources.walk().forEach { file ->
                if (file.extension == "cir") {
                    val protocir = Paths.get(file.parent, file.nameWithoutExtension + ".protocir").toFile()
                    val aliasPb = Paths.get(file.parent, file.nameWithoutExtension + ".alias.pb").toFile()

                    val cmd: List<String> = listOf(
                        cirTacCompilerFile.absolutePath,
                        file.absolutePath,
                        "--emit-alias=${aliasPb.absolutePath}",
                    )

                    println("Running: '${cmd.joinToString(" ")}'")
                    val process = ProcessBuilder(cmd).directory(resources).redirectOutput(protocir).start()
                    val ok = process.waitFor(1, TimeUnit.MINUTES)

                    val stderr = process.errorStream.bufferedReader().readText().trim()
                    if (stderr.isNotBlank()) {
                        println("[STDERR]:\n--------\n$stderr\n--------")
                    }

                    if (!ok) {
                        println("Timeout!")
                        process.destroy()
                    }
                }
            }

            println("Done generating test resources in %.1fs".format((System.currentTimeMillis() - startTime) / 1000.0))
        }
    }
}

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

group = "org.seqra"
version = rootProperties.getProperty("seqraIrVersion")

publishing {
    publications {
        create<MavenPublication>("ir-core-cir") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}