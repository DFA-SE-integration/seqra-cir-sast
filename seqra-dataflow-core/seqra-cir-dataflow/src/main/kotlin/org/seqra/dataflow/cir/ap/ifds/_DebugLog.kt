package org.seqra.dataflow.cir.ap.ifds

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

internal object DebugLog {
    private val PATH: String? = System.getenv("SEQRA_DEBUG_LOG_PATH")?.takeIf { it.isNotBlank() }
    private val SESSION: String = "67e34f"
    private val LOCK = Any()

    fun isEnabled(): Boolean = PATH != null

    fun log(hypothesisId: String, message: String, data: Map<String, Any?>) {
        val path = PATH ?: return
        val now = System.currentTimeMillis()
        val sb = StringBuilder(256)
        sb.append('{')
            .append("\"sessionId\":\"").append(SESSION).append("\",")
            .append("\"timestamp\":").append(now).append(',')
            .append("\"hypothesisId\":").append(jsonString(hypothesisId)).append(',')
            .append("\"message\":").append(jsonString(message)).append(',')
            .append("\"data\":{")
        var first = true
        for ((k, v) in data) {
            if (!first) sb.append(',')
            first = false
            sb.append(jsonString(k)).append(':').append(jsonString(v?.toString()))
        }
        sb.append("}}\n")
        synchronized(LOCK) {
            runCatching {
                Files.write(
                    Paths.get(path),
                    sb.toString().toByteArray(Charsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                )
            }
        }
    }

    private fun jsonString(value: String?): String {
        if (value == null) return "null"
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append("\\u").append(String.format("%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
