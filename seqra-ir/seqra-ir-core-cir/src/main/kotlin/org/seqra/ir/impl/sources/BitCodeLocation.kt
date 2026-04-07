package org.seqra.ir.impl.sources

import mu.KLogging
import org.seqra.ir.api.cir.CIRBitCodeLocation
import java.io.File
import java.nio.file.Paths

val logger = object : KLogging() {}.logger

fun File.asByteCodeLocation(): Collection<CIRBitCodeLocation> {
    if (!exists()) {
        throw IllegalArgumentException("file $absolutePath doesn't exist")
    }
    if (isFile && (name.endsWith(".cir") || name.endsWith(".protocir"))) {
        val bytecodeFile =
            if (name.endsWith(".cir")) Paths.get(parent, "$nameWithoutExtension.protocir").toFile() else this
        return mutableSetOf(bytecodeFile).map { CIRLocation(it) }
    } else if (isDirectory) {
        val result = hashSetOf<CIRBitCodeLocation>()
        walk().forEach {
            if (it.isFile && (it.name.endsWith(".cir") || it.name.endsWith(".protocir"))) {
                result.addAll(it.asByteCodeLocation())
            }
        }
        return result
    }
    error("$absolutePath is not a .cir file")
}

fun Collection<File>.filterExisting(): List<File> {
    val nonExistingFiles = mutableListOf<File>()

    val existingFiles = filter { file ->
        file.exists().also {
            if (!it) {
                nonExistingFiles.add(file)
            }
        }
    }

    if (nonExistingFiles.isNotEmpty()) {
        val filesList = nonExistingFiles.joinToString(separator = "") { "\n\t- $it" }
        logger.warn("The following files$filesList\ndon't exist. Make sure there is no mistake")
    }

    return existingFiles
}

fun File.findLinkCommands(): List<File> = walk().filter {
    it.name == "link_commands.json"
}.toList()

fun File.findCompileCommands(): List<File> = walk().filter {
    it.name == "cir_commands.json"
}.toList()
