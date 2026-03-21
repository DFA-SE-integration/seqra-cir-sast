package org.seqra.ir.api.cir

import org.seqra.ir.api.caches.ValueStoreType
import java.time.Duration

class CIRPersistenceSettings {
    val persistenceId: String? get() = implSettings?.persistenceId
    var persistenceLocation: String? = null
    var persistenceClearOnStart: Boolean? = null
    var implSettings: CIRPersistenceImplSettings? = null
}

interface CIRPersistenceImplSettings {
    val persistenceId: String
}

data class CIRCacheSegmentSettings(
    val valueStoreType: ValueStoreType = ValueStoreType.STRONG,
    val maxSize: Long = 10_000,
    val expiration: Duration = Duration.ofMinutes(1)
)
