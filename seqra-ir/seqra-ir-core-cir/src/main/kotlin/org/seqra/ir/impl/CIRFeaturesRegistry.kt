package org.seqra.ir.impl

import kotlinx.collections.immutable.toPersistentList
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.CIRFeature
import org.seqra.ir.api.cir.CIRModuleSource
import org.seqra.ir.api.cir.RegisteredLocation
import java.io.Closeable

internal sealed class CIRInternalSignal {
    class BeforeIndexing(val clearOnStart: Boolean) : CIRInternalSignal()
    object AfterIndexing : CIRInternalSignal()
    object Drop : CIRInternalSignal()
    object Closed : CIRInternalSignal()
    class LocationRemoved(val location: RegisteredLocation) : CIRInternalSignal()

    fun asCIRSignal(db: CIRDatabase): org.seqra.ir.api.cir.CIRSignal = when (this) {
        is BeforeIndexing -> org.seqra.ir.api.cir.CIRSignal.BeforeIndexing(db, clearOnStart)
        is AfterIndexing -> org.seqra.ir.api.cir.CIRSignal.AfterIndexing(db)
        is LocationRemoved -> org.seqra.ir.api.cir.CIRSignal.LocationRemoved(db, location)
        is Drop -> org.seqra.ir.api.cir.CIRSignal.Drop(db)
        is Closed -> org.seqra.ir.api.cir.CIRSignal.Closed(db)
    }
}

internal class CIRFeaturesRegistry(features: List<CIRFeature<*, *>>) : Closeable {

    val features = features.toPersistentList()

    private lateinit var db: CIRDatabase

    fun bind(db: CIRDatabase) {
        this.db = db
    }

    fun index(location: RegisteredLocation, modules: List<CIRModuleSource>) {
        features.forEach { feature ->
            val indexer = feature.newIndexer(db, location)
            modules.forEach { indexer.index(it.node) }
            indexer.flush()
        }
    }

    fun broadcast(signal: CIRInternalSignal) {
        features.forEach { it.onSignal(signal.asCIRSignal(db)) }
    }

    override fun close() {
    }
}
