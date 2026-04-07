package org.seqra.ir.impl.cfg.builder

import org.seqra.ir.api.cir.cfg.*
import org.seqra.ir.impl.grpc.Type

fun buildMLIRType(type: Type.MLIRType): MLIRType {
    val id = buildMLIRTypeID(type.id)
    val builtType: MLIRType = when (type.typeCase!!) {
        Type.MLIRType.TypeCase.MLIRB_FLOAT16_TYPE -> buildMLIRBFloat16Type(id, type.mlirbFloat16Type)
        Type.MLIRType.TypeCase.MLIR_COMPLEX_TYPE -> buildMLIRComplexType(id, type.mlirComplexType)
        Type.MLIRType.TypeCase.MLIR_FLOAT4_E2_M1_FN_TYPE -> buildMLIRFloat4E2M1FNType(id, type.mlirFloat4E2M1FnType)
        Type.MLIRType.TypeCase.MLIR_FLOAT6_E2_M3_FN_TYPE -> buildMLIRFloat6E2M3FNType(id, type.mlirFloat6E2M3FnType)
        Type.MLIRType.TypeCase.MLIR_FLOAT6_E3_M2_FN_TYPE -> buildMLIRFloat6E3M2FNType(id, type.mlirFloat6E3M2FnType)
        Type.MLIRType.TypeCase.MLIR_FLOAT8_E3_M4_TYPE -> buildMLIRFloat8E3M4Type(id, type.mlirFloat8E3M4Type)
        Type.MLIRType.TypeCase.MLIR_FLOAT8_E4_M3_TYPE -> buildMLIRFloat8E4M3Type(id, type.mlirFloat8E4M3Type)
        Type.MLIRType.TypeCase.MLIR_FLOAT8_E4_M3_B11_FNUZ_TYPE -> buildMLIRFloat8E4M3B11FNUZType(id, type.mlirFloat8E4M3B11FnuzType)
        Type.MLIRType.TypeCase.MLIR_FLOAT8_E4_M3_FN_TYPE -> buildMLIRFloat8E4M3FNType(id, type.mlirFloat8E4M3FnType)
        Type.MLIRType.TypeCase.MLIR_FLOAT8_E4_M3_FNUZ_TYPE -> buildMLIRFloat8E4M3FNUZType(id, type.mlirFloat8E4M3FnuzType)
        Type.MLIRType.TypeCase.MLIR_FLOAT8_E5_M2_TYPE -> buildMLIRFloat8E5M2Type(id, type.mlirFloat8E5M2Type)
        Type.MLIRType.TypeCase.MLIR_FLOAT8_E5_M2_FNUZ_TYPE -> buildMLIRFloat8E5M2FNUZType(id, type.mlirFloat8E5M2FnuzType)
        Type.MLIRType.TypeCase.MLIR_FLOAT8_E8_M0_FNU_TYPE -> buildMLIRFloat8E8M0FNUType(id, type.mlirFloat8E8M0FnuType)
        Type.MLIRType.TypeCase.MLIR_FLOAT16_TYPE -> buildMLIRFloat16Type(id, type.mlirFloat16Type)
        Type.MLIRType.TypeCase.MLIR_FLOAT32_TYPE -> buildMLIRFloat32Type(id, type.mlirFloat32Type)
        Type.MLIRType.TypeCase.MLIR_FLOAT64_TYPE -> buildMLIRFloat64Type(id, type.mlirFloat64Type)
        Type.MLIRType.TypeCase.MLIR_FLOAT80_TYPE -> buildMLIRFloat80Type(id, type.mlirFloat80Type)
        Type.MLIRType.TypeCase.MLIR_FLOAT128_TYPE -> buildMLIRFloat128Type(id, type.mlirFloat128Type)
        Type.MLIRType.TypeCase.MLIR_FLOAT_TF32_TYPE -> buildMLIRFloatTF32Type(id, type.mlirFloatTf32Type)
        Type.MLIRType.TypeCase.MLIR_FUNCTION_TYPE -> buildMLIRFunctionType(id, type.mlirFunctionType)
        Type.MLIRType.TypeCase.MLIR_INDEX_TYPE -> buildMLIRIndexType(id, type.mlirIndexType)
        Type.MLIRType.TypeCase.MLIR_INTEGER_TYPE -> buildMLIRIntegerType(id, type.mlirIntegerType)
        Type.MLIRType.TypeCase.MLIR_MEM_REF_TYPE -> buildMLIRMemRefType(id, type.mlirMemRefType)
        Type.MLIRType.TypeCase.MLIR_NONE_TYPE -> buildMLIRNoneType(id, type.mlirNoneType)
        Type.MLIRType.TypeCase.MLIR_OPAQUE_TYPE -> buildMLIROpaqueType(id, type.mlirOpaqueType)
        Type.MLIRType.TypeCase.MLIR_RANKED_TENSOR_TYPE -> buildMLIRRankedTensorType(id, type.mlirRankedTensorType)
        Type.MLIRType.TypeCase.MLIR_TUPLE_TYPE -> buildMLIRTupleType(id, type.mlirTupleType)
        Type.MLIRType.TypeCase.MLIR_UNRANKED_MEM_REF_TYPE -> buildMLIRUnrankedMemRefType(id, type.mlirUnrankedMemRefType)
        Type.MLIRType.TypeCase.MLIR_UNRANKED_TENSOR_TYPE -> buildMLIRUnrankedTensorType(id, type.mlirUnrankedTensorType)
        Type.MLIRType.TypeCase.MLIR_VECTOR_TYPE -> buildMLIRVectorType(id, type.mlirVectorType)
        Type.MLIRType.TypeCase.CIR_ARRAY_TYPE -> buildCIRArrayType(id, type.cirArrayType)
        Type.MLIRType.TypeCase.CIRB_FLOAT16_TYPE -> buildCIRBFloat16Type(id, type.cirbFloat16Type)
        Type.MLIRType.TypeCase.CIR_BOOL_TYPE -> buildCIRBoolType(id, type.cirBoolType)
        Type.MLIRType.TypeCase.CIR_COMPLEX_TYPE -> buildCIRComplexType(id, type.cirComplexType)
        Type.MLIRType.TypeCase.CIR_DATA_MEMBER_TYPE -> buildCIRDataMemberType(id, type.cirDataMemberType)
        Type.MLIRType.TypeCase.CIR_DOUBLE_TYPE -> buildCIRDoubleType(id, type.cirDoubleType)
        Type.MLIRType.TypeCase.CIR_EXCEPTION_TYPE -> buildCIRExceptionType(id, type.cirExceptionType)
        Type.MLIRType.TypeCase.CIRFP16_TYPE -> buildCIRFP16Type(id, type.cirfp16Type)
        Type.MLIRType.TypeCase.CIRFP80_TYPE -> buildCIRFP80Type(id, type.cirfp80Type)
        Type.MLIRType.TypeCase.CIRFP128_TYPE -> buildCIRFP128Type(id, type.cirfp128Type)
        Type.MLIRType.TypeCase.CIR_FUNC_TYPE -> buildCIRFuncType(id, type.cirFuncType)
        Type.MLIRType.TypeCase.CIR_INT_TYPE -> buildCIRIntType(id, type.cirIntType)
        Type.MLIRType.TypeCase.CIR_LONG_DOUBLE_TYPE -> buildCIRLongDoubleType(id, type.cirLongDoubleType)
        Type.MLIRType.TypeCase.CIR_METHOD_TYPE -> buildCIRMethodType(id, type.cirMethodType)
        Type.MLIRType.TypeCase.CIR_POINTER_TYPE -> buildCIRPointerType(id, type.cirPointerType)
        Type.MLIRType.TypeCase.CIR_SINGLE_TYPE -> buildCIRSingleType(id, type.cirSingleType)
        Type.MLIRType.TypeCase.CIR_VECTOR_TYPE -> buildCIRVectorType(id, type.cirVectorType)
        Type.MLIRType.TypeCase.CIR_VOID_TYPE -> buildCIRVoidType(id, type.cirVoidType)
        Type.MLIRType.TypeCase.CIR_STRUCT_TYPE -> buildCIRStructType(id, type.cirStructType)
        Type.MLIRType.TypeCase.TYPE_NOT_SET -> throw Exception()
    }
    return builtType
}

