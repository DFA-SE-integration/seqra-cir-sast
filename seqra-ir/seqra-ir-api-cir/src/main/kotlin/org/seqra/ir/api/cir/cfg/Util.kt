package org.seqra.ir.api.cir.cfg

import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.common.CommonTypeName

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

fun MLIRTypeID.normalizeIndirectFunctionTypeId(cp: CIRClasspath): MLIRTypeID? {
    val resolvedType = cp.findTypeOrNull(this) ?: return null
    return when (resolvedType) {
        is CIRPointerType -> resolvedType.pointee.normalizeFunctionTypeId(cp)
        else -> null
    }
}

fun MLIRTypeID.normalizeFunctionTypeId(cp: CIRClasspath): MLIRTypeID? {
    val resolvedType = cp.findTypeOrNull(this) ?: return null
    return when (resolvedType) {
        is CIRFuncType -> resolvedType.id
        is CIRMethodType -> resolvedType.memberFuncTy.normalizeFunctionTypeId(cp)
        else -> null
    }
}