package org.seqra.ir.impl.cfg.builder

import org.seqra.ir.api.cir.cfg.*
import org.seqra.ir.impl.grpc.Attr

import java.math.BigDecimal
import java.math.BigInteger

fun buildMLIRAttribute(attr: Attr.MLIRAttribute): MLIRAttribute = when (attr.attributeCase!!) {
    Attr.MLIRAttribute.AttributeCase.ARRAY_ATTR -> buildMLIRArrayAttr(attr.arrayAttr)
    Attr.MLIRAttribute.AttributeCase.DICTIONARY_ATTR -> buildMLIRDictionaryAttr(attr.dictionaryAttr)
    Attr.MLIRAttribute.AttributeCase.FLOAT_ATTR -> buildMLIRFloatAttr(attr.floatAttr)
    Attr.MLIRAttribute.AttributeCase.INTEGER_ATTR -> buildMLIRIntegerAttr(attr.integerAttr)
    Attr.MLIRAttribute.AttributeCase.STRING_ATTR -> buildMLIRStringAttr(attr.stringAttr)
    Attr.MLIRAttribute.AttributeCase.TYPE_ATTR -> buildMLIRTypeAttr(attr.typeAttr)
    Attr.MLIRAttribute.AttributeCase.UNIT_ATTR -> buildMLIRUnitAttr(attr.unitAttr)
    Attr.MLIRAttribute.AttributeCase.NAMED_ATTR -> buildMLIRNamedAttr(attr.namedAttr)
    Attr.MLIRAttribute.AttributeCase.FLAT_SYMBOL_REF_ATTR -> buildMLIRFlatSymbolRefAttr(attr.flatSymbolRefAttr)
    Attr.MLIRAttribute.AttributeCase.DENSE_I32_ARRAY_ATTR -> buildMLIRDenseI32ArrayAttr(attr.denseI32ArrayAttr)
    Attr.MLIRAttribute.AttributeCase.LOCATION -> buildMLIRLocation(attr.location)
    Attr.MLIRAttribute.AttributeCase.ADDRESS_SPACE_ATTR -> buildCIRAddressSpaceAttr(attr.addressSpaceAttr)
    Attr.MLIRAttribute.AttributeCase.ANNOTATION_ATTR -> buildCIRAnnotationAttr(attr.annotationAttr)
    Attr.MLIRAttribute.AttributeCase.BITFIELD_INFO_ATTR -> buildCIRBitfieldInfoAttr(attr.bitfieldInfoAttr)
    Attr.MLIRAttribute.AttributeCase.BOOL_ATTR -> buildCIRBoolAttr(attr.boolAttr)
    Attr.MLIRAttribute.AttributeCase.TBAA_ATTR -> buildCIRTBAAAttr(attr.tbaaAttr)
    Attr.MLIRAttribute.AttributeCase.CATCH_ALL_ATTR -> buildCIRCatchAllAttr(attr.catchAllAttr)
    Attr.MLIRAttribute.AttributeCase.CATCH_UNWIND_ATTR -> buildCIRCatchUnwindAttr(attr.catchUnwindAttr)
    Attr.MLIRAttribute.AttributeCase.CMP_THREE_WAY_INFO_ATTR -> buildCIRCmpThreeWayInfoAttr(attr.cmpThreeWayInfoAttr)
    Attr.MLIRAttribute.AttributeCase.COMPLEX_ATTR -> buildCIRComplexAttr(attr.complexAttr)
    Attr.MLIRAttribute.AttributeCase.CONST_ARRAY_ATTR -> buildCIRConstArrayAttr(attr.constArrayAttr)
    Attr.MLIRAttribute.AttributeCase.CONST_PTR_ATTR -> buildCIRConstPtrAttr(attr.constPtrAttr)
    Attr.MLIRAttribute.AttributeCase.CONST_STRUCT_ATTR -> buildCIRConstStructAttr(attr.constStructAttr)
    Attr.MLIRAttribute.AttributeCase.CONST_VECTOR_ATTR -> buildCIRConstVectorAttr(attr.constVectorAttr)
    Attr.MLIRAttribute.AttributeCase.CONVERGENT_ATTR -> buildCIRConvergentAttr(attr.convergentAttr)
    Attr.MLIRAttribute.AttributeCase.DATA_MEMBER_ATTR -> buildCIRDataMemberAttr(attr.dataMemberAttr)
    Attr.MLIRAttribute.AttributeCase.DYNAMIC_CAST_INFO_ATTR -> buildCIRDynamicCastInfoAttr(attr.dynamicCastInfoAttr)
    Attr.MLIRAttribute.AttributeCase.EXTRA_FUNC_ATTRIBUTES_ATTR -> buildCIRExtraFuncAttributesAttr(attr.extraFuncAttributesAttr)
    Attr.MLIRAttribute.AttributeCase.FP_ATTR -> buildCIRFPAttr(attr.fpAttr)
    Attr.MLIRAttribute.AttributeCase.GLOBAL_ANNOTATION_VALUES_ATTR -> buildCIRGlobalAnnotationValuesAttr(attr.globalAnnotationValuesAttr)
    Attr.MLIRAttribute.AttributeCase.GLOBAL_CTOR_ATTR -> buildCIRGlobalCtorAttr(attr.globalCtorAttr)
    Attr.MLIRAttribute.AttributeCase.GLOBAL_DTOR_ATTR -> buildCIRGlobalDtorAttr(attr.globalDtorAttr)
    Attr.MLIRAttribute.AttributeCase.GLOBAL_VIEW_ATTR -> buildCIRGlobalViewAttr(attr.globalViewAttr)
    Attr.MLIRAttribute.AttributeCase.INACTIVE_UNION_FIELD_ATTR -> buildCIRInactiveUnionFieldAttr(attr.inactiveUnionFieldAttr)
    Attr.MLIRAttribute.AttributeCase.INLINE_ATTR -> buildCIRInlineAttr(attr.inlineAttr)
    Attr.MLIRAttribute.AttributeCase.INT_ATTR -> buildCIRIntAttr(attr.intAttr)
    Attr.MLIRAttribute.AttributeCase.LANG_ATTR -> buildCIRLangAttr(attr.langAttr)
    Attr.MLIRAttribute.AttributeCase.METHOD_ATTR -> buildCIRMethodAttr(attr.methodAttr)
    Attr.MLIRAttribute.AttributeCase.NO_THROW_ATTR -> buildCIRNoThrowAttr(attr.noThrowAttr)
    Attr.MLIRAttribute.AttributeCase.OPEN_CL_KERNEL_ARG_METADATA_ATTR -> buildCIROpenCLKernelArgMetadataAttr(attr.openClKernelArgMetadataAttr)
    Attr.MLIRAttribute.AttributeCase.OPEN_CL_KERNEL_ATTR -> buildCIROpenCLKernelAttr(attr.openClKernelAttr)
    Attr.MLIRAttribute.AttributeCase.OPEN_CL_KERNEL_METADATA_ATTR -> buildCIROpenCLKernelMetadataAttr(attr.openClKernelMetadataAttr)
    Attr.MLIRAttribute.AttributeCase.OPEN_CL_KERNEL_UNIFORM_WORK_GROUP_SIZE_ATTR -> buildCIROpenCLKernelUniformWorkGroupSizeAttr(attr.openClKernelUniformWorkGroupSizeAttr)
    Attr.MLIRAttribute.AttributeCase.OPEN_CL_VERSION_ATTR -> buildCIROpenCLVersionAttr(attr.openClVersionAttr)
    Attr.MLIRAttribute.AttributeCase.OPT_NONE_ATTR -> buildCIROptNoneAttr(attr.optNoneAttr)
    Attr.MLIRAttribute.AttributeCase.STRUCT_LAYOUT_ATTR -> buildCIRStructLayoutAttr(attr.structLayoutAttr)
    Attr.MLIRAttribute.AttributeCase.TYPE_INFO_ATTR -> buildCIRTypeInfoAttr(attr.typeInfoAttr)
    Attr.MLIRAttribute.AttributeCase.UNDEF_ATTR -> buildCIRUndefAttr(attr.undefAttr)
    Attr.MLIRAttribute.AttributeCase.V_TABLE_ATTR -> buildCIRVTableAttr(attr.vTableAttr)
    Attr.MLIRAttribute.AttributeCase.VISIBILITY_ATTR -> buildCIRVisibilityAttr(attr.visibilityAttr)
    Attr.MLIRAttribute.AttributeCase.ZERO_ATTR -> buildCIRZeroAttr(attr.zeroAttr)
    Attr.MLIRAttribute.AttributeCase.ASM_FLAVOR_ATTR -> buildCIRAsmFlavorAttr(attr.asmFlavorAttr)
    Attr.MLIRAttribute.AttributeCase.ATOMIC_FETCH_KIND_ATTR -> buildCIRAtomicFetchKindAttr(attr.atomicFetchKindAttr)
    Attr.MLIRAttribute.AttributeCase.AWAIT_KIND_ATTR -> buildCIRAwaitKindAttr(attr.awaitKindAttr)
    Attr.MLIRAttribute.AttributeCase.BIN_OP_KIND_ATTR -> buildCIRBinOpKindAttr(attr.binOpKindAttr)
    Attr.MLIRAttribute.AttributeCase.BIN_OP_OVERFLOW_KIND_ATTR -> buildCIRBinOpOverflowKindAttr(attr.binOpOverflowKindAttr)
    Attr.MLIRAttribute.AttributeCase.CALLING_CONV_ATTR -> buildCIRCallingConvAttr(attr.callingConvAttr)
    Attr.MLIRAttribute.AttributeCase.CASE_OP_KIND_ATTR -> buildCIRCaseOpKindAttr(attr.caseOpKindAttr)
    Attr.MLIRAttribute.AttributeCase.CAST_KIND_ATTR -> buildCIRCastKindAttr(attr.castKindAttr)
    Attr.MLIRAttribute.AttributeCase.CATCH_PARAM_KIND_ATTR -> buildCIRCatchParamKindAttr(attr.catchParamKindAttr)
    Attr.MLIRAttribute.AttributeCase.CMP_OP_KIND_ATTR -> buildCIRCmpOpKindAttr(attr.cmpOpKindAttr)
    Attr.MLIRAttribute.AttributeCase.CMP_ORDERING_ATTR -> buildCIRCmpOrderingAttr(attr.cmpOrderingAttr)
    Attr.MLIRAttribute.AttributeCase.COMPLEX_BIN_OP_KIND_ATTR -> buildCIRComplexBinOpKindAttr(attr.complexBinOpKindAttr)
    Attr.MLIRAttribute.AttributeCase.COMPLEX_RANGE_KIND_ATTR -> buildCIRComplexRangeKindAttr(attr.complexRangeKindAttr)
    Attr.MLIRAttribute.AttributeCase.DYNAMIC_CAST_KIND_ATTR -> buildCIRDynamicCastKindAttr(attr.dynamicCastKindAttr)
    Attr.MLIRAttribute.AttributeCase.GLOBAL_LINKAGE_KIND_ATTR -> buildCIRGlobalLinkageKindAttr(attr.globalLinkageKindAttr)
    Attr.MLIRAttribute.AttributeCase.INLINE_KIND_ATTR -> buildCIRInlineKindAttr(attr.inlineKindAttr)
    Attr.MLIRAttribute.AttributeCase.MEM_ORDER_ATTR -> buildCIRMemOrderAttr(attr.memOrderAttr)
    Attr.MLIRAttribute.AttributeCase.SIGNED_OVERFLOW_BEHAVIOR_ATTR -> buildCIRSignedOverflowBehaviorAttr(attr.signedOverflowBehaviorAttr)
    Attr.MLIRAttribute.AttributeCase.SIZE_INFO_TYPE_ATTR -> buildCIRSizeInfoTypeAttr(attr.sizeInfoTypeAttr)
    Attr.MLIRAttribute.AttributeCase.SOURCE_LANGUAGE_ATTR -> buildCIRSourceLanguageAttr(attr.sourceLanguageAttr)
    Attr.MLIRAttribute.AttributeCase.TLS_MODEL_ATTR -> buildCIRTLSModelAttr(attr.tlsModelAttr)
    Attr.MLIRAttribute.AttributeCase.UNARY_OP_KIND_ATTR -> buildCIRUnaryOpKindAttr(attr.unaryOpKindAttr)
    Attr.MLIRAttribute.AttributeCase.VISIBILITY_KIND_ATTR -> buildCIRVisibilityKindAttr(attr.visibilityKindAttr)
    Attr.MLIRAttribute.AttributeCase.ATTRIBUTE_NOT_SET -> throw Exception()
}

