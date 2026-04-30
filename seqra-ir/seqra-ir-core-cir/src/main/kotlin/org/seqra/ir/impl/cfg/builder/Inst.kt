package org.seqra.ir.impl.cfg.builder

import org.seqra.ir.api.cir.cfg.*
import org.seqra.ir.impl.grpc.Op

private val CIRExpressions = listOf(
    Op.MLIROp.OperationCase.ABS_OP,
    Op.MLIROp.OperationCase.BASE_CLASS_ADDR_OP,
    Op.MLIROp.OperationCase.BIN_OP,
    Op.MLIROp.OperationCase.BIT_CLRSB_OP,
    Op.MLIROp.OperationCase.BIT_CLZ_OP,
    Op.MLIROp.OperationCase.BIT_CTZ_OP,
    Op.MLIROp.OperationCase.BIT_FFS_OP,
    Op.MLIROp.OperationCase.BIT_PARITY_OP,
    Op.MLIROp.OperationCase.BIT_POPCOUNT_OP,
    Op.MLIROp.OperationCase.BYTESWAP_OP,
    Op.MLIROp.OperationCase.CAST_OP,
    Op.MLIROp.OperationCase.CEIL_OP,
    Op.MLIROp.OperationCase.CMP_OP,
    Op.MLIROp.OperationCase.CMP_THREE_WAY_OP,
    Op.MLIROp.OperationCase.COMPLEX_BIN_OP,
    Op.MLIROp.OperationCase.COMPLEX_CREATE_OP,
    Op.MLIROp.OperationCase.COMPLEX_IMAG_OP,
    Op.MLIROp.OperationCase.COMPLEX_IMAG_PTR_OP,
    Op.MLIROp.OperationCase.COMPLEX_REAL_OP,
    Op.MLIROp.OperationCase.COMPLEX_REAL_PTR_OP,
    Op.MLIROp.OperationCase.CONSTANT_OP,
    Op.MLIROp.OperationCase.COPYSIGN_OP,
    Op.MLIROp.OperationCase.COS_OP,
    Op.MLIROp.OperationCase.DERIVED_CLASS_ADDR_OP,
    Op.MLIROp.OperationCase.DYNAMIC_CAST_OP,
    Op.MLIROp.OperationCase.EH_TYPE_ID_OP,
    Op.MLIROp.OperationCase.EXP2_OP,
    Op.MLIROp.OperationCase.EXP_OP,
    Op.MLIROp.OperationCase.F_ABS_OP,
    Op.MLIROp.OperationCase.F_MAX_OP,
    Op.MLIROp.OperationCase.F_MIN_OP,
    Op.MLIROp.OperationCase.F_MOD_OP,
    Op.MLIROp.OperationCase.FLOOR_OP,
    Op.MLIROp.OperationCase.GET_GLOBAL_OP,
    Op.MLIROp.OperationCase.GET_MEMBER_OP,
    Op.MLIROp.OperationCase.IS_CONSTANT_OP,
    Op.MLIROp.OperationCase.IS_FP_CLASS_OP,
    Op.MLIROp.OperationCase.ITER_BEGIN_OP,
    Op.MLIROp.OperationCase.ITER_END_OP,
    Op.MLIROp.OperationCase.L_LRINT_OP,
    Op.MLIROp.OperationCase.L_LROUND_OP,
    Op.MLIROp.OperationCase.LOG10_OP,
    Op.MLIROp.OperationCase.LOG2_OP,
    Op.MLIROp.OperationCase.LOG_OP,
    Op.MLIROp.OperationCase.LRINT_OP,
    Op.MLIROp.OperationCase.LROUND_OP,
    Op.MLIROp.OperationCase.NEARBYINT_OP,
    Op.MLIROp.OperationCase.OBJ_SIZE_OP,
    Op.MLIROp.OperationCase.POW_OP,
    Op.MLIROp.OperationCase.PTR_DIFF_OP,
    Op.MLIROp.OperationCase.PTR_MASK_OP,
    Op.MLIROp.OperationCase.PTR_STRIDE_OP,
    Op.MLIROp.OperationCase.RINT_OP,
    Op.MLIROp.OperationCase.ROTATE_OP,
    Op.MLIROp.OperationCase.ROUND_OP,
    Op.MLIROp.OperationCase.SELECT_OP,
    Op.MLIROp.OperationCase.SHIFT_OP,
    Op.MLIROp.OperationCase.SIGN_BIT_OP,
    Op.MLIROp.OperationCase.SIN_OP,
    Op.MLIROp.OperationCase.SQRT_OP,
    Op.MLIROp.OperationCase.TRUNC_OP,
    Op.MLIROp.OperationCase.UNARY_OP,
    Op.MLIROp.OperationCase.VTT_ADDR_POINT_OP,
    Op.MLIROp.OperationCase.V_TABLE_ADDR_POINT_OP,
)

class CIRInstBuilder(private val function: CIRFunction) {

    private var indexCounter : Int = 0

