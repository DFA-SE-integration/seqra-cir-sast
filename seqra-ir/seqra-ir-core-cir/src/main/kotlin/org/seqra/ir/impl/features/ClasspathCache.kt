package org.seqra.ir.impl.features

import org.seqra.ir.api.caches.PluggableCache
import org.seqra.ir.api.caches.PluggableCacheProvider
import org.seqra.ir.api.cir.CIRCacheSegmentSettings
import org.seqra.ir.api.cir.CIRClasspathExtFeature
import org.seqra.ir.api.cir.CIRClasspathExtFeature.CIRResolvedFunctionResult
import org.seqra.ir.api.cir.CIRClasspathExtFeature.CIRResolvedGlobalResult
import org.seqra.ir.api.cir.CIRClasspathExtFeature.CIRResolvedTypeResult
import org.seqra.ir.api.cir.CIRFeatureEvent
import org.seqra.ir.api.cir.cfg.*
import org.seqra.ir.impl.CIRCacheSettings
import org.seqra.ir.impl.caches.xodus.XODUS_CACHE_PROVIDER_ID
import org.seqra.ir.impl.logger

open class CIRClasspathCache(settings: CIRCacheSettings) : CIRClasspathExtFeature {
    private val cacheProvider = PluggableCacheProvider.getProvider(settings.cacheSpiId ?: XODUS_CACHE_PROVIDER_ID)

    private val functionCache = newSegment<CIRFunctionID, CIRResolvedFunctionResult>(settings.functions)
    private val typeCache = newSegment<MLIRTypeID, CIRResolvedTypeResult>(settings.types)
    private val globalCache = newSegment<CIRGlobalID, CIRResolvedGlobalResult>(settings.globals)

    override fun tryFindGlobal(globalID: CIRGlobalID): CIRResolvedGlobalResult? {
        logger.warn { if (globalCache[globalID] != null) "Cache hit: ${globalID.id}" else "Cache miss ${globalID.id}" }
        return globalCache[globalID]
    }

    override fun tryFindType(typeID: MLIRTypeID): CIRResolvedTypeResult? {
        logger.warn { if (typeCache[typeID] != null) "Cache hit: ${typeID.id}" else "Cache miss ${typeID.id}" }
        return typeCache[typeID]
    }

    override fun tryFindFunction(functionID: CIRFunctionID): CIRResolvedFunctionResult? {
        logger.warn { if (functionCache[functionID] != null) "Cache hit: ${functionID.id}" else "Cache miss ${functionID.id}" }
        return functionCache[functionID]
    }

    override fun on(event: CIRFeatureEvent) {
        when (val result = event.result) {
            is CIRResolvedFunctionResult -> functionCache[result.name] = result
            is CIRResolvedTypeResult -> typeCache[result.name] = result
            is CIRResolvedGlobalResult -> globalCache[result.name] = result
        }
    }

    private fun <K : Any, V : Any> newSegment(settings: CIRCacheSegmentSettings): PluggableCache<K, V> {
        with(settings) {
            return cacheProvider.newCache {
                maximumSize = maxSize.toInt()
                expirationDuration = expiration
                valueRefType = valueStoreType
            }
        }
    }
}
