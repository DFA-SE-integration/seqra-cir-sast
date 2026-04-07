package org.seqra.ir.impl.features

import org.seqra.ir.api.cir.CIRFeatureEvent
import org.seqra.ir.api.cir.CIRInstExtFeature
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.impl.cfg.CIRGraphImpl
import org.seqra.ir.impl.cfg.builder.buildBlocks
import org.seqra.ir.impl.features.AbstractCIRInstResult.CIRFlowGraphResultImpl
import org.seqra.ir.impl.grpc.Model

class FunctionInstructionsFeature : CIRFunctionExtFeature {
    private val CIRFunction.functionFeatures
        get() = classpath.features.filterIsInstance<CIRInstExtFeature>()

    override fun flowGraph(function: CIRFunction): CIRFunctionExtFeature.CIRFlowGraphResult {
        return CIRFlowGraphResultImpl(
            function, CIRGraphImpl(function)
        )
    }

    override fun blockList(function: CIRFunction): CIRFunctionExtFeature.CIRBlockListResult {
        val cirBlockList = function.withIRNode { ir ->
            ir?.let { buildBlocks(function, Model.MLIRBlockList.parseFrom(it)) } ?: emptyList()
        }
        return AbstractCIRInstResult.CIRBlockListResultImpl(function,
            function.functionFeatures.fold(CIRBlockList(cirBlockList)) { value, feature ->
                feature.transformBlockList(
                    function, value
                )
            })
    }

    override fun event(result: Any): CIRFeatureEvent {
        return CIRFeatureEventImpl(this, result)
    }
}