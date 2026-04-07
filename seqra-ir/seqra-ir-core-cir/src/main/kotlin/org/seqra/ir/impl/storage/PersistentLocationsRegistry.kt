package org.seqra.ir.impl.storage

import org.seqra.ir.api.cir.CIRBitCodeLocation
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.storage.ers.Transaction
import org.seqra.ir.api.storage.ers.getEntityOrNull
import org.seqra.ir.impl.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentHashMap.KeySetView

class PersistentLocationsRegistry(private val jcdb: CIRDatabaseImpl) : LocationsRegistry {
    private val persistence = jcdb.persistence

    override val actualLocations: List<PersistentByteCodeLocation>
        get() = persistence.read { txn ->
            txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE).map { entity ->
                PersistentByteCodeLocation(
                    jcdb, PersistentByteCodeLocationData.fromErsEntity(entity)
                )
            }.toList()
        }

    // TODO: for POSIX, stdlib and so on (?)
    //  How to provide sources? It is probably should
    //  be hidden from the user?
    override lateinit var runtimeLocations: List<RegisteredLocation>

    override val snapshots: KeySetView<LocationsRegistrySnapshot, Boolean> = ConcurrentHashMap.newKeySet()

    override fun cleanup(): CleanupResult = persistence.write { txn ->
        val deprecated = txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
            .filter { it.getLinks(BytecodeLocationEntity.UPDATED_LINK).isNotEmpty }
            .map { PersistentByteCodeLocationData.fromErsEntity(it) }
            .filterNot { data -> snapshots.any { it.ids.contains(data.id) } }
            .map { PersistentByteCodeLocation(jcdb, it) }.toList()
        deprecate(txn, deprecated)
        CleanupResult(deprecated)
    }

    override fun refresh(): RefreshResult {
        val deprecated = arrayListOf<PersistentByteCodeLocation>()
        val newLocations = arrayListOf<CIRBitCodeLocation>()
        val updated = hashMapOf<CIRBitCodeLocation, PersistentByteCodeLocation>()

        actualLocations.forEach { location ->
            val cirLocation = location.cirLocation
            when {
                cirLocation == null -> {
                    if (!location.hasReferences(snapshots)) {
                        deprecated.add(location)
                    }
                }

                cirLocation.isChanged() -> {
                    val refreshed = cirLocation.createRefreshed()
                    if (refreshed != null) {
                        newLocations.add(refreshed)
                    }
                    if (!location.hasReferences(snapshots)) {
                        deprecated.add(location)
                    } else {
                        updated[cirLocation] = location
                    }
                }
            }
        }
        val new = persistence.write { txn ->
            deprecate(txn, deprecated)
            newLocations.map { location ->
                val refreshed = txn.save(location)
                val toUpdate = updated[location]
                if (toUpdate != null) {
                    txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, toUpdate.id)?.let {
                        it.addLink(
                            BytecodeLocationEntity.UPDATED_LINK, txn.getEntityOrNull(
                                BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, refreshed.id
                            )!!
                        )
                        it[BytecodeLocationEntity.STATE] = LocationState.OUTDATED.ordinal
                    }
                }
                refreshed
            }
        }
        return RefreshResult(new = new)
    }

    override fun setup(runtimeLocations: List<CIRBitCodeLocation>): RegistrationResult {
        return registerIfNeeded(runtimeLocations).also {
            this.runtimeLocations = it.registered
        }
    }

    override fun registerIfNeeded(locations: List<CIRBitCodeLocation>): RegistrationResult {
        val uniqueLocations = locations.toSet()
        return persistence.write { txn ->
            val result = arrayListOf<RegisteredLocation>()
            val toAdd = arrayListOf<CIRBitCodeLocation>()
            val fsIds = uniqueLocations.map { it.fileSystemId }
            val existed = fsIds.flatMap { fsId ->
                txn.find(
                    type = BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
                    propertyName = BytecodeLocationEntity.FILE_SYSTEM_ID,
                    value = fsId
                )
            }.map { entity -> PersistentByteCodeLocationData.fromErsEntity(entity) }.associateBy { it.fileSystemId }

            uniqueLocations.forEach {
                val found = existed[it.fileSystemId]
                if (found == null) {
                    toAdd += it
                } else {
                    result += PersistentByteCodeLocation(jcdb, found, it)
                }
            }
            val records = toAdd.map { location ->
                val entity = txn.newEntity(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                entity[BytecodeLocationEntity.PATH] = location.path
                entity[BytecodeLocationEntity.FILE_SYSTEM_ID] = location.fileSystemId
                entity[BytecodeLocationEntity.STATE] = LocationState.INITIAL.ordinal
                entity.id.instanceId to location
            }
            val added = records.map {
                PersistentByteCodeLocation(
                    jcdb.persistence, it.first, null, it.second
                )
            }
            RegistrationResult(result + added, added)
        }
    }

    override fun afterProcessing(locations: List<RegisteredLocation>) {
        val ids = locations.map { it.id }
        persistence.write { txn ->
            ids.forEach { id ->
                val entity = txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, id)
                entity?.set(BytecodeLocationEntity.STATE, LocationState.PROCESSED.ordinal)
            }
        }
//        jcdb.featuresRegistry.broadcast(JcInternalSignal.AfterIndexing)
    }

    override fun newSnapshot(classpathSetLocations: List<RegisteredLocation>): LocationsRegistrySnapshot {
        return LocationsRegistrySnapshot(this, classpathSetLocations).also {
            snapshots.add(it)
        }
    }

    private fun deprecate(txn: Transaction, locations: List<RegisteredLocation>) {
//        locations.forEach {
//            jcdb.featuresRegistry.broadcast(JcInternalSignal.LocationRemoved(it))
//        }
        val locationIds = locations.map { it.id }.toSet()
        txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE).filter { it.id.instanceId in locationIds }
            .forEach { it.delete() }
    }

    private fun Transaction.save(location: CIRBitCodeLocation) =
        PersistentByteCodeLocation(jcdb, location.findOrNew(this), location)

    private fun CIRBitCodeLocation.findOrNew(txn: Transaction): PersistentByteCodeLocationData {
        val existing = findOrNull(txn)
        if (existing != null) {
            return existing
        }
        val entity = txn.find(
            type = BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
            propertyName = BytecodeLocationEntity.PATH,
            value = path
        ).firstOrNull() ?: txn.newEntity(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
        entity[BytecodeLocationEntity.PATH] = path
        entity[BytecodeLocationEntity.FILE_SYSTEM_ID] = fileSystemId
        return PersistentByteCodeLocationData.fromErsEntity(entity)
    }

    private fun CIRBitCodeLocation.findOrNull(txn: Transaction): PersistentByteCodeLocationData? = txn.find(
        type = BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
        propertyName = BytecodeLocationEntity.PATH,
        value = path
    ).firstOrNull { it.get<String>(BytecodeLocationEntity.FILE_SYSTEM_ID) == fileSystemId }?.let {
        PersistentByteCodeLocationData.fromErsEntity(it)
    }

    override fun close(snapshot: LocationsRegistrySnapshot) {
        snapshots.remove(snapshot)
        cleanup()
    }

    override fun close() {
//        jcdb.featuresRegistry.broadcast(JcInternalSignal.Closed)
        runtimeLocations = emptyList()
    }
}

object BytecodeLocationEntity {
    const val BYTECODE_LOCATION_ENTITY_TYPE = "ByteCodeLocation"
    const val STATE = "state"
    const val IS_RUNTIME = "isRuntime"
    const val PATH = "path"
    const val FILE_SYSTEM_ID = "fileSystemId"
    const val UPDATED_LINK = "updatedLink"
}