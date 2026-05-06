package org.seqra.ir.impl.sources

import org.seqra.ir.api.cir.CIRBitCodeLocation
import org.seqra.ir.api.cir.CIRModuleBlob
import org.seqra.ir.impl.grpc.Model.MLIRModule
import org.seqra.ir.impl.shaHash
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class CIRLocation(private val file: File) : CIRBitCodeLocation {
    override val cirFile: File
        get() = file
    override val fileSystemId by lazy { fileChecksum }
    override val path: String
        get() = cirFile.path

    override fun isChanged() = fileSystemId != fileChecksum

    override fun createRefreshed() = CIRLocation(file)

    override val moduleBlobs: Map<String, CIRModuleBlob>
        get() {
            val bytes = Files.readAllBytes(Paths.get(file.path))
            val parsed = MLIRModule.parseFrom(bytes)
            val aliasBytes = if (parsed.hasAliasData()) parsed.aliasData.toByteArray() else null
            return mapOf(file.path to CIRModuleBlob(bytes, aliasBytes))
        }

    private val fileChecksum: String
        get() {
            return cirFile.let {
                it.absolutePath + it.lastModified() + it.length()
            }.shaHash
        }
}