    fun buildInst(inst: Op.MLIROp) : CIRInst {
        val id = buildMLIROpID(inst.id)
        val location = CIRInstLocation(function, indexCounter, buildMLIRLocation(inst.location))
        indexCounter += 1

        if (CIRExpressions.contains(inst.operationCase!!)) {
            val expr = buildExpr(inst)
            return CIRAssignInst(
                location,
                id,
                MLIROpValue(expr.type, buildMLIROpID(inst.id), 0),
                expr
            )
        }

        return when (inst.operationCase!!) {
            Op.MLIROp.OperationCase.ALLOC_EXCEPTION_OP -> buildCIRAllocExceptionOpInst(inst.allocExceptionOp, id, location)
            Op.MLIROp.OperationCase.ALLOCA_OP -> buildCIRAllocaOpInst(inst.allocaOp, id, location)
            Op.MLIROp.OperationCase.ASSUME_ALIGNED_OP -> buildCIRAssumeAlignedOpInst(inst.assumeAlignedOp, id, location)
            Op.MLIROp.OperationCase.ASSUME_OP -> buildCIRAssumeOpInst(inst.assumeOp, id, location)
            Op.MLIROp.OperationCase.ASSUME_SEP_STORAGE_OP -> buildCIRAssumeSepStorageOpInst(inst.assumeSepStorageOp, id, location)
            Op.MLIROp.OperationCase.ATOMIC_CMP_XCHG_OP -> buildCIRAtomicCmpXchgOpInst(inst.atomicCmpXchgOp, id, location)
            Op.MLIROp.OperationCase.ATOMIC_FETCH_OP -> buildCIRAtomicFetchOpInst(inst.atomicFetchOp, id, location)
            Op.MLIROp.OperationCase.ATOMIC_XCHG_OP -> buildCIRAtomicXchgOpInst(inst.atomicXchgOp, id, location)
            Op.MLIROp.OperationCase.BIN_OP_OVERFLOW_OP -> buildCIRBinOpOverflowOpInst(inst.binOpOverflowOp, id, location)
            Op.MLIROp.OperationCase.BR_COND_OP -> buildCIRBrCondOpInst(inst.brCondOp, id, location)
            Op.MLIROp.OperationCase.BR_OP -> buildCIRBrOpInst(inst.brOp, id, location)
            Op.MLIROp.OperationCase.BREAK_OP -> buildCIRBreakOpInst(id, location)
            Op.MLIROp.OperationCase.CASE_OP -> buildCIRCaseOpInst(inst.caseOp, id, location)
            Op.MLIROp.OperationCase.CALL_OP -> buildCIRCallOpInst(inst.callOp, id, location)
            Op.MLIROp.OperationCase.CATCH_PARAM_OP -> buildCIRCatchParamOpInst(inst.catchParamOp, id, location)
            Op.MLIROp.OperationCase.CLEAR_CACHE_OP -> buildCIRClearCacheOpInst(inst.clearCacheOp, id, location)
            Op.MLIROp.OperationCase.COPY_OP -> buildCIRCopyOpInst(inst.copyOp, id, location)
            Op.MLIROp.OperationCase.CONDITION_OP -> buildCIRConditionOpInst(inst.conditionOp, id, location)
            Op.MLIROp.OperationCase.CONTINUE_OP -> buildCIRContinueOpInst(id, location)
            Op.MLIROp.OperationCase.DYNAMIC_CAST_OP -> buildCIRDynamicCastOp(inst.dynamicCastOp, id, location)
            Op.MLIROp.OperationCase.DO_WHILE_OP -> buildCIRDoWhileOpInst(id, location)
            Op.MLIROp.OperationCase.EH_INFLIGHT_OP -> buildCIREhInflightOpInst(inst.ehInflightOp, id, location)
            Op.MLIROp.OperationCase.EXPECT_OP -> buildCIRExpectOpInst(inst.expectOp, id, location)
            Op.MLIROp.OperationCase.FREE_EXCEPTION_OP -> buildCIRFreeExceptionOpInst(inst.freeExceptionOp, id, location)
            Op.MLIROp.OperationCase.FOR_OP -> buildCIRForOpInst(id, location)
            Op.MLIROp.OperationCase.GET_BITFIELD_OP -> buildCIRGetBitfieldOpInst(inst.getBitfieldOp, id, location)
            Op.MLIROp.OperationCase.GET_METHOD_OP -> buildCIRGetMethodOpInst(inst.getMethodOp, id, location)
            Op.MLIROp.OperationCase.GET_RUNTIME_MEMBER_OP -> buildCIRGetRuntimeMemberOpInst(inst.getRuntimeMemberOp, id, location)
            Op.MLIROp.OperationCase.GOTO_OP -> buildCIRGotoOpInst(inst.gotoOp, id, location)
            Op.MLIROp.OperationCase.IF_OP -> buildCIRIfOpInst(inst.ifOp, id, location)
            Op.MLIROp.OperationCase.LLVM_INTRINSIC_CALL_OP -> buildCIRLLVMIntrinsicCallOpInst(inst.llvmIntrinsicCallOp, id, location)
            Op.MLIROp.OperationCase.LOAD_OP -> buildCIRLoadOpInst(inst.loadOp, id, location)
            Op.MLIROp.OperationCase.MEM_CHR_OP -> buildCIRMemChrOpInst(inst.memChrOp, id, location)
            Op.MLIROp.OperationCase.MEM_CPY_INLINE_OP -> buildCIRMemCpyInlineOpInst(inst.memCpyInlineOp, id, location)
            Op.MLIROp.OperationCase.MEM_CPY_OP -> buildCIRMemCpyOpInst(inst.memCpyOp, id, location)
            Op.MLIROp.OperationCase.MEM_MOVE_OP -> buildCIRMemMoveOpInst(inst.memMoveOp, id, location)
            Op.MLIROp.OperationCase.MEM_SET_INLINE_OP -> buildCIRMemSetInlineOpInst(inst.memSetInlineOp, id, location)
            Op.MLIROp.OperationCase.MEM_SET_OP -> buildCIRMemSetOpInst(inst.memSetOp, id, location)
            Op.MLIROp.OperationCase.PREFETCH_OP -> buildCIRPrefetchOpInst(inst.prefetchOp, id, location)
            Op.MLIROp.OperationCase.RESUME_OP -> buildCIRResumeOpInst(inst.resumeOp, id, location)
            Op.MLIROp.OperationCase.RETURN_OP -> buildCIRReturnOpInst(inst.returnOp, id, location)
            Op.MLIROp.OperationCase.SCOPE_OP -> buildCIRScopeOpInst(inst.scopeOp, id, location)
            Op.MLIROp.OperationCase.SET_BITFIELD_OP -> buildCIRSetBitfieldOpInst(inst.setBitfieldOp, id, location)
            Op.MLIROp.OperationCase.STACK_RESTORE_OP -> buildCIRStackRestoreOpInst(inst.stackRestoreOp, id, location)
            Op.MLIROp.OperationCase.STACK_SAVE_OP -> buildCIRStackSaveOpInst(inst.stackSaveOp, id, location)
            Op.MLIROp.OperationCase.STD_FIND_OP -> buildCIRStdFindOpInst(inst.stdFindOp, id, location)
            Op.MLIROp.OperationCase.STORE_OP -> buildCIRStoreOpInst(inst.storeOp, id, location)
            Op.MLIROp.OperationCase.SWITCH_FLAT_OP -> buildCIRSwitchFlatOpInst(inst.switchFlatOp, id, location)
            Op.MLIROp.OperationCase.SWITCH_OP -> buildCIRSwitchOpInst(inst.switchOp, id, location)
            Op.MLIROp.OperationCase.THROW_OP -> buildCIRThrowOpInst(inst.throwOp, id, location)
            Op.MLIROp.OperationCase.TRAP_OP -> buildCIRTrapOpInst(inst.trapOp, id, location)
            Op.MLIROp.OperationCase.TRY_OP -> buildCIRTryOpInst(inst.tryOp, id, location)
            Op.MLIROp.OperationCase.TRY_CALL_OP -> buildCIRTryCallOpInst(inst.tryCallOp, id, location)
            Op.MLIROp.OperationCase.UNREACHABLE_OP -> buildCIRUnreachableOpInst(inst.unreachableOp, id, location)
            Op.MLIROp.OperationCase.VA_ARG_OP -> buildCIRVAArgOpInst(inst.vaArgOp, id, location)
            Op.MLIROp.OperationCase.VA_COPY_OP -> buildCIRVACopyOpInst(inst.vaCopyOp, id, location)
            Op.MLIROp.OperationCase.VA_END_OP -> buildCIRVAEndOpInst(inst.vaEndOp, id, location)
            Op.MLIROp.OperationCase.VA_START_OP -> buildCIRVAStartOpInst(inst.vaStartOp, id, location)
            Op.MLIROp.OperationCase.VEC_CMP_OP -> buildCIRVecCmpOpInst(inst.vecCmpOp, id, location)
            Op.MLIROp.OperationCase.VEC_CREATE_OP -> buildCIRVecCreateOpInst(inst.vecCreateOp, id, location)
            Op.MLIROp.OperationCase.VEC_EXTRACT_OP -> buildCIRVecExtractOpInst(inst.vecExtractOp, id, location)
            Op.MLIROp.OperationCase.VEC_INSERT_OP -> buildCIRVecInsertOpInst(inst.vecInsertOp, id, location)
            Op.MLIROp.OperationCase.VEC_SHUFFLE_DYNAMIC_OP -> buildCIRVecShuffleDynamicOpInst(inst.vecShuffleDynamicOp, id, location)
            Op.MLIROp.OperationCase.VEC_SHUFFLE_OP -> buildCIRVecShuffleOpInst(inst.vecShuffleOp, id, location)
            Op.MLIROp.OperationCase.VEC_SPLAT_OP -> buildCIRVecSplatOpInst(inst.vecSplatOp, id, location)
            Op.MLIROp.OperationCase.VEC_TERNARY_OP -> buildCIRVecTernaryOpInst(inst.vecTernaryOp, id, location)
            Op.MLIROp.OperationCase.YIELD_OP -> buildCIRYieldOpInst(inst.yieldOp, id, location)
            Op.MLIROp.OperationCase.WHILE_OP -> buildCIRWhileOpInst(id, location)
            Op.MLIROp.OperationCase.AWAIT_OP -> buildCIRAwaitOpInst(inst.awaitOp, id, location)
            else -> throw Exception()
        }
    }
}

