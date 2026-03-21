package org.seqra.ir.api.cir.cfg

import org.seqra.ir.api.common.CommonTypeName
import java.math.BigDecimal
import java.math.BigInteger

data class MLIRModuleID(
    val id: String,
)

data class CIRGlobalID(
    val moduleID: MLIRModuleID,
    val id: String,
)

data class CIRFunctionID(
    val moduleID: MLIRModuleID,
    val id: String,
)

data class MLIRBlockID(
    val id: Long,
)

data class MLIRTypeID(
    val moduleID: MLIRModuleID,
    val id: String,
) : CommonTypeName {
    override val typeName = id
}

data class MLIROpID(
    val id: Long,
)