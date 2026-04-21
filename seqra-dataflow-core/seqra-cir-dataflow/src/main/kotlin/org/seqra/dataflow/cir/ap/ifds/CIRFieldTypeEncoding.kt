package org.seqra.dataflow.cir.ap.ifds

import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object CIRFieldTypeEncoding {
    private const val Prefix = "@cir-type:v1:"

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(typeId: MLIRTypeID): String {
        val modulePart = encoder.encodeToString(typeId.moduleID.id.toByteArray(UTF_8))
        val typePart = encoder.encodeToString(typeId.id.toByteArray(UTF_8))
        return "$Prefix$modulePart:$typePart"
    }

    fun decodeOrNull(value: String): MLIRTypeID? {
        if (!value.startsWith(Prefix)) return null

        val payload = value.removePrefix(Prefix)
        val separatorIdx = payload.indexOf(':')
        if (separatorIdx <= 0 || separatorIdx == payload.lastIndex) return null

        val modulePart = payload.substring(0, separatorIdx)
        val typePart = payload.substring(separatorIdx + 1)

        val moduleId = modulePart.decodeBase64UrlOrNull() ?: return null
        val typeId = typePart.decodeBase64UrlOrNull() ?: return null

        return MLIRTypeID(
            moduleID = MLIRModuleID(moduleId),
            id = typeId,
        )
    }

    fun isEncoded(value: String): Boolean =
        value.startsWith(Prefix)

    private fun String.decodeBase64UrlOrNull(): String? {
        return runCatching {
            String(decoder.decode(this), UTF_8)
        }.getOrNull()
    }
}