fun buildCIRAllocExceptionOpInst(inst: Op.CIRAllocExceptionOp, id: MLIROpID, location: CIRInstLocation) =
    CIRAllocExceptionOpInst(
        location,
        id,
        buildMLIRIntegerAttr(inst.size),
        buildMLIRTypeID(inst.addr),
)

fun buildCIRAllocaOpInst(inst: Op.CIRAllocaOp, id: MLIROpID, location: CIRInstLocation) =
    CIRAllocaOpInst(
        location,
        id,
        if (inst.hasDynAllocSize()) buildMLIRValue(inst.dynAllocSize) else null,
        buildMLIRTypeAttr(inst.allocaType),
        buildMLIRStringAttr(inst.name),
        if (inst.hasInit()) buildMLIRUnitAttr(inst.init) else null,
        if (inst.hasConstant()) buildMLIRUnitAttr(inst.constant) else null,
        if (inst.hasAlignment()) buildMLIRIntegerAttr(inst.alignment) else null,
        if (inst.hasAnnotations()) buildMLIRArrayAttr(inst.annotations) else null,
        buildMLIRTypeID(inst.addr),
)

fun buildCIRAssumeAlignedOpInst(inst: Op.CIRAssumeAlignedOp, id: MLIROpID, location: CIRInstLocation) =
    CIRAssumeAlignedOpInst(
        location,
        id,
        buildMLIRValue(inst.pointer),
        if (inst.hasOffset()) buildMLIRValue(inst.offset) else null,
        buildMLIRIntegerAttr(inst.alignment),
        buildMLIRTypeID(inst.result),
)

