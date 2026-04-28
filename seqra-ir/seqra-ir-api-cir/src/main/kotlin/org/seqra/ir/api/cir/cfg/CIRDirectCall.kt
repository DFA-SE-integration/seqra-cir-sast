package org.seqra.ir.api.cir.cfg

import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.features.CIRCallByPointerOpInst
import org.seqra.ir.api.common.cfg.CommonCallExpr
import org.seqra.ir.api.common.cfg.CommonValue

interface CIRDirectCall : CIRInst, CommonCallExpr {
    val arg_ops: List<MLIRValue>
    val result: MLIRTypeID?
    val calleeRef: CIRCalleeRef?

    val exception: MLIRUnitAttr?
    val callee: MLIRFlatSymbolRefAttr?
    val callingConv: CIRCallingConv
    val extraAttrs: CIRExtraFuncAttributesAttr

    override val typeName: String get() = result?.typeName ?: "void"
    override val args: List<CommonValue> get() = arg_ops

    fun indirect() = CIRCallByPointerOpInst(this)
}

/**
 * cir.call (::cir::CallOp)
 * Call operation
 *
 * Direct and indirect calls.
 *
 * For direct calls, the call operation represents a direct call to a function that is within the same symbol scope as the call. The operands and result types of the call must match the specified function type. The callee is encoded as a aymbol reference attribute named “callee”.
 *
 * For indirect calls, the first mlir::Operation operand is the call target.
 *
 * Given the way indirect calls are encoded, avoid using mlir::Operation methods to walk the operands for this operation, instead use the methods provided by CIRCallOpInterface.
 *
 * If the cir.call has the exception keyword, the call can throw. In this case, cleanups can be added in the cleanup region.
 *
 * // Direct call
 * %2 = cir.call @my_add(%0, %1) : (f32, f32) -> f32
 *  ...
 * // Indirect call
 * %20 = cir.call %18(%17)
 *  ...
 * // Call that might throw
 * cir.call exception @my_div() -> () cleanup {
 *   // call dtor...
 * }
 *
 * Took invariant that indirect call operate only above cir.ptr<cir.func>
 * e.g. In clangir/clang/test/CIR/IR/call.cir:
 *   - !fnptr = !cir.ptr<!cir.func<!s32i (!s32i)>>
 *   - cir.call %fnptr(%a) : (!fnptr, !s32i) -> !s32i
 */
fun CIRDirectCall.resolveDirectCall(cp: CIRClasspath) : List<CIRFunction> {
    // Direct call
    calleeRef?.function?.let { calleeFunc ->
        return listOf(calleeFunc)
    }

    // Indirect call (arg#0 store cir.ptr<cir.fun>)
    if (arg_ops.isEmpty()) {
        return emptyList()
    }

    // Fetch indirect callee
    val functionTypeId = indirect().functionType
        .normalizeIndirectFunctionTypeId(cp)
        ?: return emptyList()

    return cp.resolveFunctionCandidates(functionTypeId)
}

fun CIRClasspath.resolveFunctionCandidates(functionTypeId: MLIRTypeID): List<CIRFunction> {
    return db.persistence.findFunctionsByType(this, functionTypeId)
        .mapNotNull(::findFunctionOrNull)
        .distinctBy { it.id }
}