package org.seqra.ir.api.cir.cfg

import java.math.BigDecimal
import java.math.BigInteger

interface MLIRAttribute

data class MLIRArrayAttr(
    val value: ArrayList<MLIRAttribute>,
) : MLIRAttribute

data class MLIRDictionaryAttr(
    val value: ArrayList<MLIRNamedAttr>,
) : MLIRAttribute

data class MLIRFloatAttr(
    val type: MLIRTypeID?,
    val value: BigDecimal,
) : MLIRAttribute

data class MLIRIntegerAttr(
    val type: MLIRTypeID?,
    val value: BigInteger,
) : MLIRAttribute

data class MLIRStringAttr(
    val value: String,
    val type: MLIRTypeID?,
) : MLIRAttribute

data class MLIRTypeAttr(
    val value: MLIRTypeID,
) : MLIRAttribute

object MLIRUnitAttr : MLIRAttribute

data class MLIRNamedAttr(
    val name: MLIRStringAttr,
    val value: MLIRAttribute,
) : MLIRAttribute

data class MLIRFlatSymbolRefAttr(
    val rootReference: MLIRStringAttr,
) : MLIRAttribute

data class MLIRDenseI32ArrayAttr(
    val size: Long,
    val rawData: ArrayList<Int>,
) : MLIRAttribute

interface MLIRLocation : MLIRAttribute

data class MLIRCallSiteLoc(
    val callee: MLIRLocation,
    val caller: MLIRLocation,
) : MLIRLocation {
    override fun toString(): String = "(call of $callee from $caller)"
}

data class MLIRFileLineColLoc(
    val filename: MLIRStringAttr,
    val line: Int,
    val column: Int,
) : MLIRLocation {
    override fun toString(): String = "(${filename.value}:$line:$column)"
}

data class MLIRFusedLoc(
    val locations: ArrayList<MLIRLocation>,
    val metadata: MLIRAttribute?,
) : MLIRLocation {
    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("(fused: ")
        for (loc in locations) {
            builder.append(loc.toString())
        }
        builder.append(")")
        return builder.toString()
    }
}

data class MLIRNameLoc(
    val name: MLIRStringAttr,
    val childLoc: MLIRLocation,
) : MLIRLocation {
    override fun toString(): String = "(${name.value} = $childLoc)"
}

data class MLIROpaqueLoc(
    val fallbackLocation: MLIRLocation,
) : MLIRLocation {
    override fun toString(): String = "(opaque, fallback: $fallbackLocation)"
}

object MLIRUnknownLoc : MLIRLocation {
    override fun toString(): String = "(unknown)"
}

data class CIRAddressSpaceAttr(
    val value: Int,
) : MLIRAttribute

data class CIRAnnotationAttr(
    val name: MLIRStringAttr,
    val args: MLIRArrayAttr,
) : MLIRAttribute

data class CIRBitfieldInfoAttr(
    val name: MLIRStringAttr,
    val storageType: MLIRTypeID,
    val size: Long,
    val offset: Long,
    val isSigned: Boolean,
) : MLIRAttribute

data class CIRBoolAttr(
    val type: MLIRTypeID,
    val value: Boolean,
) : MLIRAttribute

object CIRTBAAAttr : MLIRAttribute

object CIRCatchAllAttr : MLIRAttribute

object CIRCatchUnwindAttr : MLIRAttribute

data class CIRCmpThreeWayInfoAttr(
    val ordering: CIRCmpOrdering,
    val lt: Long,
    val eq: Long,
    val gt: Long,
    val unordered: Long?,
) : MLIRAttribute

data class CIRComplexAttr(
    val type: MLIRTypeID,
    val real: MLIRAttribute,
    val imag: MLIRAttribute,
) : MLIRAttribute

data class CIRConstArrayAttr(
    val type: MLIRTypeID?,
    val elts: MLIRAttribute,
    val trailingZerosNum: Int,
) : MLIRAttribute

data class CIRConstPtrAttr(
    val type: MLIRTypeID,
    val value: MLIRIntegerAttr,
) : MLIRAttribute

data class CIRConstStructAttr(
    val type: MLIRTypeID?,
    val members: MLIRArrayAttr,
) : MLIRAttribute

data class CIRConstVectorAttr(
    val type: MLIRTypeID?,
    val elts: MLIRArrayAttr,
) : MLIRAttribute

object CIRConvergentAttr : MLIRAttribute

data class CIRDataMemberAttr(
    val type: MLIRTypeID,
    val memberIndex: Int?,
) : MLIRAttribute

data class CIRDynamicCastInfoAttr(
    val srcRtti: CIRGlobalViewAttr,
    val destRtti: CIRGlobalViewAttr,
    val runtimeFunc: MLIRFlatSymbolRefAttr,
    val badCastFunc: MLIRFlatSymbolRefAttr,
    val offsetHint: CIRIntAttr,
) : MLIRAttribute

data class CIRExtraFuncAttributesAttr(
    val elements: MLIRDictionaryAttr,
) : MLIRAttribute

data class CIRFPAttr(
    val type: MLIRTypeID,
    val value: BigDecimal,
) : MLIRAttribute

data class CIRGlobalAnnotationValuesAttr(
    val annotations: MLIRArrayAttr,
) : MLIRAttribute

data class CIRGlobalCtorAttr(
    val name: MLIRStringAttr,
    val priority: Int,
) : MLIRAttribute