fun buildCIRAssumeOpInst(inst: Op.CIRAssumeOp, id: MLIROpID, location: CIRInstLocation) =
    CIRAssumeOpInst(
        location,
        id,
        buildMLIRValue(inst.predicate),
)

fun buildCIRAssumeSepStorageOpInst(inst: Op.CIRAssumeSepStorageOp, id: MLIROpID, location: CIRInstLocation) =
    CIRAssumeSepStorageOpInst(
        location,
        id,
        buildMLIRValue(inst.ptr1),
        buildMLIRValue(inst.ptr2),
)

fun buildCIRAtomicCmpXchgOpInst(inst: Op.CIRAtomicCmpXchgOp, id: MLIROpID, location: CIRInstLocation) =
    CIRAtomicCmpXchgOpInst(
        location,
        id,
        buildMLIRValue(inst.ptr),
        buildMLIRValue(inst.expected),
        buildMLIRValue(inst.desired),
        buildCIRMemOrder(inst.succOrder),
        buildCIRMemOrder(inst.failOrder),
        if (inst.hasWeak()) buildMLIRUnitAttr(inst.weak) else null,
        if (inst.hasIsVolatile()) buildMLIRUnitAttr(inst.isVolatile) else null,
        buildMLIRTypeID(inst.old),
        buildMLIRTypeID(inst.cmp),
)

fun buildCIRAtomicFetchOpInst(inst: Op.CIRAtomicFetchOp, id: MLIROpID, location: CIRInstLocation) =
    CIRAtomicFetchOpInst(
        location,
        id,
        buildMLIRValue(inst.ptr),
        buildMLIRValue(inst.getVal()),
        buildCIRAtomicFetchKind(inst.binop),
        buildCIRMemOrder(inst.memOrder),
        if (inst.hasIsVolatile()) buildMLIRUnitAttr(inst.isVolatile) else null,
        if (inst.hasFetchFirst()) buildMLIRUnitAttr(inst.fetchFirst) else null,
        buildMLIRTypeID(inst.result),
)

fun buildCIRAtomicXchgOpInst(inst: Op.CIRAtomicXchgOp, id: MLIROpID, location: CIRInstLocation) =
    CIRAtomicXchgOpInst(
        location,
        id,
        buildMLIRValue(inst.ptr),
        buildMLIRValue(inst.getVal()),
        buildCIRMemOrder(inst.memOrder),
        if (inst.hasIsVolatile()) buildMLIRUnitAttr(inst.isVolatile) else null,
        buildMLIRTypeID(inst.result),
)

fun buildCIRBinOpOverflowOpInst(inst: Op.CIRBinOpOverflowOp, id: MLIROpID, location: CIRInstLocation) =
    CIRBinOpOverflowOpInst(
        location,
        id,
        buildMLIRValue(inst.lhs),
        buildMLIRValue(inst.rhs),
        buildCIRBinOpOverflowKind(inst.kind),
        buildMLIRTypeID(inst.result),
        buildMLIRTypeID(inst.overflow),
)

fun buildCIRBrCondOpInst(inst: Op.CIRBrCondOp, id: MLIROpID, location: CIRInstLocation) =
    CIRBrCondOpInst(
        location,
        id,
        buildMLIRValue(inst.cond),
        buildMLIRValueArray(inst.destOperandsTrueList),
        buildMLIRValueArray(inst.destOperandsFalseList),
        buildMLIRBlockID(inst.destTrue),
        buildMLIRBlockID(inst.destFalse),
)

