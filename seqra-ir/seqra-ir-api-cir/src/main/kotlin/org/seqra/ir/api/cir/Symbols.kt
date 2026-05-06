package org.seqra.ir.api.cir

import java.io.File

/**
 * Per-module byte snapshot read from a [CIRBitCodeLocation].
 *
 * [bytes] are the raw `MLIRModule` protobuf payload, suitable for being
 * handed to the source loader. [aliasBytes], when present, is the
 * `CIRModuleAliasData` sub-message extracted from the same parse pass so
 * that consumers do not have to re-read the file or re-parse the module
 * just to obtain the alias side-channel.
 */
class CIRModuleBlob(val bytes: ByteArray, val aliasBytes: ByteArray?)

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

    /**
     * Single per-module snapshot: protobuf bytes plus the optional alias
     * sub-blob extracted from the same parse pass. Implementations are
     * expected to perform at most one read/parse round-trip per call.
     */
    val moduleBlobs: Map<String, CIRModuleBlob>
}
