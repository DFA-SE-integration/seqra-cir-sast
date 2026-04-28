package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.EmptyMethodContext
import org.seqra.dataflow.ap.ifds.MethodContext
import org.seqra.dataflow.ap.ifds.MethodWithContext
import org.seqra.dataflow.configuration.core.This
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRTryCallOpInst
import org.seqra.ir.api.cir.cfg.resolveDirectCall
import org.seqra.ir.api.common.cfg.CommonCallExpr

class CIRCallResolver(
    private val cp: CIRClasspath
) {
    sealed interface MethodResolutionResult {
        data object MethodResolutionFailed : MethodResolutionResult
        data class ConcreteMethod(val method: MethodWithContext) : MethodResolutionResult
    }

    fun resolve(call: CommonCallExpr, location: CIRInst, context: MethodContext): List<MethodResolutionResult> {
        return when (call) {
            is CIRCallOpInst, is CIRTryCallOpInst -> {
                val callees = call.resolveDirectCall(cp)
                if (callees.isEmpty())
                    listOf(MethodResolutionResult.MethodResolutionFailed)
                else
                    callees.map { function -> MethodResolutionResult.ConcreteMethod(attachContext(function)) }
            }
            else -> listOf(MethodResolutionResult.MethodResolutionFailed)
        }
    }

    private fun attachContext(function: CIRFunction): MethodWithContext {
        val methodContext = CIRMethodPositionBaseTypeResolver(function)
            .resolve(This)
            ?.id
            ?.let(::CIRInstanceTypeMethodContext)
            ?: EmptyMethodContext

        return MethodWithContext(function, methodContext)
    }
}