fun buildCIRBrOpInst(inst: Op.CIRBrOp, id: MLIROpID, location: CIRInstLocation) =
    CIRBrOpInst(
        location,
        id,
        buildMLIRValueArray(inst.destOperandsList),
        buildMLIRBlockID(inst.dest),
)

fun buildCIRCallOpInst(inst: Op.CIRCallOp, id: MLIROpID, location: CIRInstLocation) =
    CIRCallOpInst(
        location,
        id,
        buildMLIRValueArray(inst.argOpsList),
        if (inst.hasException()) buildMLIRUnitAttr(inst.exception) else null,
        if (inst.hasCallee()) buildMLIRFlatSymbolRefAttr(inst.callee) else null,
        buildCIRCallingConv(inst.callingConv),
        buildCIRExtraFuncAttributesAttr(inst.extraAttrs),
        if (inst.hasResult()) buildMLIRTypeID(inst.result) else null,
        if (inst.hasCallee()) CIRCalleeRef(inst.callee.rootReference.value, location.method.classpath) else null,
)

fun buildCIRCatchParamOpInst(inst: Op.CIRCatchParamOp, id: MLIROpID, location: CIRInstLocation) =
    CIRCatchParamOpInst(
        location,
        id,
        if (inst.hasExceptionPtr()) buildMLIRValue(inst.exceptionPtr) else null,
        if (inst.hasKind()) buildCIRCatchParamKind(inst.kind) else null,
        if (inst.hasParam()) buildMLIRTypeID(inst.param) else null,
)

fun buildCIRClearCacheOpInst(inst: Op.CIRClearCacheOp, id: MLIROpID, location: CIRInstLocation) =
    CIRClearCacheOpInst(
        location,
        id,
        buildMLIRValue(inst.begin),
        buildMLIRValue(inst.end),
)

fun buildCIRCopyOpInst(inst: Op.CIRCopyOp, id: MLIROpID, location: CIRInstLocation) =
    CIRCopyOpInst(
        location,
        id,
        buildMLIRValue(inst.dst),
        buildMLIRValue(inst.src),
        if (inst.hasIsVolatile()) buildMLIRUnitAttr(inst.isVolatile) else null,
        if (inst.hasTbaa()) buildMLIRArrayAttr(inst.tbaa) else null,
)

fun buildCIREhInflightOpInst(inst: Op.CIREhInflightOp, id: MLIROpID, location: CIRInstLocation) =
    CIREhInflightOpInst(
        location,
        id,
        if (inst.hasCleanup()) buildMLIRUnitAttr(inst.cleanup) else null,
        if (inst.hasSymTypeList()) buildMLIRArrayAttr(inst.symTypeList) else null,
        buildMLIRTypeID(inst.exceptionPtr),
        buildMLIRTypeID(inst.typeId),
)

fun buildCIRExpectOpInst(inst: Op.CIRExpectOp, id: MLIROpID, location: CIRInstLocation) =
    CIRExpectOpInst(
        location,
        id,
        buildMLIRValue(inst.getVal()),
        buildMLIRValue(inst.expected),
        if (inst.hasProb()) buildMLIRFloatAttr(inst.prob) else null,
        buildMLIRTypeID(inst.result),
)

fun buildCIRFreeExceptionOpInst(inst: Op.CIRFreeExceptionOp, id: MLIROpID, location: CIRInstLocation) =
    CIRFreeExceptionOpInst(
        location,
        id,
        buildMLIRValue(inst.ptr),
)

fun buildCIRGetBitfieldOpInst(inst: Op.CIRGetBitfieldOp, id: MLIROpID, location: CIRInstLocation) =
    CIRGetBitfieldOpInst(
        location,
        id,
        buildMLIRValue(inst.addr),
        buildCIRBitfieldInfoAttr(inst.bitfieldInfo),
        if (inst.hasIsVolatile()) buildMLIRUnitAttr(inst.isVolatile) else null,
        buildMLIRTypeID(inst.result),
)

fun buildCIRGetMethodOpInst(inst: Op.CIRGetMethodOp, id: MLIROpID, location: CIRInstLocation) =
    CIRGetMethodOpInst(
        location,
        id,
        buildMLIRValue(inst.method),
        buildMLIRValue(inst.getObject()),
        buildMLIRTypeID(inst.callee),
        buildMLIRTypeID(inst.adjustedThis),
)

fun buildCIRGetRuntimeMemberOpInst(inst: Op.CIRGetRuntimeMemberOp, id: MLIROpID, location: CIRInstLocation) =
    CIRGetRuntimeMemberOpInst(
        location,
        id,
        buildMLIRValue(inst.addr),
        buildMLIRValue(inst.member),
        buildMLIRTypeID(inst.result),
)

fun buildCIRGotoOpInst(inst: Op.CIRGotoOp, id: MLIROpID, location: CIRInstLocation) =
    CIRGotoOpInst(
        location,
        id,
        buildMLIRStringAttr(inst.label),
    )