fun buildMLIRBFloat16Type(id: MLIRTypeID, type: Type.MLIRBFloat16Type) =
    MLIRBFloat16Type(
        id,
    )

fun buildMLIRComplexType(id: MLIRTypeID, type: Type.MLIRComplexType) =
    MLIRComplexType(
        id,
        buildMLIRTypeID(type.elementType),
    )

fun buildMLIRFloat4E2M1FNType(id: MLIRTypeID, type: Type.MLIRFloat4E2M1FNType) =
    MLIRFloat4E2M1FNType(
        id,
    )

fun buildMLIRFloat6E2M3FNType(id: MLIRTypeID, type: Type.MLIRFloat6E2M3FNType) =
    MLIRFloat6E2M3FNType(
        id,
    )

fun buildMLIRFloat6E3M2FNType(id: MLIRTypeID, type: Type.MLIRFloat6E3M2FNType) =
    MLIRFloat6E3M2FNType(
        id,
    )

fun buildMLIRFloat8E3M4Type(id: MLIRTypeID, type: Type.MLIRFloat8E3M4Type) =
    MLIRFloat8E3M4Type(
        id,
    )

fun buildMLIRFloat8E4M3Type(id: MLIRTypeID, type: Type.MLIRFloat8E4M3Type) =
    MLIRFloat8E4M3Type(
        id,
    )

