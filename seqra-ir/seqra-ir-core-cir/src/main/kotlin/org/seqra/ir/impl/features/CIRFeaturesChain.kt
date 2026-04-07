package org.seqra.ir.impl.features

import org.seqra.ir.api.cir.CIRClasspathFeature
import org.seqra.ir.api.cir.CIRFeatureEvent
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRGraph

interface CIRFunctionExtFeature : CIRClasspathFeature {
    interface CIRFlowGraphResult {
        val function: CIRFunction
        val flowGraph: CIRGraph
    }

    interface CIRBlockListResult {
        val function: CIRFunction
        val blockList: CIRBlockList
    }

    fun flowGraph(function: CIRFunction): CIRFlowGraphResult? = null
    fun blockList(function: CIRFunction): CIRBlockListResult? = null
}

class CIRFeaturesChain(features: List<CIRClasspathFeature>) {
    val featuresArray = features.toTypedArray()

    inline fun <reified T : CIRClasspathFeature, W> run(call: (T) -> W?): W? {
        for (feature in featuresArray) {
            if (feature is T) {
                val result = call(feature)
                if (result != null) {
                    val event = feature.event(result)
                    if (event != null) {
                        for (anyFeature in featuresArray) {
                            anyFeature.on(event)
                        }
                    }
                    return result
                }
            }
        }
        return null
    }
}

class CIRFeatureEventImpl(
    override val feature: CIRClasspathFeature,
    override val result: Any,
) : CIRFeatureEvent