fun buildCIRLLVMIntrinsicCallOpInst(inst: Op.CIRLLVMIntrinsicCallOp, id: MLIROpID, location: CIRInstLocation) =
    CIRLLVMIntrinsicCallOpInst(
        location,
        id,
        buildMLIRValueArray(inst.argOpsList),
        buildMLIRStringAttr(inst.intrinsicName),
        if (inst.hasResult()) buildMLIRTypeID(inst.result) else null,
)

fun buildCIRLoadOpInst(inst: Op.CIRLoadOp, id: MLIROpID, location: CIRInstLocation) =
    CIRLoadOpInst(
        location,
        id,
        buildMLIRValue(inst.addr),
        if (inst.hasIsDeref()) buildMLIRUnitAttr(inst.isDeref) else null,
        if (inst.hasIsVolatile()) buildMLIRUnitAttr(inst.isVolatile) else null,
        if (inst.hasAlignment()) buildMLIRIntegerAttr(inst.alignment) else null,
        if (inst.hasMemOrder()) buildCIRMemOrder(inst.memOrder) else null,
        if (inst.hasTbaa()) buildMLIRArrayAttr(inst.tbaa) else null,
        buildMLIRTypeID(inst.result),
)

fun buildCIRMemChrOpInst(inst: Op.CIRMemChrOp, id: MLIROpID, location: CIRInstLocation) =
    CIRMemChrOpInst(
        location,
        id,
        buildMLIRValue(inst.src),
        buildMLIRValue(inst.pattern),
        buildMLIRValue(inst.len),
        buildMLIRTypeID(inst.result),
)

fun buildCIRMemCpyInlineOpInst(inst: Op.CIRMemCpyInlineOp, id: MLIROpID, location: CIRInstLocation) =
    CIRMemCpyInlineOpInst(
        location,
        id,
        buildMLIRValue(inst.dst),
        buildMLIRValue(inst.src),
        buildMLIRIntegerAttr(inst.len),
)

fun buildCIRMemCpyOpInst(inst: Op.CIRMemCpyOp, id: MLIROpID, location: CIRInstLocation) =
    CIRMemCpyOpInst(
        location,
        id,
        buildMLIRValue(inst.dst),
        buildMLIRValue(inst.src),
        buildMLIRValue(inst.len),
)

fun buildCIRMemMoveOpInst(inst: Op.CIRMemMoveOp, id: MLIROpID, location: CIRInstLocation) =
    CIRMemMoveOpInst(
        location,
        id,
        buildMLIRValue(inst.dst),
        buildMLIRValue(inst.src),
        buildMLIRValue(inst.len),
)

fun buildCIRMemSetInlineOpInst(inst: Op.CIRMemSetInlineOp, id: MLIROpID, location: CIRInstLocation) =
    CIRMemSetInlineOpInst(
        location,
        id,
        buildMLIRValue(inst.dst),
        buildMLIRValue(inst.getVal()),
        buildMLIRIntegerAttr(inst.len),
)

fun buildCIRMemSetOpInst(inst: Op.CIRMemSetOp, id: MLIROpID, location: CIRInstLocation) =
    CIRMemSetOpInst(
        location,
        id,
        buildMLIRValue(inst.dst),
        buildMLIRValue(inst.getVal()),
        buildMLIRValue(inst.len),
)

fun buildCIRPrefetchOpInst(inst: Op.CIRPrefetchOp, id: MLIROpID, location: CIRInstLocation) =
    CIRPrefetchOpInst(
        location,
        id,
        buildMLIRValue(inst.addr),
        buildMLIRIntegerAttr(inst.locality),
        if (inst.hasIsWrite()) buildMLIRUnitAttr(inst.isWrite) else null,
)

fun buildCIRResumeOpInst(inst: Op.CIRResumeOp, id: MLIROpID, location: CIRInstLocation) =
    CIRResumeOpInst(
        location,
        id,
        if (inst.hasExceptionPtr()) buildMLIRValue(inst.exceptionPtr) else null,
        if (inst.hasTypeId()) buildMLIRValue(inst.typeId) else null,
        if (inst.hasRethrow()) buildMLIRUnitAttr(inst.rethrow) else null,
)

fun buildCIRReturnOpInst(inst: Op.CIRReturnOp, id: MLIROpID, location: CIRInstLocation) =
    CIRReturnOpInst(
        location,
        id,
        buildMLIRValueArray(inst.inputList),
)

fun buildCIRSetBitfieldOpInst(inst: Op.CIRSetBitfieldOp, id: MLIROpID, location: CIRInstLocation) =
    CIRSetBitfieldOpInst(
        location,
        id,
        buildMLIRValue(inst.addr),
        buildMLIRValue(inst.src),
        buildCIRBitfieldInfoAttr(inst.bitfieldInfo),
        if (inst.hasIsVolatile()) buildMLIRUnitAttr(inst.isVolatile) else null,
        buildMLIRTypeID(inst.result),
)

fun buildCIRStackRestoreOpInst(inst: Op.CIRStackRestoreOp, id: MLIROpID, location: CIRInstLocation) =
    CIRStackRestoreOpInst(
        location,
        id,
        buildMLIRValue(inst.ptr),
)

fun buildCIRStackSaveOpInst(inst: Op.CIRStackSaveOp, id: MLIROpID, location: CIRInstLocation) =
    CIRStackSaveOpInst(
        location,
        id,
        buildMLIRTypeID(inst.result),
)

