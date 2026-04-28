package org.seqra.ir.api.cir.cfg

import org.seqra.ir.api.common.cfg.*

data class CIRAssignInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,
    override val lhv: MLIRValue,
    override val rhv: CIRExpr,
) : CIRInst, CommonAssignInst

data class CIRAllocExceptionOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val size: MLIRIntegerAttr,

    val addr: MLIRTypeID,
) : CIRInst

data class CIRAllocaOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val dynAllocSize: MLIRValue?,

    val allocaType: MLIRTypeAttr,
    val name: MLIRStringAttr,
    val init: MLIRUnitAttr?,
    val constant: MLIRUnitAttr?,
    val alignment: MLIRIntegerAttr?,
    val annotations: MLIRArrayAttr?,

    val addr: MLIRTypeID,
) : CIRInst

data class CIRAssumeAlignedOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val pointer: MLIRValue,
    val offset: MLIRValue?,

    val alignment: MLIRIntegerAttr,

    val result: MLIRTypeID,
) : CIRInst

data class CIRAssumeOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val predicate: MLIRValue,

    ) : CIRInst

data class CIRAssumeSepStorageOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val ptr1: MLIRValue,
    val ptr2: MLIRValue,

    ) : CIRInst

data class CIRAtomicCmpXchgOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val ptr: MLIRValue,
    val expected: MLIRValue,
    val desired: MLIRValue,

    val succOrder: CIRMemOrder,
    val failOrder: CIRMemOrder,
    val weak: MLIRUnitAttr?,
    val isVolatile: MLIRUnitAttr?,

    val old: MLIRTypeID,
    val cmp: MLIRTypeID,
) : CIRInst

data class CIRAtomicFetchOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val ptr: MLIRValue,
    val value: MLIRValue,

    val binop: CIRAtomicFetchKind,
    val memOrder: CIRMemOrder,
    val isVolatile: MLIRUnitAttr?,
    val fetchFirst: MLIRUnitAttr?,

    val result: MLIRTypeID,
) : CIRInst

data class CIRAtomicXchgOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val ptr: MLIRValue,
    val value: MLIRValue,

    val memOrder: CIRMemOrder,
    val isVolatile: MLIRUnitAttr?,

    val result: MLIRTypeID,
) : CIRInst

data class CIRBinOpOverflowOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val lhs: MLIRValue,
    val rhs: MLIRValue,

    val kind: CIRBinOpOverflowKind,

    val result: MLIRTypeID,
    val overflow: MLIRTypeID,
) : CIRInst

data class CIRBrCondOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val cond: MLIRValue,
    val destOperandsTrue: List<MLIRValue>,
    val destOperandsFalse: List<MLIRValue>,

    val destTrue: MLIRBlockID,
    val destFalse: MLIRBlockID,

    ) : CIRInst

data class CIRBrOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val destOperands: List<MLIRValue>,

    val dest: MLIRBlockID,

    ) : CIRInst

data class CIRCallOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    override val arg_ops: List<MLIRValue>,

    override val exception: MLIRUnitAttr?,
    override val callee: MLIRFlatSymbolRefAttr?,
    override val callingConv: CIRCallingConv,
    override val extraAttrs: CIRExtraFuncAttributesAttr,

    override val result: MLIRTypeID?,

    // Null in indirect calls(see arg#0 for callee ptr)
    override val calleeRef: CIRCalleeRef?,
) : CIRDirectCall, CIRInst

data class CIRCatchParamOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val exception_ptr: MLIRValue?,

    val kind: CIRCatchParamKind?,

    val param: MLIRTypeID?,
) : CIRInst

data class CIRClearCacheOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val begin: MLIRValue,
    val end: MLIRValue,

    ) : CIRInst

data class CIRCopyOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val dst: MLIRValue,
    val src: MLIRValue,

    val isVolatile: MLIRUnitAttr?,
    val tbaa: MLIRArrayAttr?,

    ) : CIRInst

data class CIREhInflightOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val cleanup: MLIRUnitAttr?,
    val symTypeList: MLIRArrayAttr?,

    val exceptionPtr: MLIRTypeID,
    val typeId: MLIRTypeID,
) : CIRInst

data class CIRExpectOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val value: MLIRValue,
    val expected: MLIRValue,

    val prob: MLIRFloatAttr?,

    val result: MLIRTypeID,
) : CIRInst

data class CIRFreeExceptionOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val ptr: MLIRValue,

    ) : CIRInst

data class CIRGetBitfieldOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val addr: MLIRValue,

    val bitfieldInfo: CIRBitfieldInfoAttr,
    val isVolatile: MLIRUnitAttr?,

    val result: MLIRTypeID,
) : CIRInst

data class CIRGetMethodOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val meth: MLIRValue,
    val obj: MLIRValue,

    val callee: MLIRTypeID,
    val adjustedThis: MLIRTypeID,
) : CIRInst

data class CIRGetRuntimeMemberOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val addr: MLIRValue,
    val member: MLIRValue,

    val result: MLIRTypeID,
) : CIRInst

data class CIRLLVMIntrinsicCallOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val arg_ops: List<MLIRValue>,

    val intrinsicName: MLIRStringAttr,

    val result: MLIRTypeID?,
) : CIRInst

data class CIRLoadOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val addr: MLIRValue,

    val isDeref: MLIRUnitAttr?,
    val isVolatile: MLIRUnitAttr?,
    val alignment: MLIRIntegerAttr?,
    val memOrder: CIRMemOrder?,
    val tbaa: MLIRArrayAttr?,

    val result: MLIRTypeID,
) : CIRInst

