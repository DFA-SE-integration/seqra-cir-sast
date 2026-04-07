package org.seqra.ir.impl.features

import org.seqra.ir.api.cir.CIRClasspathExtFeature.*
import org.seqra.ir.api.cir.cfg.*

sealed class AbstractCIRInstResult(val function: CIRFunction) {
    class CIRFlowGraphResultImpl(method: CIRFunction, override val flowGraph: CIRGraph) : AbstractCIRInstResult(method),
        CIRFunctionExtFeature.CIRFlowGraphResult

    class CIRBlockListResultImpl(method: CIRFunction, override val blockList: CIRBlockList) :
        AbstractCIRInstResult(method), CIRFunctionExtFeature.CIRBlockListResult
}


sealed class AbstractCIRResolvedResult {
    class CIRResolvedFunctionResultImpl(override val name: CIRFunctionID, override val function: CIRFunction?) :
        AbstractCIRResolvedResult(), CIRResolvedFunctionResult

    class CIRResolvedTypeResultImpl(override val name: MLIRTypeID, override val type: MLIRType?) :
        AbstractCIRResolvedResult(), CIRResolvedTypeResult

    class CIRResolvedGlobalResultImpl(override val name: CIRGlobalID, override val global: CIRGlobal?) :
        AbstractCIRResolvedResult(), CIRResolvedGlobalResult
}
