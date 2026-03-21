package org.seqra.ir.api.cir

import java.io.File

interface CIRBitCodeLocation {
    val cirFile: File
    val fileSystemId: String //id based on from file system

    /** url for bytecode location */
    val path: String

    /**
     * this operation may involve file-system operations and may be expensive
     *
     * @returns true if file-system has changes not reflected in current `location`
     */
    fun isChanged(): Boolean

    /**
     * @return new refreshed version of this `location`
     */
    fun createRefreshed(): CIRBitCodeLocation?

    val modules: Map<String, ByteArray>
}