fun buildCIRStdFindOpInst(inst: Op.CIRStdFindOp, id: MLIROpID, location: CIRInstLocation) =
    CIRStdFindOpInst(
        location,
        id,
        buildMLIRValue(inst.first),
        buildMLIRValue(inst.last),
        buildMLIRValue(inst.pattern),
        buildMLIRFlatSymbolRefAttr(inst.originalFn),
        buildMLIRTypeID(inst.result),
)

fun buildCIRStoreOpInst(inst: Op.CIRStoreOp, id: MLIROpID, location: CIRInstLocation) =
    CIRStoreOpInst(
        location,
        id,
        buildMLIRValue(inst.value),
        buildMLIRValue(inst.addr),
        if (inst.hasIsVolatile()) buildMLIRUnitAttr(inst.isVolatile) else null,
        if (inst.hasAlignment()) buildMLIRIntegerAttr(inst.alignment) else null,
        if (inst.hasMemOrder()) buildCIRMemOrder(inst.memOrder) else null,
        if (inst.hasTbaa()) buildMLIRArrayAttr(inst.tbaa) else null,
)

fun buildCIRSwitchFlatOpInst(inst: Op.CIRSwitchFlatOp, id: MLIROpID, location: CIRInstLocation) =
    CIRSwitchFlatOpInst(
        location,
        id,
        buildMLIRValue(inst.condition),
        buildMLIRValueArray(inst.defaultOperandsList),
        buildMLIRValueArrayArray(inst.caseOperandsList),
        buildMLIRArrayAttr(inst.caseValues),
        buildMLIRDenseI32ArrayAttr(inst.caseOperandSegments),
        buildMLIRBlockID(inst.defaultDestination),
        buildMLIRBlockIDArray(inst.caseDestinationsList),
)

fun buildCIRThrowOpInst(inst: Op.CIRThrowOp, id: MLIROpID, location: CIRInstLocation) =
    CIRThrowOpInst(
        location,
        id,
        if (inst.hasExceptionPtr()) buildMLIRValue(inst.exceptionPtr) else null,
        if (inst.hasTypeInfo()) buildMLIRFlatSymbolRefAttr(inst.typeInfo) else null,
        if (inst.hasDtor()) buildMLIRFlatSymbolRefAttr(inst.dtor) else null,
)

fun buildCIRTrapOpInst(inst: Op.CIRTrapOp, id: MLIROpID, location: CIRInstLocation) =
    CIRTrapOpInst(
        location,
        id,
)

fun buildCIRTryCallOpInst(inst: Op.CIRTryCallOp, id: MLIROpID, location: CIRInstLocation) =
    CIRTryCallOpInst(
        location,
        id,
        buildMLIRValueArray(inst.contOperandsList),
        buildMLIRValueArray(inst.landingPadOperandsList),
        buildMLIRValueArray(inst.argOpsList),
        null,
        if (inst.hasCallee()) buildMLIRFlatSymbolRefAttr(inst.callee) else null,
        buildCIRCallingConv(inst.callingConv),
        buildCIRExtraFuncAttributesAttr(inst.extraAttrs),
        buildMLIRBlockID(inst.cont),
        buildMLIRBlockID(inst.landingPad),
        if (inst.hasResult()) buildMLIRTypeID(inst.result) else null,
        if (inst.hasCallee()) CIRCalleeRef(inst.callee.rootReference.value, location.method.classpath) else null,
)

fun buildCIRUnreachableOpInst(inst: Op.CIRUnreachableOp, id: MLIROpID, location: CIRInstLocation) =
    CIRUnreachableOpInst(
        location,
        id,
)