fun buildMLIRArrayAttr(attr: Attr.MLIRArrayAttr) = MLIRArrayAttr(
    buildMLIRAttributeArray(attr.valueList),
)

fun buildMLIRDictionaryAttr(attr: Attr.MLIRDictionaryAttr) = MLIRDictionaryAttr(
    buildMLIRNamedAttrArray(attr.valueList),
)

fun buildMLIRFloatAttr(attr: Attr.MLIRFloatAttr) = MLIRFloatAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
    BigDecimal(attr.value),
)

fun buildMLIRIntegerAttr(attr: Attr.MLIRIntegerAttr) = MLIRIntegerAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
    BigInteger(attr.value),
)

fun buildMLIRStringAttr(attr: Attr.MLIRStringAttr) = MLIRStringAttr(
    attr.value,
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
)

fun buildMLIRTypeAttr(attr: Attr.MLIRTypeAttr) = MLIRTypeAttr(
    buildMLIRTypeID(attr.value),
)

fun buildMLIRUnitAttr(attr: Attr.MLIRUnitAttr) = MLIRUnitAttr

fun buildMLIRNamedAttr(attr: Attr.MLIRNamedAttr) = MLIRNamedAttr(
    buildMLIRStringAttr(attr.name),
    buildMLIRAttribute(attr.value),
)

