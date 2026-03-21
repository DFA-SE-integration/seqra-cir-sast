package org.seqra.ir.impl.sources

import org.seqra.ir.api.cir.CIRBitCodeLocation
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

    override val modules: Map<String, ByteArray>
        get() = mapOf(file.path to Files.newInputStream(Paths.get(file.path)).readBytes())

    private val fileChecksum: String
        get() {
            return cirFile.let {
                it.absolutePath + it.lastModified() + it.length()
            }.shaHash
        }
}
