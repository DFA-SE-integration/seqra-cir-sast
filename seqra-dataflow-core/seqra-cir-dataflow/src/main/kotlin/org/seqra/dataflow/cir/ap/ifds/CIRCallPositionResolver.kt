package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.configuration.core.Argument
import org.seqra.dataflow.configuration.core.ClassStatic
import org.seqra.dataflow.configuration.core.Position
import org.seqra.dataflow.configuration.core.PositionResolver
import org.seqra.dataflow.configuration.core.PositionWithAccess
import org.seqra.dataflow.configuration.core.Result
import org.seqra.dataflow.configuration.core.This
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionParameter
import org.seqra.ir.api.cir.cfg.CIRMethodType
import org.seqra.ir.api.cir.cfg.CIRTryCallOpInst
import org.seqra.ir.api.cir.cfg.MLIRBasicBlock
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRType
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.common.cfg.CommonCallExpr
import org.seqra.util.Maybe
import org.seqra.util.toMaybe

class CallPositionToCIRValueResolver(
    private val callExpr: CommonCallExpr,
    private val returnValue: MLIRValue?,
) : PositionResolver<Maybe<MLIRValue>> {
    override fun resolve(position: Position): Maybe<MLIRValue> = when (position) {
        is Argument ->
            (callExpr.args.getOrNull(position.index) as? MLIRValue).toMaybe()

        This ->
            when (callExpr) {
                is CIRCallOpInst -> {
                    val curMethod = callExpr.location.method
                    curMethod.parameters.getOrNull(0)?.asBlockArgumentOrThrow(curMethod).toMaybe()
                }
                is CIRTryCallOpInst -> {
                    val curMethod = callExpr.location.method
                    curMethod.parameters.getOrNull(0)?.asBlockArgumentOrThrow(curMethod).toMaybe()
                }
                else -> throw RuntimeException("enclosing CIR function not found for !this!")
            }

        is Result -> returnValue?.toMaybe() ?: callExpr.resultValueOrNull().toMaybe()

        is PositionWithAccess -> notImplemented()

        // Inapplicable caller positions
        is ClassStatic -> notImplemented()
    }
}

class CalleePositionToCIRValueResolver(
    private val method: CIRFunction,
) : PositionResolver<Maybe<MLIRValue>> {
    @Suppress("unused")
    private val cp = method.classpath

    override fun resolve(position: Position): Maybe<MLIRValue> = when (position) {
        is Argument ->
            method.parameters.getOrNull(position.index)?.asBlockArgumentOrThrow(method).toMaybe()

        /** Typically %arg0 */
        is This ->
            method.parameters.getOrNull(0)?.asBlockArgumentOrThrow(method).toMaybe()

        is PositionWithAccess -> notImplemented()

        // Inapplicable callee positions
        Result -> notImplemented()
        is ClassStatic -> notImplemented()
    }
}

private fun notImplemented(): Nothing = throw RuntimeException("Not Implemented")

private fun CIRFunction.entryBlockOrThrow(): MLIRBasicBlock {
    val bs = blocks.blocks
    if (bs.isEmpty()) {
        throw RuntimeException("CIR function $id has no basic blocks")
    }
    return bs.first()
}

private fun CIRFunctionParameter.asBlockArgumentOrThrow(method: CIRFunction): MLIRBlockValue {
    val entryBlock = method.entryBlockOrThrow()
    return MLIRBlockValue(type, entryBlock.id, index.toLong())
}

private fun CommonCallExpr.resultValueOrNull(): MLIRValue? = when (this) {
    is CIRCallOpInst -> {
        val r = result ?: return null
        MLIROpValue(r, id, 0L)
    }

    is CIRTryCallOpInst -> {
        val r = result ?: return null
        MLIROpValue(r, id, 0L)
    }

    else -> null
}

class CIRMethodPositionBaseTypeResolver(
    private val method: CIRFunction,
) : PositionResolver<MLIRType?> {
    private val cp = method.classpath

    override fun resolve(position: Position): MLIRType? = when (position) {
        This -> method.receiverTypeOrNull()
        is Argument -> method.parameters.getOrNull(position.index)?.let { cp.findTypeOrNull(it.type) }
        Result -> cp.findTypeOrNull(method.returnType)
        is PositionWithAccess -> resolve(position.base)
        is ClassStatic -> null
    }
}

private fun CIRFunction.receiverTypeOrNull(): MLIRType? {
    val functionTypeId = info.functionType.value
    val resolved = classpath.findTypeOrNull(functionTypeId) ?: return null
    return when (resolved) {
        is CIRMethodType -> classpath.findTypeOrNull(resolved.clsTy)
        else -> null
    }
}