fun buildMLIRFlatSymbolRefAttr(attr: Attr.MLIRFlatSymbolRefAttr) = MLIRFlatSymbolRefAttr(
    buildMLIRStringAttr(attr.rootReference),
)

fun buildMLIRDenseI32ArrayAttr(attr: Attr.MLIRDenseI32ArrayAttr) = MLIRDenseI32ArrayAttr(
    attr.size,
    buildI32Array(attr.rawDataList),
)

fun buildMLIRLocation(attr: Attr.MLIRLocation): MLIRLocation = when (attr.locationCase!!) {
    Attr.MLIRLocation.LocationCase.CALL_SITE_LOC -> buildMLIRCallSiteLoc(attr.callSiteLoc)
    Attr.MLIRLocation.LocationCase.FILE_LINE_COL_LOC -> buildMLIRFileLineColLoc(attr.fileLineColLoc)
    Attr.MLIRLocation.LocationCase.FUSED_LOC -> buildMLIRFusedLoc(attr.fusedLoc)
    Attr.MLIRLocation.LocationCase.NAME_LOC -> buildMLIRNameLoc(attr.nameLoc)
    Attr.MLIRLocation.LocationCase.OPAQUE_LOC -> buildMLIROpaqueLoc(attr.opaqueLoc)
    Attr.MLIRLocation.LocationCase.UNKNOWN_LOC -> buildMLIRUnknownLoc(attr.unknownLoc)
    Attr.MLIRLocation.LocationCase.LOCATION_NOT_SET -> throw Exception()
}

