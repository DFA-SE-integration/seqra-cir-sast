package org.seqra.ir.impl.ir

import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.cfg.CIRFuncType
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRMethodType
import org.seqra.ir.api.cir.cfg.MLIRTypeID

class CIRResolutionException(override val message: String?) : RuntimeException()

class CIRFunctionTypeResolver(val function: CIRFunction, val classpath: CIRClasspath) {
    val functionTypeID: MLIRTypeID = function.info.functionType.value
    val functionType by lazy {
        val resolvedType = resolveFunctionType(functionTypeID)
            ?: throw CIRResolutionException("Failed to resolve function type ${functionTypeID.id}")
        resolvedType
    }

    private fun resolveFunctionType(functionTypeID: MLIRTypeID): CIRFuncType? {
        val resolvedType = classpath.findTypeOrNull(functionTypeID) ?: return null
        return when (resolvedType) {
            is CIRFuncType -> resolvedType
            is CIRMethodType -> resolveFunctionType(resolvedType.memberFuncTy)
            else -> throw CIRResolutionException("Unknown function type ${functionTypeID.id}")
        }
    }

    fun returnType(): MLIRTypeID = functionType.returnType
    fun parameterTypes(): List<MLIRTypeID> = functionType.inputs
}