data class CIRGlobalDtorAttr(
    val name: MLIRStringAttr,
    val priority: Int,
) : MLIRAttribute

data class CIRGlobalViewAttr(
    val type: MLIRTypeID?,
    val symbol: MLIRFlatSymbolRefAttr,
    val indices: MLIRArrayAttr?,
) : MLIRAttribute

data class CIRInactiveUnionFieldAttr(
    val type: MLIRTypeID?,
) : MLIRAttribute

data class CIRInlineAttr(
    val value: CIRInlineKind,
) : MLIRAttribute

data class CIRIntAttr(
    val type: MLIRTypeID?,
    val value: BigInteger,
) : MLIRAttribute

data class CIRLangAttr(
    val lang: CIRSourceLanguage,
) : MLIRAttribute

data class CIRMethodAttr(
    val type: MLIRTypeID,
    val symbol: MLIRFlatSymbolRefAttr?,
    val vtableOffset: Long?,
) : MLIRAttribute

object CIRNoThrowAttr : MLIRAttribute

data class CIROpenCLKernelArgMetadataAttr(
    val addrSpace: MLIRArrayAttr,
    val accessQual: MLIRArrayAttr,
    val type: MLIRArrayAttr,
    val baseType: MLIRArrayAttr,
    val typeQual: MLIRArrayAttr,
    val name: MLIRArrayAttr?,
) : MLIRAttribute

object CIROpenCLKernelAttr : MLIRAttribute

data class CIROpenCLKernelMetadataAttr(
    val workGroupSizeHint: MLIRArrayAttr?,
    val reqdWorkGroupSize: MLIRArrayAttr?,
    val vecTypeHint: MLIRTypeAttr?,
    val vecTypeHintSignedness: Boolean?,
    val intelReqdSubGroupSize: MLIRIntegerAttr?,
) : MLIRAttribute

object CIROpenCLKernelUniformWorkGroupSizeAttr : MLIRAttribute

data class CIROpenCLVersionAttr(
    val majorVersion: Int,
    val minorVersion: Int,
) : MLIRAttribute

object CIROptNoneAttr : MLIRAttribute

data class CIRStructLayoutAttr(
    val size: Int,
    val alignment: Int,
    val padded: Boolean,
    val largestMember: MLIRTypeID,
    val offsets: MLIRArrayAttr,
) : MLIRAttribute

data class CIRTypeInfoAttr(
    val type: MLIRTypeID?,
    val data: MLIRArrayAttr,
) : MLIRAttribute

data class CIRUndefAttr(
    val type: MLIRTypeID?,
) : MLIRAttribute

data class CIRVTableAttr(
    val type: MLIRTypeID?,
    val vtableData: MLIRArrayAttr,
) : MLIRAttribute

data class CIRVisibilityAttr(
    val value: CIRVisibilityKind,
) : MLIRAttribute

data class CIRZeroAttr(
    val type: MLIRTypeID?,
) : MLIRAttribute

data class CIRAsmFlavorAttr(
    val value: CIRAsmFlavor,
) : MLIRAttribute

data class CIRAtomicFetchKindAttr(
    val value: CIRAtomicFetchKind,
) : MLIRAttribute

data class CIRAwaitKindAttr(
    val value: CIRAwaitKind,
) : MLIRAttribute

data class CIRBinOpKindAttr(
    val value: CIRBinOpKind,
) : MLIRAttribute

data class CIRBinOpOverflowKindAttr(
    val value: CIRBinOpOverflowKind,
) : MLIRAttribute

data class CIRCallingConvAttr(
    val value: CIRCallingConv,
) : MLIRAttribute

data class CIRCaseOpKindAttr(
    val value: CIRCaseOpKind,
) : MLIRAttribute

data class CIRCastKindAttr(
    val value: CIRCastKind,
) : MLIRAttribute

data class CIRCatchParamKindAttr(
    val value: CIRCatchParamKind,
) : MLIRAttribute

data class CIRCmpOpKindAttr(
    val value: CIRCmpOpKind,
) : MLIRAttribute

data class CIRCmpOrderingAttr(
    val value: CIRCmpOrdering,
) : MLIRAttribute

data class CIRComplexBinOpKindAttr(
    val value: CIRComplexBinOpKind,
) : MLIRAttribute

data class CIRComplexRangeKindAttr(
    val value: CIRComplexRangeKind,
) : MLIRAttribute

data class CIRDynamicCastKindAttr(
    val value: CIRDynamicCastKind,
) : MLIRAttribute

data class CIRGlobalLinkageKindAttr(
    val value: CIRGlobalLinkageKind,
) : MLIRAttribute

data class CIRInlineKindAttr(
    val value: CIRInlineKind,
) : MLIRAttribute

data class CIRMemOrderAttr(
    val value: CIRMemOrder,
) : MLIRAttribute

data class CIRSignedOverflowBehaviorAttr(
    val value: CIRSignedOverflowBehavior,
) : MLIRAttribute

data class CIRSizeInfoTypeAttr(
    val value: CIRSizeInfoType,
) : MLIRAttribute

data class CIRSourceLanguageAttr(
    val value: CIRSourceLanguage,
) : MLIRAttribute

data class CIRTLSModelAttr(
    val value: CIRTLSModel,
) : MLIRAttribute

data class CIRUnaryOpKindAttr(
    val value: CIRUnaryOpKind,
) : MLIRAttribute

data class CIRVisibilityKindAttr(
    val value: CIRVisibilityKind,
) : MLIRAttribute
