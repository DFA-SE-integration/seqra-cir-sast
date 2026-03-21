package builders

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

class CIRCommandsBuilder(val directory: Path) {
    private val elements = mutableListOf<JsonElement>()

    /*
    "directory": "src/test/resources/doubleModuleWithLinkCommandsTypes",
    "file": "helperModule.c",
    "output": "src/test/resources/doubleModuleWithLinkCommandsTypes/helperModule.cir",
    "object": "helperModule.o"
     */

    data class CompilationTarget(private val outputPath: Path, private val objectFileName: String) {
        fun toJsonObject(directoryPath: Path) = JsonObject(
            mutableMapOf(
                "directory" to JsonPrimitive(directoryPath.pathString),
                "output" to JsonPrimitive(outputPath.pathString),
                "object" to JsonPrimitive(objectFileName)
            )
        )
    }

    fun append(target: CompilationTarget) = apply {
        elements.add(target.toJsonObject(directory))
    }

    fun build(): String = JsonArray(elements).toString()
    fun buildToFile(file: File = directory.resolve("cir_commands.json").toFile()): File {
        if (!file.exists()) {
            file.createNewFile()
        }

        file.bufferedWriter().use { writer ->
            writer.write(build())
            writer.flush()
        }

        return file
    }
}

class LinkCommandsBuilder(val directory: Path) {
    /*
        "files": [
          "src/test/resources/doubleModuleWithLinkCommandsTypes/mainModule.o",
          "src/test/resources/doubleModuleWithLinkCommandsTypes/helperModule.o"
        ],
        "directory": "src/test/resources/doubleModuleWithLinkCommandsTypes",
        "output": "src/test/resources/doubleModuleWithLinkCommandsTypes/app.o"
         */
    private val elements = mutableListOf<JsonElement>()

    data class LinkTarget(val files: List<File>, private val outputFile: File) {
        private fun List<File>.toJsonArray() = JsonArray(map { file -> JsonPrimitive(file.absolutePath) })

        fun toJsonObject(directory: Path) = JsonObject(
            mutableMapOf(
                "files" to files.toJsonArray(),
                "directory" to JsonPrimitive(directory.pathString),
                "output" to JsonPrimitive(outputFile.absolutePath),
            )
        )
    }

    fun append(target: LinkTarget) = apply {
        elements.add(target.toJsonObject(directory))
    }

    fun build(): String = JsonArray(elements).toString()
    fun buildToFile(file: File = directory.resolve("link_commands.json").toFile()) {
        if (!file.exists()) {
            file.createNewFile()
        }

        file.bufferedWriter().use { writer ->
            writer.write(build())
            writer.flush()
        }
    }
}