fun buildMLIRFloat8E4M3B11FNUZType(id: MLIRTypeID, type: Type.MLIRFloat8E4M3B11FNUZType) =
    MLIRFloat8E4M3B11FNUZType(
        id,
    )

fun buildMLIRFloat8E4M3FNType(id: MLIRTypeID, type: Type.MLIRFloat8E4M3FNType) =
    MLIRFloat8E4M3FNType(
        id,
    )

fun buildMLIRFloat8E4M3FNUZType(id: MLIRTypeID, type: Type.MLIRFloat8E4M3FNUZType) =
    MLIRFloat8E4M3FNUZType(
        id,
    )

fun buildMLIRFloat8E5M2Type(id: MLIRTypeID, type: Type.MLIRFloat8E5M2Type) =
    MLIRFloat8E5M2Type(
        id,
    )

fun buildMLIRFloat8E5M2FNUZType(id: MLIRTypeID, type: Type.MLIRFloat8E5M2FNUZType) =
    MLIRFloat8E5M2FNUZType(
        id,
    )

fun buildMLIRFloat8E8M0FNUType(id: MLIRTypeID, type: Type.MLIRFloat8E8M0FNUType) =
    MLIRFloat8E8M0FNUType(
        id,
    )

fun buildMLIRFloat16Type(id: MLIRTypeID, type: Type.MLIRFloat16Type) =
    MLIRFloat16Type(
        id,
    )

fun buildMLIRFloat32Type(id: MLIRTypeID, type: Type.MLIRFloat32Type) =
    MLIRFloat32Type(
        id,
    )

fun buildMLIRFloat64Type(id: MLIRTypeID, type: Type.MLIRFloat64Type) =
    MLIRFloat64Type(
        id,
    )

fun buildMLIRFloat80Type(id: MLIRTypeID, type: Type.MLIRFloat80Type) =
    MLIRFloat80Type(
        id,
    )

fun buildMLIRFloat128Type(id: MLIRTypeID, type: Type.MLIRFloat128Type) =
    MLIRFloat128Type(
        id,
    )

fun buildMLIRFloatTF32Type(id: MLIRTypeID, type: Type.MLIRFloatTF32Type) =
    MLIRFloatTF32Type(
        id,
    )

fun buildMLIRFunctionType(id: MLIRTypeID, type: Type.MLIRFunctionType) =
    MLIRFunctionType(
        id,
        buildMLIRTypeIDArray(type.inputsList),
        buildMLIRTypeIDArray(type.resultsList),
    )

fun buildMLIRIndexType(id: MLIRTypeID, type: Type.MLIRIndexType) =
    MLIRIndexType(
        id,
    )

fun buildMLIRIntegerType(id: MLIRTypeID, type: Type.MLIRIntegerType) =
    MLIRIntegerType(
        id,
        type.width,
        buildMLIRSignednessSemantics(type.signedness),
    )

fun buildMLIRMemRefType(id: MLIRTypeID, type: Type.MLIRMemRefType) =
    MLIRMemRefType(
        id,
        buildI64Array(type.shapeList),
        buildMLIRTypeID(type.elementType),
        buildMLIRAttribute(type.layout),
        buildMLIRAttribute(type.memorySpace),
    )

fun buildMLIRNoneType(id: MLIRTypeID, type: Type.MLIRNoneType) =
    MLIRNoneType(
        id,
    )

fun buildMLIROpaqueType(id: MLIRTypeID, type: Type.MLIROpaqueType) =
    MLIROpaqueType(
        id,
        buildMLIRStringAttr(type.dialectNamespace),
        type.typeData,
    )

fun buildMLIRRankedTensorType(id: MLIRTypeID, type: Type.MLIRRankedTensorType) =
    MLIRRankedTensorType(
        id,
        buildI64Array(type.shapeList),
        buildMLIRTypeID(type.elementType),
        buildMLIRAttribute(type.encoding),
    )