data class CIRMemChrOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val src: MLIRValue,
    val pattern: MLIRValue,
    val len: MLIRValue,

    val result: MLIRTypeID,
) : CIRInst

data class CIRMemCpyInlineOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val dst: MLIRValue,
    val src: MLIRValue,

    val len: MLIRIntegerAttr,

    ) : CIRInst

data class CIRMemCpyOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val dst: MLIRValue,
    val src: MLIRValue,
    val len: MLIRValue,

    ) : CIRInst

data class CIRMemMoveOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val dst: MLIRValue,
    val src: MLIRValue,
    val len: MLIRValue,

    ) : CIRInst

data class CIRMemSetInlineOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val dst: MLIRValue,
    val value: MLIRValue,

    val len: MLIRIntegerAttr,

    ) : CIRInst

data class CIRMemSetOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val dst: MLIRValue,
    val value: MLIRValue,
    val len: MLIRValue,

    ) : CIRInst

data class CIRPrefetchOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val addr: MLIRValue,

    val locality: MLIRIntegerAttr,
    val isWrite: MLIRUnitAttr?,

    ) : CIRInst

data class CIRResumeOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val exception_ptr: MLIRValue?,
    val type_id: MLIRValue?,

    val rethrow: MLIRUnitAttr?,

    ) : CIRInst

data class CIRReturnOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val input: List<MLIRValue>,

    ) : CIRInst, CIRTerminatingInst

data class CIRSetBitfieldOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val addr: MLIRValue,
    val src: MLIRValue,

    val bitfieldInfo: CIRBitfieldInfoAttr,
    val isVolatile: MLIRUnitAttr?,

    val result: MLIRTypeID,
) : CIRInst

data class CIRStackRestoreOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val ptr: MLIRValue,

    ) : CIRInst

data class CIRStackSaveOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val result: MLIRTypeID,
) : CIRInst

data class CIRStdFindOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val first: MLIRValue,
    val last: MLIRValue,
    val pattern: MLIRValue,

    val originalFn: MLIRFlatSymbolRefAttr,

    val result: MLIRTypeID,
) : CIRInst

data class CIRStoreOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val value: MLIRValue,
    val addr: MLIRValue,

    val isVolatile: MLIRUnitAttr?,
    val alignment: MLIRIntegerAttr?,
    val memOrder: CIRMemOrder?,
    val tbaa: MLIRArrayAttr?,

    ) : CIRInst

data class CIRSwitchFlatOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val condition: MLIRValue,
    val defaultOperands: List<MLIRValue>,
    val caseOperands: List<List<MLIRValue>>,

    val caseValues: MLIRArrayAttr,
    val caseOperandSegments: MLIRDenseI32ArrayAttr,

    val defaultDestination: MLIRBlockID,
    val caseDestinations: List<MLIRBlockID>,

    ) : CIRInst

data class CIRThrowOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val exception_ptr: MLIRValue?,

    val typeInfo: MLIRFlatSymbolRefAttr?,
    val dtor: MLIRFlatSymbolRefAttr?,

    ) : CIRInst, CIRTerminatingInst

data class CIRTrapOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    ) : CIRInst

data class CIRTryCallOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val contOperands: List<MLIRValue>,
    val landingPadOperands: List<MLIRValue>,
    override val arg_ops: List<MLIRValue>,

    override val exception: MLIRUnitAttr? = null, // TODO, sorry, skill issue:(
    override val callee: MLIRFlatSymbolRefAttr?,
    override val callingConv: CIRCallingConv,
    override val extraAttrs: CIRExtraFuncAttributesAttr,

    val cont: MLIRBlockID,
    val landingPad: MLIRBlockID,

    override val result: MLIRTypeID?,

    // Null in indirect calls(see arg#0 for callee ptr)
    override val calleeRef: CIRCalleeRef?,
) : CIRDirectCall, CIRInst

data class CIRUnreachableOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    ) : CIRInst

data class CIRVAArgOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val arg_list: MLIRValue,

    val result: MLIRTypeID,
) : CIRInst

data class CIRVACopyOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val dst_list: MLIRValue,
    val src_list: MLIRValue,

    ) : CIRInst

data class CIRVAEndOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val arg_list: MLIRValue,

    ) : CIRInst

data class CIRVAStartOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val arg_list: MLIRValue,

    ) : CIRInst

data class CIRVecCmpOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val lhs: MLIRValue,
    val rhs: MLIRValue,

    val kind: CIRCmpOpKind,

    val result: MLIRTypeID,
) : CIRInst

data class CIRVecCreateOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val elements: List<MLIRValue>,

    val result: MLIRTypeID,
) : CIRInst

data class CIRVecExtractOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val vec: MLIRValue,
    val index: MLIRValue,

    val result: MLIRTypeID,
) : CIRInst

data class CIRVecInsertOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val vec: MLIRValue,
    val value: MLIRValue,
    val index: MLIRValue,

    val result: MLIRTypeID,
) : CIRInst

data class CIRVecShuffleDynamicOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val vec: MLIRValue,
    val indices: MLIRValue,

    val result: MLIRTypeID,
) : CIRInst

data class CIRVecShuffleOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val vec1: MLIRValue,
    val vec2: MLIRValue,

    val indices: MLIRArrayAttr,

    val result: MLIRTypeID,
) : CIRInst

data class CIRVecSplatOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val value: MLIRValue,

    val result: MLIRTypeID,
) : CIRInst

data class CIRVecTernaryOpInst(
    override val location: CIRInstLocation,
    override val id: MLIROpID,

    val cond: MLIRValue,
    val vec1: MLIRValue,
    val vec2: MLIRValue,

    val result: MLIRTypeID,
) : CIRInst