fun buildMLIRCallSiteLoc(attr: Attr.MLIRCallSiteLoc) = MLIRCallSiteLoc(
    buildMLIRLocation(attr.callee),
    buildMLIRLocation(attr.caller),
)

fun buildMLIRFileLineColLoc(attr: Attr.MLIRFileLineColLoc) = MLIRFileLineColLoc(
    buildMLIRStringAttr(attr.filename),
    attr.line,
    attr.column,
)

fun buildMLIRFusedLoc(attr: Attr.MLIRFusedLoc) = MLIRFusedLoc(
    buildMLIRLocationArray(attr.locationsList),
    if (attr.hasMetadata()) buildMLIRAttribute(attr.metadata) else null,
)

fun buildMLIRNameLoc(attr: Attr.MLIRNameLoc) = MLIRNameLoc(
    buildMLIRStringAttr(attr.name),
    buildMLIRLocation(attr.childLoc),
)

fun buildMLIROpaqueLoc(attr: Attr.MLIROpaqueLoc) = MLIROpaqueLoc(
    buildMLIRLocation(attr.fallbackLocation),
)

fun buildMLIRUnknownLoc(attr: Attr.MLIRUnknownLoc) = MLIRUnknownLoc

fun buildCIRAddressSpaceAttr(attr: Attr.CIRAddressSpaceAttr) = CIRAddressSpaceAttr(
    attr.value,
)

fun buildCIRAnnotationAttr(attr: Attr.CIRAnnotationAttr) = CIRAnnotationAttr(
    buildMLIRStringAttr(attr.name),
    buildMLIRArrayAttr(attr.args),
)

