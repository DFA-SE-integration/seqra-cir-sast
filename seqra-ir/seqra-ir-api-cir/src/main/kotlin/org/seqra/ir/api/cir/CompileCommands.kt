package org.seqra.ir.api.cir

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Paths

object CompileCommandsEntryEntity {
    const val ARGUMENTS = "arguments"
    const val DIRECTORY = "directory"
    const val FILE = "file"
    const val OUTPUT = "output"
    const val OBJECT = "object"
}

data class CompileCommands(val entries: List<CompileCommandsEntry>) {
    companion object {
        fun fromFile(file: File): CompileCommands? {
            val bufferedReader = file.bufferedReader()
            val linkCommandsJson = Json.parseToJsonElement(bufferedReader.readText())

            val entries = arrayListOf<CompileCommandsEntry>()

            try {
                for (linkCommandsEntryJson in linkCommandsJson.jsonArray) {
                    val linkCommandsEntryJsonObject = linkCommandsEntryJson.jsonObject

                    val arguments = arrayListOf<String>()
                    for (argumentEntryJson in linkCommandsEntryJsonObject[CompileCommandsEntryEntity.ARGUMENTS]?.jsonArray.orEmpty()) {
                        arguments.add(argumentEntryJson.jsonPrimitive.content)
                    }

                    val directory =
                        linkCommandsEntryJsonObject[CompileCommandsEntryEntity.DIRECTORY]?.jsonPrimitive?.content ?: ""
                    val inputFile =
                        linkCommandsEntryJsonObject[CompileCommandsEntryEntity.FILE]?.jsonPrimitive?.content ?: ""
                    val outputCir =
                        linkCommandsEntryJsonObject[CompileCommandsEntryEntity.OUTPUT]?.jsonPrimitive?.content ?: ""
                    val outputObject =
                        linkCommandsEntryJsonObject[CompileCommandsEntryEntity.OBJECT]?.jsonPrimitive?.content ?: ""

                    entries.add(
                        CompileCommandsEntry(
                            arguments = arguments,
                            directory = File(directory).absolutePath,
                            file = File(inputFile).absolutePath,
                            outputCir = File(outputCir).absolutePath,
                            objectFile = Paths.get(File(directory).absolutePath, outputObject).toString(),
                        )
                    )
                }
            } catch (e: Exception) {
                return null
            }

            return CompileCommands(entries)
        }
    }

    private val objectToCirMapping: Map<String, List<CompileCommandsEntry>> = entries.groupBy { it.objectFile }
    fun mapObjectToCir(objectFile: String): String? = objectToCirMapping[objectFile]?.firstOrNull()?.outputCir

    val targets = objectToCirMapping.keys
}

data class CompileCommandsEntry(
    val arguments: List<String>, val directory: String, val file: String, val outputCir: String, val objectFile: String
)