package org.seqra.ir.impl

import org.seqra.ir.api.cir.CIRCacheSegmentSettings
import org.seqra.ir.api.cir.CIRFeature
import org.seqra.ir.api.cir.CIRPersistenceImplSettings
import org.seqra.ir.api.cir.CIRPersistenceSettings
import org.seqra.ir.api.storage.ers.EmptyErsSettings
import org.seqra.ir.api.storage.ers.ErsSettings
import org.seqra.ir.impl.storage.ers.ERS_DATABASE_PERSISTENCE_SPI
import org.seqra.ir.impl.storage.ers.kv.KV_ERS_SPI
import org.seqra.ir.impl.storage.kv.xodus.XODUS_KEY_VALUE_STORAGE_SPI

class CIRSettings {
    /** persisted  */
    val persistenceId: String?
        get() = persistenceSettings.persistenceId

    var persistenceSettings: CIRPersistenceSettings = CIRPersistenceSettings()
    var cacheSettings = CIRCacheSettings()

    /** features to add */
    var features: List<CIRFeature<*, *>> = emptyList()
        private set

    fun persistenceImpl(persistenceImplSettings: CIRPersistenceImplSettings) = apply {
        persistenceSettings.implSettings = persistenceImplSettings
    }

    fun persistent(
        location: String,
        clearOnStart: Boolean = false,
        implSettings: CIRPersistenceImplSettings = CIRXodusKvErsSettings
    ) = apply {
        persistenceSettings.persistenceLocation = location
        persistenceSettings.persistenceClearOnStart = clearOnStart
        persistenceSettings.implSettings = implSettings
    }

    fun installFeatures(vararg feature: CIRFeature<*, *>) = apply {
        features = features + feature.toList()
    }
}

class CIRCacheSettings {
    var cacheSpiId: String? = null
    var functions: CIRCacheSegmentSettings = CIRCacheSegmentSettings()
    var types: CIRCacheSegmentSettings = CIRCacheSegmentSettings()
    var globals: CIRCacheSegmentSettings = CIRCacheSegmentSettings()
}

open class CIRErsSettings(
    val ersId: String, val ersSettings: ErsSettings = EmptyErsSettings
) : CIRPersistenceImplSettings {

    override val persistenceId: String
        get() = ERS_DATABASE_PERSISTENCE_SPI
}

object CIRXodusKvErsSettings : CIRErsSettings(KV_ERS_SPI, JIRKvErsSettings(XODUS_KEY_VALUE_STORAGE_SPI))