fun buildCIRBitfieldInfoAttr(attr: Attr.CIRBitfieldInfoAttr) = CIRBitfieldInfoAttr(
    buildMLIRStringAttr(attr.name),
    buildMLIRTypeID(attr.storageType),
    attr.size,
    attr.offset,
    attr.isSigned,
)

fun buildCIRBoolAttr(attr: Attr.CIRBoolAttr) = CIRBoolAttr(
    buildMLIRTypeID(attr.type),
    attr.value,
)

fun buildCIRTBAAAttr(attr: Attr.CIRTBAAAttr) = CIRTBAAAttr

fun buildCIRCatchAllAttr(attr: Attr.CIRCatchAllAttr) = CIRCatchAllAttr

fun buildCIRCatchUnwindAttr(attr: Attr.CIRCatchUnwindAttr) = CIRCatchUnwindAttr

fun buildCIRCmpThreeWayInfoAttr(attr: Attr.CIRCmpThreeWayInfoAttr) = CIRCmpThreeWayInfoAttr(
    buildCIRCmpOrdering(attr.ordering),
    attr.lt,
    attr.eq,
    attr.gt,
    if (attr.hasUnordered()) attr.unordered else null,
)

fun buildCIRComplexAttr(attr: Attr.CIRComplexAttr) = CIRComplexAttr(
    buildMLIRTypeID(attr.type),
    buildMLIRAttribute(attr.real),
    buildMLIRAttribute(attr.imag),
)

fun buildCIRConstArrayAttr(attr: Attr.CIRConstArrayAttr) = CIRConstArrayAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
    buildMLIRAttribute(attr.elts),
    attr.trailingZerosNum,
)

fun buildCIRConstPtrAttr(attr: Attr.CIRConstPtrAttr) = CIRConstPtrAttr(
    buildMLIRTypeID(attr.type),
    buildMLIRIntegerAttr(attr.value),
)

fun buildCIRConstStructAttr(attr: Attr.CIRConstStructAttr) = CIRConstStructAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
    buildMLIRArrayAttr(attr.members),
)

fun buildCIRConstVectorAttr(attr: Attr.CIRConstVectorAttr) = CIRConstVectorAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
    buildMLIRArrayAttr(attr.elts),
)

fun buildCIRConvergentAttr(attr: Attr.CIRConvergentAttr) = CIRConvergentAttr

fun buildCIRDataMemberAttr(attr: Attr.CIRDataMemberAttr) = CIRDataMemberAttr(
    buildMLIRTypeID(attr.type),
    if (attr.hasMemberIndex()) attr.memberIndex else null,
)

fun buildCIRDynamicCastInfoAttr(attr: Attr.CIRDynamicCastInfoAttr) = CIRDynamicCastInfoAttr(
    buildCIRGlobalViewAttr(attr.srcRtti),
    buildCIRGlobalViewAttr(attr.destRtti),
    buildMLIRFlatSymbolRefAttr(attr.runtimeFunc),
    buildMLIRFlatSymbolRefAttr(attr.badCastFunc),
    buildCIRIntAttr(attr.offsetHint),
)

fun buildCIRExtraFuncAttributesAttr(attr: Attr.CIRExtraFuncAttributesAttr) = CIRExtraFuncAttributesAttr(
    buildMLIRDictionaryAttr(attr.elements),
)

fun buildCIRFPAttr(attr: Attr.CIRFPAttr) = CIRFPAttr(
    buildMLIRTypeID(attr.type),
    BigDecimal(attr.value),
)

fun buildCIRGlobalAnnotationValuesAttr(attr: Attr.CIRGlobalAnnotationValuesAttr) = CIRGlobalAnnotationValuesAttr(
    buildMLIRArrayAttr(attr.annotations),
)

fun buildCIRGlobalCtorAttr(attr: Attr.CIRGlobalCtorAttr) = CIRGlobalCtorAttr(
    buildMLIRStringAttr(attr.name),
    attr.priority,
)

fun buildCIRGlobalDtorAttr(attr: Attr.CIRGlobalDtorAttr) = CIRGlobalDtorAttr(
    buildMLIRStringAttr(attr.name),
    attr.priority,
)

fun buildCIRGlobalViewAttr(attr: Attr.CIRGlobalViewAttr) = CIRGlobalViewAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
    buildMLIRFlatSymbolRefAttr(attr.symbol),
    if (attr.hasIndices()) buildMLIRArrayAttr(attr.indices) else null,
)