fun buildCIRVAArgOpInst(inst: Op.CIRVAArgOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVAArgOpInst(
        location,
        id,
        buildMLIRValue(inst.argList),
        buildMLIRTypeID(inst.result),
)

fun buildCIRVACopyOpInst(inst: Op.CIRVACopyOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVACopyOpInst(
        location,
        id,
        buildMLIRValue(inst.dstList),
        buildMLIRValue(inst.srcList),
)

fun buildCIRVAEndOpInst(inst: Op.CIRVAEndOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVAEndOpInst(
        location,
        id,
        buildMLIRValue(inst.argList),
)

fun buildCIRVAStartOpInst(inst: Op.CIRVAStartOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVAStartOpInst(
        location,
        id,
        buildMLIRValue(inst.argList),
)

fun buildCIRVecCmpOpInst(inst: Op.CIRVecCmpOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVecCmpOpInst(
        location,
        id,
        buildMLIRValue(inst.lhs),
        buildMLIRValue(inst.rhs),
        buildCIRCmpOpKind(inst.kind),
        buildMLIRTypeID(inst.result),
)

fun buildCIRVecCreateOpInst(inst: Op.CIRVecCreateOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVecCreateOpInst(
        location,
        id,
        buildMLIRValueArray(inst.elementsList),
        buildMLIRTypeID(inst.result),
)

fun buildCIRVecExtractOpInst(inst: Op.CIRVecExtractOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVecExtractOpInst(
        location,
        id,
        buildMLIRValue(inst.vec),
        buildMLIRValue(inst.index),
        buildMLIRTypeID(inst.result),
)

fun buildCIRVecInsertOpInst(inst: Op.CIRVecInsertOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVecInsertOpInst(
        location,
        id,
        buildMLIRValue(inst.vec),
        buildMLIRValue(inst.value),
        buildMLIRValue(inst.index),
        buildMLIRTypeID(inst.result),
)

fun buildCIRVecShuffleDynamicOpInst(inst: Op.CIRVecShuffleDynamicOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVecShuffleDynamicOpInst(
        location,
        id,
        buildMLIRValue(inst.vec),
        buildMLIRValue(inst.indices),
        buildMLIRTypeID(inst.result),
)

fun buildCIRVecShuffleOpInst(inst: Op.CIRVecShuffleOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVecShuffleOpInst(
        location,
        id,
        buildMLIRValue(inst.vec1),
        buildMLIRValue(inst.vec2),
        buildMLIRArrayAttr(inst.indices),
        buildMLIRTypeID(inst.result),
)

fun buildCIRVecSplatOpInst(inst: Op.CIRVecSplatOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVecSplatOpInst(
        location,
        id,
        buildMLIRValue(inst.value),
        buildMLIRTypeID(inst.result),
)

fun buildCIRVecTernaryOpInst(inst: Op.CIRVecTernaryOp, id: MLIROpID, location: CIRInstLocation) =
    CIRVecTernaryOpInst(
        location,
        id,
        buildMLIRValue(inst.cond),
        buildMLIRValue(inst.vec1),
        buildMLIRValue(inst.vec2),
        buildMLIRTypeID(inst.result),
)

fun buildCIRAwaitOpInst(inst: Op.CIRAwaitOp, id: MLIROpID, location: CIRInstLocation) =
    CIRAwaitOpInst(
        location,
        id,
        buildCIRAwaitKind(inst.kind),
    )

fun buildCIRBreakOpInst(id: MLIROpID, location: CIRInstLocation) =
    CIRBreakOpInst(location, id)

fun buildCIRCaseOpInst(inst: Op.CIRCaseOp, id: MLIROpID, location: CIRInstLocation) =
    CIRCaseOpInst(
        location,
        id,
        buildMLIRArrayAttr(inst.value),
        buildCIRCaseOpKind(inst.kind),
    )

fun buildCIRConditionOpInst(inst: Op.CIRConditionOp, id: MLIROpID, location: CIRInstLocation) =
    CIRConditionOpInst(
        location,
        id,
        buildMLIRValue(inst.condition),
    )

fun buildCIRContinueOpInst(id: MLIROpID, location: CIRInstLocation) =
    CIRContinueOpInst(location, id)

fun buildCIRDoWhileOpInst(id: MLIROpID, location: CIRInstLocation) =
    CIRDoWhileOpInst(location, id)

fun buildCIRDynamicCastOp(inst: Op.CIRDynamicCastOp, id: MLIROpID, location: CIRInstLocation) =
    CIRDynamicCastOp(
        location,
        id,
        buildMLIRValue(inst.src),
        buildCIRDynamicCastKind(inst.kind),
        if (inst.hasInfo()) buildCIRDynamicCastInfoAttr(inst.info) else null,
        if (inst.hasRelativeLayout()) buildMLIRUnitAttr(inst.relativeLayout) else null,
        buildMLIRTypeID(inst.result)
        )

fun buildCIRForOpInst(id: MLIROpID, location: CIRInstLocation) =
    CIRForOpInst(location, id)

fun buildCIRIfOpInst(inst: Op.CIRIfOp, id: MLIROpID, location: CIRInstLocation) =
    CIRIfOpInst(
        location,
        id,
        buildMLIRValue(inst.condition),
    )

fun buildCIRScopeOpInst(inst: Op.CIRScopeOp, id: MLIROpID, location: CIRInstLocation) =
    CIRScopeOpInst(
        location,
        id,
        if (inst.hasResults()) buildMLIRTypeID(inst.results) else null,
    )

fun buildCIRSwitchOpInst(inst: Op.CIRSwitchOp, id: MLIROpID, location: CIRInstLocation) =
    CIRSwitchOpInst(
        location,
        id,
        buildMLIRValue(inst.condition),
    )

fun buildCIRTryOpInst(inst: Op.CIRTryOp, id: MLIROpID, location: CIRInstLocation) =
    CIRTryOpInst(
        location,
        id,
        if (inst.hasSynthetic()) buildMLIRUnitAttr(inst.synthetic) else null,
        if (inst.hasCleanup()) buildMLIRUnitAttr(inst.cleanup) else null,
        if (inst.hasCatchTypes()) buildMLIRArrayAttr(inst.catchTypes) else null,
    )

fun buildCIRWhileOpInst(id: MLIROpID, location: CIRInstLocation) =
    CIRWhileOpInst(location, id)

fun buildCIRYieldOpInst(inst: Op.CIRYieldOp, id: MLIROpID, location: CIRInstLocation) =
    CIRYieldOpInst(
        location,
        id,
        buildMLIRValueArray(inst.argsList),
    )