fun buildMLIRTupleType(id: MLIRTypeID, type: Type.MLIRTupleType) =
    MLIRTupleType(
        id,
        buildMLIRTypeIDArray(type.typesList),
    )

fun buildMLIRUnrankedMemRefType(id: MLIRTypeID, type: Type.MLIRUnrankedMemRefType) =
    MLIRUnrankedMemRefType(
        id,
        buildMLIRTypeID(type.elementType),
        buildMLIRAttribute(type.memorySpace),
    )

fun buildMLIRUnrankedTensorType(id: MLIRTypeID, type: Type.MLIRUnrankedTensorType) =
    MLIRUnrankedTensorType(
        id,
        buildMLIRTypeID(type.elementType),
    )

fun buildMLIRVectorType(id: MLIRTypeID, type: Type.MLIRVectorType) =
    MLIRVectorType(
        id,
        buildI64Array(type.shapeList),
        buildMLIRTypeID(type.elementType),
        buildBooleanArray(type.scalableDimsList),
    )

fun buildCIRArrayType(id: MLIRTypeID, type: Type.CIRArrayType) =
    CIRArrayType(
        id,
        buildMLIRTypeID(type.eltType),
        type.size,
    )

fun buildCIRBFloat16Type(id: MLIRTypeID, type: Type.CIRBFloat16Type) =
    CIRBFloat16Type(
        id,
    )

fun buildCIRBoolType(id: MLIRTypeID, type: Type.CIRBoolType) =
    CIRBoolType(
        id,
    )

fun buildCIRComplexType(id: MLIRTypeID, type: Type.CIRComplexType) =
    CIRComplexType(
        id,
        buildMLIRTypeID(type.elementTy),
    )

fun buildCIRDataMemberType(id: MLIRTypeID, type: Type.CIRDataMemberType) =
    CIRDataMemberType(
        id,
        buildMLIRTypeID(type.memberTy),
        buildMLIRTypeID(type.clsTy),
    )

fun buildCIRDoubleType(id: MLIRTypeID, type: Type.CIRDoubleType) =
    CIRDoubleType(
        id,
    )

fun buildCIRExceptionType(id: MLIRTypeID, type: Type.CIRExceptionType) =
    CIRExceptionType(
        id,
    )

fun buildCIRFP16Type(id: MLIRTypeID, type: Type.CIRFP16Type) =
    CIRFP16Type(
        id,
    )

fun buildCIRFP80Type(id: MLIRTypeID, type: Type.CIRFP80Type) =
    CIRFP80Type(
        id,
    )

fun buildCIRFP128Type(id: MLIRTypeID, type: Type.CIRFP128Type) =
    CIRFP128Type(
        id,
    )

fun buildCIRFuncType(id: MLIRTypeID, type: Type.CIRFuncType) =
    CIRFuncType(
        id,
        buildMLIRTypeIDArray(type.inputsList),
        buildMLIRTypeID(type.returnType),
        type.varArg,
    )

fun buildCIRIntType(id: MLIRTypeID, type: Type.CIRIntType) =
    CIRIntType(
        id,
        type.width,
        type.isSigned,
    )

fun buildCIRLongDoubleType(id: MLIRTypeID, type: Type.CIRLongDoubleType) =
    CIRLongDoubleType(
        id,
        buildMLIRTypeID(type.underlying),
    )

fun buildCIRMethodType(id: MLIRTypeID, type: Type.CIRMethodType) =
    CIRMethodType(
        id,
        buildMLIRTypeID(type.memberFuncTy),
        buildMLIRTypeID(type.clsTy),
    )

fun buildCIRPointerType(id: MLIRTypeID, type: Type.CIRPointerType) =
    CIRPointerType(
        id,
        buildMLIRTypeID(type.pointee),
        if (type.hasAddrSpace()) buildMLIRAttribute(type.addrSpace) else null,
    )

fun buildCIRSingleType(id: MLIRTypeID, type: Type.CIRSingleType) =
    CIRSingleType(
        id,
    )

fun buildCIRVectorType(id: MLIRTypeID, type: Type.CIRVectorType) =
    CIRVectorType(
        id,
        buildMLIRTypeID(type.eltType),
        type.size,
    )

fun buildCIRVoidType(id: MLIRTypeID, type: Type.CIRVoidType) =
    CIRVoidType(
        id,
    )

fun buildCIRStructType(id: MLIRTypeID, type: Type.CIRStructType) =
    CIRStructType(
        id,
        buildMLIRTypeIDArray(type.membersList),
        buildMLIRStringAttr(type.name),
        type.incomplete,
        type.packed,
        buildCIRRecordKind(type.kind),
    )
