package org.seqra.ir.api.cir

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class LinkCommands(private val entries: List<LinkCommandsEntry>) {
    companion object {
        fun fromFile(file: File): LinkCommands? {
            val bufferedReader = file.bufferedReader()

            try {
                val linkCommandsJson = Json.parseToJsonElement(bufferedReader.readText())
                val jsonArray = linkCommandsJson.jsonArray

                val linkCommands = arrayListOf<LinkCommandsEntry>()
                for (jsonObject in jsonArray) {
                    val argumentsJsonArray = jsonObject.jsonObject["arguments"]
                    val arguments = argumentsJsonArray?.jsonArray.orEmpty().map { it.jsonPrimitive.content }

                    val directory = jsonObject.jsonObject["directory"]?.jsonPrimitive?.content ?: ""

                    val inputFile = jsonObject.jsonObject["file"]?.jsonPrimitive?.content ?: ""

                    val filesJsonArray = jsonObject.jsonObject["files"]
                    val files = filesJsonArray?.jsonArray.orEmpty().map { File(it.jsonPrimitive.content).absolutePath }

                    val output =
                        jsonObject.jsonObject["output"]?.let { File(it.jsonPrimitive.content).absolutePath } ?: ""
                    linkCommands.add(LinkCommandsEntry(arguments, directory, inputFile, output, files))
                }
                return LinkCommands(linkCommands)
            } catch (e: Exception) {
                return null
            }
        }
    }


    private val entriesByTargets = hashMapOf<String, LinkCommandsEntry>()

    init {
        for (entry in entries) {
            assert(entry.outputFile() !in entriesByTargets)
            entriesByTargets[entry.outputFile()] = entry
        }
    }

    val targets = entries.map { it.outputFile() }.toSet()

    // Set of non-referenced targets which may be considered as final
    val finalTargets: Set<String>
        get() {
            val result = targets.toHashSet()
            for (entry in entries) {
                for (file in entry.dependsOn()) {
                    if (file in entriesByTargets) {
                        result.remove(file)
                    }
                }
            }
            return result
        }

    fun dependenciesOfTarget(target: String): Set<String> {
        val result = hashSetOf<String>()
        innerDependenciesOfTarget(target, result)
        return result
    }

    private fun innerDependenciesOfTarget(
        target: String, result: MutableSet<String>
    ) {
        for (dependency in entriesByTargets[target]?.dependsOn() ?: return) {
            if (result.add(dependency)) {
                innerDependenciesOfTarget(dependency, result)
            }
        }
    }

    operator fun iterator() = entries.iterator()
}

data class LinkCommandsEntry(
    private val arguments: List<String>,
    val directory: String,
    val file: String,
    private val output: String,
    private val files: List<String>
) {
    fun dependsOn(): List<String> = files
    fun outputFile(): String = output
}
