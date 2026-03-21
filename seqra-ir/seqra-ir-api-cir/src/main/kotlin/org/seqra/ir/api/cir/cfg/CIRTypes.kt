package org.seqra.ir.api.cir.cfg

data class MLIRBFloat16Type(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRComplexType(
    override val id: MLIRTypeID,
    val elementType: MLIRTypeID,
) : MLIRType

data class MLIRFloat4E2M1FNType(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat6E2M3FNType(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat6E3M2FNType(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat8E3M4Type(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat8E4M3Type(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat8E4M3B11FNUZType(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat8E4M3FNType(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat8E4M3FNUZType(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat8E5M2Type(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat8E5M2FNUZType(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat8E8M0FNUType(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat16Type(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat32Type(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat64Type(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat80Type(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloat128Type(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFloatTF32Type(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRFunctionType(
    override val id: MLIRTypeID,
    val inputs: ArrayList<MLIRTypeID>,
    val results: ArrayList<MLIRTypeID>,
) : MLIRType

data class MLIRIndexType(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIRIntegerType(
    override val id: MLIRTypeID,
    val width: Int,
    val signedness: MLIRSignednessSemantics,
) : MLIRType

data class MLIRMemRefType(
    override val id: MLIRTypeID,
    val shape: ArrayList<Long>,
    val elementType: MLIRTypeID,
    val layout: MLIRAttribute,
    val memorySpace: MLIRAttribute,
) : MLIRType

data class MLIRNoneType(
    override val id: MLIRTypeID,
) : MLIRType

data class MLIROpaqueType(
    override val id: MLIRTypeID,
    val dialectNamespace: MLIRStringAttr,
    val typeData: String,
) : MLIRType

data class MLIRRankedTensorType(
    override val id: MLIRTypeID,
    val shape: ArrayList<Long>,
    val elementType: MLIRTypeID,
    val encoding: MLIRAttribute,
) : MLIRType

data class MLIRTupleType(
    override val id: MLIRTypeID,
    val types: ArrayList<MLIRTypeID>,
) : MLIRType

data class MLIRUnrankedMemRefType(
    override val id: MLIRTypeID,
    val elementType: MLIRTypeID,
    val memorySpace: MLIRAttribute,
) : MLIRType

data class MLIRUnrankedTensorType(
    override val id: MLIRTypeID,
    val elementType: MLIRTypeID,
) : MLIRType

data class MLIRVectorType(
    override val id: MLIRTypeID,
    val shape: ArrayList<Long>,
    val elementType: MLIRTypeID,
    val scalableDims: ArrayList<Boolean>,
) : MLIRType

data class CIRArrayType(
    override val id: MLIRTypeID,
    val eltType: MLIRTypeID,
    val size: Long,
) : MLIRType

data class CIRBFloat16Type(
    override val id: MLIRTypeID,
) : MLIRType

data class CIRBoolType(
    override val id: MLIRTypeID,
) : MLIRType

data class CIRComplexType(
    override val id: MLIRTypeID,
    val elementTy: MLIRTypeID,
) : MLIRType

data class CIRDataMemberType(
    override val id: MLIRTypeID,
    val memberTy: MLIRTypeID,
    val clsTy: MLIRTypeID,
) : MLIRType

data class CIRDoubleType(
    override val id: MLIRTypeID,
) : MLIRType

data class CIRExceptionType(
    override val id: MLIRTypeID,
) : MLIRType

data class CIRFP16Type(
    override val id: MLIRTypeID,
) : MLIRType

data class CIRFP80Type(
    override val id: MLIRTypeID,
) : MLIRType

data class CIRFP128Type(
    override val id: MLIRTypeID,
) : MLIRType

data class CIRFuncType(
    override val id: MLIRTypeID,
    val inputs: ArrayList<MLIRTypeID>,
    val returnType: MLIRTypeID,
    val varArg: Boolean,
) : MLIRType

data class CIRIntType(
    override val id: MLIRTypeID,
    val width: Int,
    val isSigned: Boolean,
) : MLIRType

data class CIRLongDoubleType(
    override val id: MLIRTypeID,
    val underlying: MLIRTypeID,
) : MLIRType

data class CIRMethodType(
    override val id: MLIRTypeID,
    val memberFuncTy: MLIRTypeID,
    val clsTy: MLIRTypeID,
) : MLIRType

data class CIRPointerType(
    override val id: MLIRTypeID,
    val pointee: MLIRTypeID,
    val addrSpace: MLIRAttribute?,
) : MLIRType

data class CIRSingleType(
    override val id: MLIRTypeID,
) : MLIRType

data class CIRVectorType(
    override val id: MLIRTypeID,
    val eltType: MLIRTypeID,
    val size: Long,
) : MLIRType

data class CIRVoidType(
    override val id: MLIRTypeID,
) : MLIRType

data class CIRStructType(
    override val id: MLIRTypeID,
    val members: List<MLIRTypeID>,
    val name: MLIRStringAttr,
    val incomplete: Boolean,
    val packed: Boolean,
    val kind: CIRRecordKind
) : MLIRType