fun buildCIRInactiveUnionFieldAttr(attr: Attr.CIRInactiveUnionFieldAttr) = CIRInactiveUnionFieldAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
)

fun buildCIRInlineAttr(attr: Attr.CIRInlineAttr) = CIRInlineAttr(
    buildCIRInlineKind(attr.value),
)

fun buildCIRIntAttr(attr: Attr.CIRIntAttr) = CIRIntAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
    BigInteger(attr.value),
)

fun buildCIRLangAttr(attr: Attr.CIRLangAttr) = CIRLangAttr(
    buildCIRSourceLanguage(attr.lang),
)

fun buildCIRMethodAttr(attr: Attr.CIRMethodAttr) = CIRMethodAttr(
    buildMLIRTypeID(attr.type),
    if (attr.hasSymbol()) buildMLIRFlatSymbolRefAttr(attr.symbol) else null,
    if (attr.hasVtableOffset()) attr.vtableOffset else null,
)

fun buildCIRNoThrowAttr(attr: Attr.CIRNoThrowAttr) = CIRNoThrowAttr

fun buildCIROpenCLKernelArgMetadataAttr(attr: Attr.CIROpenCLKernelArgMetadataAttr) = CIROpenCLKernelArgMetadataAttr(
    buildMLIRArrayAttr(attr.addrSpace),
    buildMLIRArrayAttr(attr.accessQual),
    buildMLIRArrayAttr(attr.type),
    buildMLIRArrayAttr(attr.baseType),
    buildMLIRArrayAttr(attr.typeQual),
    if (attr.hasName()) buildMLIRArrayAttr(attr.name) else null,
)

fun buildCIROpenCLKernelAttr(attr: Attr.CIROpenCLKernelAttr) = CIROpenCLKernelAttr

fun buildCIROpenCLKernelMetadataAttr(attr: Attr.CIROpenCLKernelMetadataAttr) = CIROpenCLKernelMetadataAttr(
    if (attr.hasWorkGroupSizeHint()) buildMLIRArrayAttr(attr.workGroupSizeHint) else null,
    if (attr.hasReqdWorkGroupSize()) buildMLIRArrayAttr(attr.reqdWorkGroupSize) else null,
    if (attr.hasVecTypeHint()) buildMLIRTypeAttr(attr.vecTypeHint) else null,
    if (attr.hasVecTypeHintSignedness()) attr.vecTypeHintSignedness else null,
    if (attr.hasIntelReqdSubGroupSize()) buildMLIRIntegerAttr(attr.intelReqdSubGroupSize) else null,
)

fun buildCIROpenCLKernelUniformWorkGroupSizeAttr(attr: Attr.CIROpenCLKernelUniformWorkGroupSizeAttr) = CIROpenCLKernelUniformWorkGroupSizeAttr

fun buildCIROpenCLVersionAttr(attr: Attr.CIROpenCLVersionAttr) = CIROpenCLVersionAttr(
    attr.majorVersion,
    attr.minorVersion,
)

fun buildCIROptNoneAttr(attr: Attr.CIROptNoneAttr) = CIROptNoneAttr

fun buildCIRStructLayoutAttr(attr: Attr.CIRStructLayoutAttr) = CIRStructLayoutAttr(
    attr.size,
    attr.alignment,
    attr.padded,
    buildMLIRTypeID(attr.largestMember),
    buildMLIRArrayAttr(attr.offsets),
)

fun buildCIRTypeInfoAttr(attr: Attr.CIRTypeInfoAttr) = CIRTypeInfoAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
    buildMLIRArrayAttr(attr.data),
)

fun buildCIRUndefAttr(attr: Attr.CIRUndefAttr) = CIRUndefAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
)

fun buildCIRVTableAttr(attr: Attr.CIRVTableAttr) = CIRVTableAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
    buildMLIRArrayAttr(attr.vtableData),
)

fun buildCIRVisibilityAttr(attr: Attr.CIRVisibilityAttr) = CIRVisibilityAttr(
    buildCIRVisibilityKind(attr.value),
)

fun buildCIRZeroAttr(attr: Attr.CIRZeroAttr) = CIRZeroAttr(
    if (attr.hasType()) buildMLIRTypeID(attr.type) else null,
)

fun buildCIRAsmFlavorAttr(attr: Attr.CIRAsmFlavorAttr) = CIRAsmFlavorAttr(
    buildCIRAsmFlavor(attr.value),
)

