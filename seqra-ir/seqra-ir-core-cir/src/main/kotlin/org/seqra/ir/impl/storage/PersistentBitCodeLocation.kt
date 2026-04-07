package org.seqra.ir.impl.storage

import org.seqra.ir.api.cir.CIRBitCodeLocation
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.CIRDatabasePersistence
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.storage.ers.Entity
import org.seqra.ir.api.storage.ers.getEntityOrNull
import org.seqra.ir.impl.sources.asByteCodeLocation
import java.io.File

data class PersistentByteCodeLocationData(
    val id: Long,
    val path: String,
    val fileSystemId: String
) {
    companion object {
        fun fromErsEntity(entity: Entity) = PersistentByteCodeLocationData(
            id = entity.id.instanceId,
            path = entity[BytecodeLocationEntity.PATH]!!,
            fileSystemId = entity[BytecodeLocationEntity.FILE_SYSTEM_ID]!!
        )
    }
}

class PersistentByteCodeLocation(
    private val persistence: CIRDatabasePersistence,
    override val id: Long,
    private val cachedData: PersistentByteCodeLocationData? = null,
    private val cachedLocation: CIRBitCodeLocation? = null
) : RegisteredLocation {

    constructor(
        db: CIRDatabase,
        data: PersistentByteCodeLocationData,
        location: CIRBitCodeLocation? = null
    ) : this(
        db.persistence,
        data.id,
        data,
        location
    )

    val data by lazy {
        cachedData ?: persistence.read { txn ->
            val entity = txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, id)!!
            PersistentByteCodeLocationData.fromErsEntity(entity)
        }
    }

    override val cirLocation: CIRBitCodeLocation?
        get() {
            return cachedLocation ?: data.toCIRLocation()
        }

    override val path: String
        get() = cirLocation!!.path

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegisteredLocation

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    private fun PersistentByteCodeLocationData.toCIRLocation(): CIRBitCodeLocation? {
        try {
            val newOne = File(path).asByteCodeLocation().singleOrNull()
            if (newOne?.fileSystemId != fileSystemId) {
                return null
            }
            return newOne
        } catch (e: Exception) {
            return null
        }
    }
}

