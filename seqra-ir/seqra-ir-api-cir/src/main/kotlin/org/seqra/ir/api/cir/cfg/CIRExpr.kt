package org.seqra.ir.api.cir.cfg

data class CIRAbsOpExpr(
    val src: MLIRValue,
    val poison: MLIRUnitAttr?,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRBaseClassAddrOpExpr(
    val derived_addr: MLIRValue,
    val offset: MLIRIntegerAttr,
    val assumeNotNull: MLIRUnitAttr?,
    val baseAddr: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = baseAddr
}

data class CIRBinOpExpr(
    val lhs: MLIRValue,
    val rhs: MLIRValue,
    val kind: CIRBinOpKind,
    val noUnsignedWrap: MLIRUnitAttr?,
    val noSignedWrap: MLIRUnitAttr?,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRBitClrsbOpExpr(
    val input: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRBitClzOpExpr(
    val input: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRBitCtzOpExpr(
    val input: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRBitFfsOpExpr(
    val input: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRBitParityOpExpr(
    val input: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRBitPopcountOpExpr(
    val input: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRByteswapOpExpr(
    val input: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRCastOpExpr(
    val src: MLIRValue,
    val kind: CIRCastKind,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRCeilOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRCmpOpExpr(
    val lhs: MLIRValue,
    val rhs: MLIRValue,
    val kind: CIRCmpOpKind,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRCmpThreeWayOpExpr(
    val lhs: MLIRValue,
    val rhs: MLIRValue,
    val info: CIRCmpThreeWayInfoAttr,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRComplexBinOpExpr(
    val lhs: MLIRValue,
    val rhs: MLIRValue,
    val kind: CIRComplexBinOpKind,
    val range: CIRComplexRangeKind,
    val promoted: MLIRUnitAttr?,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRComplexCreateOpExpr(
    val real: MLIRValue,
    val imag: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRComplexImagOpExpr(
    val operand: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRComplexImagPtrOpExpr(
    val operand: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRComplexRealOpExpr(
    val operand: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRComplexRealPtrOpExpr(
    val operand: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRConstantOpExpr(
    val value: MLIRAttribute,
    val res: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = res
}

data class CIRCopysignOpExpr(
    val lhs: MLIRValue,
    val rhs: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRCosOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRDerivedClassAddrOpExpr(
    val base_addr: MLIRValue,
    val offset: MLIRIntegerAttr,
    val assumeNotNull: MLIRUnitAttr?,
    val derivedAddr: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = derivedAddr
}

data class CIRDynamicCastOpExpr(
    val src: MLIRValue,
    val kind: CIRDynamicCastKind,
    val info: CIRDynamicCastInfoAttr?,
    val relativeLayout: MLIRUnitAttr?,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIREhTypeIdOpExpr(
    val typeSym: MLIRFlatSymbolRefAttr,
    val typeId: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = typeId
}

data class CIRExp2OpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRExpOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRFAbsOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRFMaxOpExpr(
    val lhs: MLIRValue,
    val rhs: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRFMinOpExpr(
    val lhs: MLIRValue,
    val rhs: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRFModOpExpr(
    val lhs: MLIRValue,
    val rhs: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRFloorOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRGetGlobalOpExpr(
    val name: MLIRFlatSymbolRefAttr,
    val tls: MLIRUnitAttr?,
    val addr: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = addr
}

data class CIRGetMemberOpExpr(
    val addr: MLIRValue,
    val name: MLIRStringAttr,
    val indexAttr: MLIRIntegerAttr,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRIsConstantOpExpr(
    val value: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRIsFPClassOpExpr(
    val src: MLIRValue,
    val flags: MLIRIntegerAttr,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRIterBeginOpExpr(
    val container: MLIRValue,
    val originalFn: MLIRFlatSymbolRefAttr,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRIterEndOpExpr(
    val container: MLIRValue,
    val originalFn: MLIRFlatSymbolRefAttr,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRLLrintOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRLLroundOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRLog10OpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRLog2OpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRLogOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRLrintOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRLroundOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRNearbyintOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRObjSizeOpExpr(
    val ptr: MLIRValue,
    val kind: CIRSizeInfoType,
    val dynamic: MLIRUnitAttr?,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRPowOpExpr(
    val lhs: MLIRValue,
    val rhs: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRPtrDiffOpExpr(
    val lhs: MLIRValue,
    val rhs: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRPtrMaskOpExpr(
    val ptr: MLIRValue,
    val mask: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRPtrStrideOpExpr(
    val base: MLIRValue,
    val stride: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRRintOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRRotateOpExpr(
    val src: MLIRValue,
    val amt: MLIRValue,
    val left: MLIRUnitAttr?,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRRoundOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRSelectOpExpr(
    val condition: MLIRValue,
    val true_value: MLIRValue,
    val false_value: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRShiftOpExpr(
    val value: MLIRValue,
    val amount: MLIRValue,
    val isShiftleft: MLIRUnitAttr?,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRSignBitOpExpr(
    val input: MLIRValue,
    val res: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = res
}

data class CIRSinOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRSqrtOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRTruncOpExpr(
    val src: MLIRValue,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRUnaryOpExpr(
    val input: MLIRValue,
    val kind: CIRUnaryOpKind,
    val result: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = result
}

data class CIRVTTAddrPointOpExpr(
    val sym_addr: MLIRValue?,
    val name: MLIRFlatSymbolRefAttr?,
    val offset: MLIRIntegerAttr,
    val addr: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = addr
}

data class CIRVTableAddrPointOpExpr(
    val sym_addr: MLIRValue?,
    val name: MLIRFlatSymbolRefAttr?,
    val vtableIndex: MLIRIntegerAttr,
    val addressPointIndex: MLIRIntegerAttr,
    val addr: MLIRTypeID,
) : CIRExpr {
    override val type: MLIRTypeID
        get() = addr
}