fun buildCIRAtomicFetchKindAttr(attr: Attr.CIRAtomicFetchKindAttr) = CIRAtomicFetchKindAttr(
    buildCIRAtomicFetchKind(attr.value),
)

fun buildCIRAwaitKindAttr(attr: Attr.CIRAwaitKindAttr) = CIRAwaitKindAttr(
    buildCIRAwaitKind(attr.value),
)

fun buildCIRBinOpKindAttr(attr: Attr.CIRBinOpKindAttr) = CIRBinOpKindAttr(
    buildCIRBinOpKind(attr.value),
)

fun buildCIRBinOpOverflowKindAttr(attr: Attr.CIRBinOpOverflowKindAttr) = CIRBinOpOverflowKindAttr(
    buildCIRBinOpOverflowKind(attr.value),
)

fun buildCIRCallingConvAttr(attr: Attr.CIRCallingConvAttr) = CIRCallingConvAttr(
    buildCIRCallingConv(attr.value),
)

fun buildCIRCaseOpKindAttr(attr: Attr.CIRCaseOpKindAttr) = CIRCaseOpKindAttr(
    buildCIRCaseOpKind(attr.value),
)

fun buildCIRCastKindAttr(attr: Attr.CIRCastKindAttr) = CIRCastKindAttr(
    buildCIRCastKind(attr.value),
)

fun buildCIRCatchParamKindAttr(attr: Attr.CIRCatchParamKindAttr) = CIRCatchParamKindAttr(
    buildCIRCatchParamKind(attr.value),
)

fun buildCIRCmpOpKindAttr(attr: Attr.CIRCmpOpKindAttr) = CIRCmpOpKindAttr(
    buildCIRCmpOpKind(attr.value),
)

fun buildCIRCmpOrderingAttr(attr: Attr.CIRCmpOrderingAttr) = CIRCmpOrderingAttr(
    buildCIRCmpOrdering(attr.value),
)

fun buildCIRComplexBinOpKindAttr(attr: Attr.CIRComplexBinOpKindAttr) = CIRComplexBinOpKindAttr(
    buildCIRComplexBinOpKind(attr.value),
)

fun buildCIRComplexRangeKindAttr(attr: Attr.CIRComplexRangeKindAttr) = CIRComplexRangeKindAttr(
    buildCIRComplexRangeKind(attr.value),
)

fun buildCIRDynamicCastKindAttr(attr: Attr.CIRDynamicCastKindAttr) = CIRDynamicCastKindAttr(
    buildCIRDynamicCastKind(attr.value),
)

fun buildCIRGlobalLinkageKindAttr(attr: Attr.CIRGlobalLinkageKindAttr) = CIRGlobalLinkageKindAttr(
    buildCIRGlobalLinkageKind(attr.value),
)

fun buildCIRInlineKindAttr(attr: Attr.CIRInlineKindAttr) = CIRInlineKindAttr(
    buildCIRInlineKind(attr.value),
)

fun buildCIRMemOrderAttr(attr: Attr.CIRMemOrderAttr) = CIRMemOrderAttr(
    buildCIRMemOrder(attr.value),
)

fun buildCIRSignedOverflowBehaviorAttr(attr: Attr.CIRSignedOverflowBehaviorAttr) = CIRSignedOverflowBehaviorAttr(
    buildCIRSignedOverflowBehavior(attr.value),
)

fun buildCIRSizeInfoTypeAttr(attr: Attr.CIRSizeInfoTypeAttr) = CIRSizeInfoTypeAttr(
    buildCIRSizeInfoType(attr.value),
)

fun buildCIRSourceLanguageAttr(attr: Attr.CIRSourceLanguageAttr) = CIRSourceLanguageAttr(
    buildCIRSourceLanguage(attr.value),
)

fun buildCIRTLSModelAttr(attr: Attr.CIRTLSModelAttr) = CIRTLSModelAttr(
    buildCIRTLSModel(attr.value),
)

fun buildCIRUnaryOpKindAttr(attr: Attr.CIRUnaryOpKindAttr) = CIRUnaryOpKindAttr(
    buildCIRUnaryOpKind(attr.value),
)

fun buildCIRVisibilityKindAttr(attr: Attr.CIRVisibilityKindAttr) = CIRVisibilityKindAttr(
    buildCIRVisibilityKind(attr.value),
)