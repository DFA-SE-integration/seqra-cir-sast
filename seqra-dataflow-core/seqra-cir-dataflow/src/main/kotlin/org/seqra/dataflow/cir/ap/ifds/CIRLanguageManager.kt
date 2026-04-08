package org.seqra.dataflow.cir.ap.ifds

import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRResumeOpInst
import org.seqra.ir.api.cir.cfg.CIRThrowOpInst
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRTryCallOpInst
import org.seqra.dataflow.ap.ifds.LanguageManager
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.cfg.CommonCallExpr
import org.seqra.ir.api.common.cfg.CommonInst
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

open class CIRLanguageManager(val cp: CIRClasspath) : LanguageManager {

    override fun getInstIndex(inst: CommonInst): Int {
        cirDowncast<CIRInst>(inst)
        return inst.location.index
    }

    override fun getMaxInstIndex(method: CommonMethod): Int {
        cirDowncast<CIRFunction>(method)
        return method.allInstructions.lastIndex
    }

    override fun getInstByIndex(method: CommonMethod, index: Int): CommonInst {
        cirDowncast<CIRFunction>(method)
        return method.allInstructions[index]
    }

    override fun isEmpty(method: CommonMethod): Boolean {
        cirDowncast<CIRFunction>(method)
        return method.allInstructions.isEmpty()
    }

    override fun getCallExpr(inst: CommonInst): CommonCallExpr? {
        cirDowncast<CIRInst>(inst)
        return when (inst) {
            is CIRCallOpInst -> inst.takeIf { it.calleeRef != null }
            is CIRTryCallOpInst -> inst.takeIf { it.calleeRef != null }
            else -> null
        }
    }

    override fun producesExceptionalControlFlow(inst: CommonInst): Boolean {
        return inst is CIRThrowOpInst || inst is CIRResumeOpInst
    }

    override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod {
        val ref = when (callExpr) {
            is CIRCallOpInst -> callExpr.calleeRef
            is CIRTryCallOpInst -> callExpr.calleeRef
            else -> error("Not a CIR call expression: $callExpr")
        } ?: error("Indirect call passed to getCalleeMethod")
        return ref.function
            ?: error("Cannot resolve callee for symbol '${ref.symbolName}'")
    }

    override val methodContextSerializer = CIRMethodContextSerializer(cp)
}

@OptIn(ExperimentalContracts::class)
internal inline fun <reified T> cirDowncast(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    check(value is T) { "CIR downcast error: expected ${T::class}, got $value" }
}
