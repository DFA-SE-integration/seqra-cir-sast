package org.seqra.ir.impl.cfg.builder

import org.seqra.ir.api.cir.cfg.*
import org.seqra.ir.impl.grpc.Op

fun buildExpr(expr: Op.MLIROp) = when (expr.operationCase!!) {
    Op.MLIROp.OperationCase.ABS_OP -> buildCIRAbsOpExpr(expr.absOp)
    Op.MLIROp.OperationCase.BASE_CLASS_ADDR_OP -> buildCIRBaseClassAddrOpExpr(expr.baseClassAddrOp)
    Op.MLIROp.OperationCase.BIN_OP -> buildCIRBinOpExpr(expr.binOp)
    Op.MLIROp.OperationCase.BIT_CLRSB_OP -> buildCIRBitClrsbOpExpr(expr.bitClrsbOp)
    Op.MLIROp.OperationCase.BIT_CLZ_OP -> buildCIRBitClzOpExpr(expr.bitClzOp)
    Op.MLIROp.OperationCase.BIT_CTZ_OP -> buildCIRBitCtzOpExpr(expr.bitCtzOp)
    Op.MLIROp.OperationCase.BIT_FFS_OP -> buildCIRBitFfsOpExpr(expr.bitFfsOp)
    Op.MLIROp.OperationCase.BIT_PARITY_OP -> buildCIRBitParityOpExpr(expr.bitParityOp)
    Op.MLIROp.OperationCase.BIT_POPCOUNT_OP -> buildCIRBitPopcountOpExpr(expr.bitPopcountOp)
    Op.MLIROp.OperationCase.BYTESWAP_OP -> buildCIRByteswapOpExpr(expr.byteswapOp)
    Op.MLIROp.OperationCase.CAST_OP -> buildCIRCastOpExpr(expr.castOp)
    Op.MLIROp.OperationCase.CEIL_OP -> buildCIRCeilOpExpr(expr.ceilOp)
    Op.MLIROp.OperationCase.CMP_OP -> buildCIRCmpOpExpr(expr.cmpOp)
    Op.MLIROp.OperationCase.CMP_THREE_WAY_OP -> buildCIRCmpThreeWayOpExpr(expr.cmpThreeWayOp)
    Op.MLIROp.OperationCase.COMPLEX_BIN_OP -> buildCIRComplexBinOpExpr(expr.complexBinOp)
    Op.MLIROp.OperationCase.COMPLEX_CREATE_OP -> buildCIRComplexCreateOpExpr(expr.complexCreateOp)
    Op.MLIROp.OperationCase.COMPLEX_IMAG_OP -> buildCIRComplexImagOpExpr(expr.complexImagOp)
    Op.MLIROp.OperationCase.COMPLEX_IMAG_PTR_OP -> buildCIRComplexImagPtrOpExpr(expr.complexImagPtrOp)
    Op.MLIROp.OperationCase.COMPLEX_REAL_OP -> buildCIRComplexRealOpExpr(expr.complexRealOp)
    Op.MLIROp.OperationCase.COMPLEX_REAL_PTR_OP -> buildCIRComplexRealPtrOpExpr(expr.complexRealPtrOp)
    Op.MLIROp.OperationCase.CONSTANT_OP -> buildCIRConstantOpExpr(expr.constantOp)
    Op.MLIROp.OperationCase.COPYSIGN_OP -> buildCIRCopysignOpExpr(expr.copysignOp)
    Op.MLIROp.OperationCase.COS_OP -> buildCIRCosOpExpr(expr.cosOp)
    Op.MLIROp.OperationCase.DERIVED_CLASS_ADDR_OP -> buildCIRDerivedClassAddrOpExpr(expr.derivedClassAddrOp)
    Op.MLIROp.OperationCase.DYNAMIC_CAST_OP -> buildCIRDynamicCastOpExpr(expr.dynamicCastOp)
    Op.MLIROp.OperationCase.EH_TYPE_ID_OP -> buildCIREhTypeIdOpExpr(expr.ehTypeIdOp)
    Op.MLIROp.OperationCase.EXP2_OP -> buildCIRExp2OpExpr(expr.exp2Op)
    Op.MLIROp.OperationCase.EXP_OP -> buildCIRExpOpExpr(expr.expOp)
    Op.MLIROp.OperationCase.F_ABS_OP -> buildCIRFAbsOpExpr(expr.fAbsOp)
    Op.MLIROp.OperationCase.F_MAX_OP -> buildCIRFMaxOpExpr(expr.fMaxOp)
    Op.MLIROp.OperationCase.F_MIN_OP -> buildCIRFMinOpExpr(expr.fMinOp)
    Op.MLIROp.OperationCase.F_MOD_OP -> buildCIRFModOpExpr(expr.fModOp)
    Op.MLIROp.OperationCase.FLOOR_OP -> buildCIRFloorOpExpr(expr.floorOp)
    Op.MLIROp.OperationCase.GET_GLOBAL_OP -> buildCIRGetGlobalOpExpr(expr.getGlobalOp)
    Op.MLIROp.OperationCase.GET_MEMBER_OP -> buildCIRGetMemberOpExpr(expr.getMemberOp)
    Op.MLIROp.OperationCase.IS_CONSTANT_OP -> buildCIRIsConstantOpExpr(expr.isConstantOp)
    Op.MLIROp.OperationCase.IS_FP_CLASS_OP -> buildCIRIsFPClassOpExpr(expr.isFpClassOp)
    Op.MLIROp.OperationCase.ITER_BEGIN_OP -> buildCIRIterBeginOpExpr(expr.iterBeginOp)
    Op.MLIROp.OperationCase.ITER_END_OP -> buildCIRIterEndOpExpr(expr.iterEndOp)
    Op.MLIROp.OperationCase.L_LRINT_OP -> buildCIRLLrintOpExpr(expr.lLrintOp)
    Op.MLIROp.OperationCase.L_LROUND_OP -> buildCIRLLroundOpExpr(expr.lLroundOp)
    Op.MLIROp.OperationCase.LOG10_OP -> buildCIRLog10OpExpr(expr.log10Op)
    Op.MLIROp.OperationCase.LOG2_OP -> buildCIRLog2OpExpr(expr.log2Op)
    Op.MLIROp.OperationCase.LOG_OP -> buildCIRLogOpExpr(expr.logOp)
    Op.MLIROp.OperationCase.LRINT_OP -> buildCIRLrintOpExpr(expr.lrintOp)
    Op.MLIROp.OperationCase.LROUND_OP -> buildCIRLroundOpExpr(expr.lroundOp)
    Op.MLIROp.OperationCase.NEARBYINT_OP -> buildCIRNearbyintOpExpr(expr.nearbyintOp)
    Op.MLIROp.OperationCase.OBJ_SIZE_OP -> buildCIRObjSizeOpExpr(expr.objSizeOp)
    Op.MLIROp.OperationCase.POW_OP -> buildCIRPowOpExpr(expr.powOp)
    Op.MLIROp.OperationCase.PTR_DIFF_OP -> buildCIRPtrDiffOpExpr(expr.ptrDiffOp)
    Op.MLIROp.OperationCase.PTR_MASK_OP -> buildCIRPtrMaskOpExpr(expr.ptrMaskOp)
    Op.MLIROp.OperationCase.PTR_STRIDE_OP -> buildCIRPtrStrideOpExpr(expr.ptrStrideOp)
    Op.MLIROp.OperationCase.RINT_OP -> buildCIRRintOpExpr(expr.rintOp)
    Op.MLIROp.OperationCase.ROTATE_OP -> buildCIRRotateOpExpr(expr.rotateOp)
    Op.MLIROp.OperationCase.ROUND_OP -> buildCIRRoundOpExpr(expr.roundOp)
    Op.MLIROp.OperationCase.SELECT_OP -> buildCIRSelectOpExpr(expr.selectOp)
    Op.MLIROp.OperationCase.SHIFT_OP -> buildCIRShiftOpExpr(expr.shiftOp)
    Op.MLIROp.OperationCase.SIGN_BIT_OP -> buildCIRSignBitOpExpr(expr.signBitOp)
    Op.MLIROp.OperationCase.SIN_OP -> buildCIRSinOpExpr(expr.sinOp)
    Op.MLIROp.OperationCase.SQRT_OP -> buildCIRSqrtOpExpr(expr.sqrtOp)
    Op.MLIROp.OperationCase.TRUNC_OP -> buildCIRTruncOpExpr(expr.truncOp)
    Op.MLIROp.OperationCase.UNARY_OP -> buildCIRUnaryOpExpr(expr.unaryOp)
    Op.MLIROp.OperationCase.VTT_ADDR_POINT_OP -> buildCIRVTTAddrPointOpExpr(expr.vttAddrPointOp)
    Op.MLIROp.OperationCase.V_TABLE_ADDR_POINT_OP -> buildCIRVTableAddrPointOpExpr(expr.vTableAddrPointOp)
    else -> throw Exception()
}

fun buildCIRAbsOpExpr(expr: Op.CIRAbsOp) = 
    CIRAbsOpExpr(
        buildMLIRValue(expr.src),
        if (expr.hasPoison()) buildMLIRUnitAttr(expr.poison) else null,
        buildMLIRTypeID(expr.result),
)

fun buildCIRBaseClassAddrOpExpr(expr: Op.CIRBaseClassAddrOp) = 
    CIRBaseClassAddrOpExpr(
        buildMLIRValue(expr.derivedAddr),
        buildMLIRIntegerAttr(expr.offset),
        if (expr.hasAssumeNotNull()) buildMLIRUnitAttr(expr.assumeNotNull) else null,
        buildMLIRTypeID(expr.baseAddr),
)

fun buildCIRBinOpExpr(expr: Op.CIRBinOp) = 
    CIRBinOpExpr(
        buildMLIRValue(expr.lhs),
        buildMLIRValue(expr.rhs),
        buildCIRBinOpKind(expr.kind),
        if (expr.hasNoUnsignedWrap()) buildMLIRUnitAttr(expr.noUnsignedWrap) else null,
        if (expr.hasNoSignedWrap()) buildMLIRUnitAttr(expr.noSignedWrap) else null,
        buildMLIRTypeID(expr.result),
)

fun buildCIRBitClrsbOpExpr(expr: Op.CIRBitClrsbOp) = 
    CIRBitClrsbOpExpr(
        buildMLIRValue(expr.input),
        buildMLIRTypeID(expr.result),
)

fun buildCIRBitClzOpExpr(expr: Op.CIRBitClzOp) = 
    CIRBitClzOpExpr(
        buildMLIRValue(expr.input),
        buildMLIRTypeID(expr.result),
)

fun buildCIRBitCtzOpExpr(expr: Op.CIRBitCtzOp) = 
    CIRBitCtzOpExpr(
        buildMLIRValue(expr.input),
        buildMLIRTypeID(expr.result),
)

fun buildCIRBitFfsOpExpr(expr: Op.CIRBitFfsOp) = 
    CIRBitFfsOpExpr(
        buildMLIRValue(expr.input),
        buildMLIRTypeID(expr.result),
)

fun buildCIRBitParityOpExpr(expr: Op.CIRBitParityOp) = 
    CIRBitParityOpExpr(
        buildMLIRValue(expr.input),
        buildMLIRTypeID(expr.result),
)

fun buildCIRBitPopcountOpExpr(expr: Op.CIRBitPopcountOp) = 
    CIRBitPopcountOpExpr(
        buildMLIRValue(expr.input),
        buildMLIRTypeID(expr.result),
)

fun buildCIRByteswapOpExpr(expr: Op.CIRByteswapOp) = 
    CIRByteswapOpExpr(
        buildMLIRValue(expr.input),
        buildMLIRTypeID(expr.result),
)

fun buildCIRCastOpExpr(expr: Op.CIRCastOp) = 
    CIRCastOpExpr(
        buildMLIRValue(expr.src),
        buildCIRCastKind(expr.kind),
        buildMLIRTypeID(expr.result),
)

fun buildCIRCeilOpExpr(expr: Op.CIRCeilOp) = 
    CIRCeilOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRCmpOpExpr(expr: Op.CIRCmpOp) = 
    CIRCmpOpExpr(
        buildMLIRValue(expr.lhs),
        buildMLIRValue(expr.rhs),
        buildCIRCmpOpKind(expr.kind),
        buildMLIRTypeID(expr.result),
)

fun buildCIRCmpThreeWayOpExpr(expr: Op.CIRCmpThreeWayOp) = 
    CIRCmpThreeWayOpExpr(
        buildMLIRValue(expr.lhs),
        buildMLIRValue(expr.rhs),
        buildCIRCmpThreeWayInfoAttr(expr.info),
        buildMLIRTypeID(expr.result),
)

fun buildCIRComplexBinOpExpr(expr: Op.CIRComplexBinOp) = 
    CIRComplexBinOpExpr(
        buildMLIRValue(expr.lhs),
        buildMLIRValue(expr.rhs),
        buildCIRComplexBinOpKind(expr.kind),
        buildCIRComplexRangeKind(expr.range),
        if (expr.hasPromoted()) buildMLIRUnitAttr(expr.promoted) else null,
        buildMLIRTypeID(expr.result),
)

fun buildCIRComplexCreateOpExpr(expr: Op.CIRComplexCreateOp) = 
    CIRComplexCreateOpExpr(
        buildMLIRValue(expr.real),
        buildMLIRValue(expr.imag),
        buildMLIRTypeID(expr.result),
)

fun buildCIRComplexImagOpExpr(expr: Op.CIRComplexImagOp) = 
    CIRComplexImagOpExpr(
        buildMLIRValue(expr.operand),
        buildMLIRTypeID(expr.result),
)

fun buildCIRComplexImagPtrOpExpr(expr: Op.CIRComplexImagPtrOp) = 
    CIRComplexImagPtrOpExpr(
        buildMLIRValue(expr.operand),
        buildMLIRTypeID(expr.result),
)

fun buildCIRComplexRealOpExpr(expr: Op.CIRComplexRealOp) = 
    CIRComplexRealOpExpr(
        buildMLIRValue(expr.operand),
        buildMLIRTypeID(expr.result),
)

fun buildCIRComplexRealPtrOpExpr(expr: Op.CIRComplexRealPtrOp) = 
    CIRComplexRealPtrOpExpr(
        buildMLIRValue(expr.operand),
        buildMLIRTypeID(expr.result),
)

fun buildCIRConstantOpExpr(expr: Op.CIRConstantOp) = 
    CIRConstantOpExpr(
        buildMLIRAttribute(expr.value),
        buildMLIRTypeID(expr.res),
)

fun buildCIRCopysignOpExpr(expr: Op.CIRCopysignOp) = 
    CIRCopysignOpExpr(
        buildMLIRValue(expr.lhs),
        buildMLIRValue(expr.rhs),
        buildMLIRTypeID(expr.result),
)

fun buildCIRCosOpExpr(expr: Op.CIRCosOp) = 
    CIRCosOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRDerivedClassAddrOpExpr(expr: Op.CIRDerivedClassAddrOp) = 
    CIRDerivedClassAddrOpExpr(
        buildMLIRValue(expr.baseAddr),
        buildMLIRIntegerAttr(expr.offset),
        if (expr.hasAssumeNotNull()) buildMLIRUnitAttr(expr.assumeNotNull) else null,
        buildMLIRTypeID(expr.derivedAddr),
)

fun buildCIRDynamicCastOpExpr(expr: Op.CIRDynamicCastOp) = 
    CIRDynamicCastOpExpr(
        buildMLIRValue(expr.src),
        buildCIRDynamicCastKind(expr.kind),
        if (expr.hasInfo()) buildCIRDynamicCastInfoAttr(expr.info) else null,
        if (expr.hasRelativeLayout()) buildMLIRUnitAttr(expr.relativeLayout) else null,
        buildMLIRTypeID(expr.result),
)

fun buildCIREhTypeIdOpExpr(expr: Op.CIREhTypeIdOp) = 
    CIREhTypeIdOpExpr(
        buildMLIRFlatSymbolRefAttr(expr.typeSym),
        buildMLIRTypeID(expr.typeId),
)

fun buildCIRExp2OpExpr(expr: Op.CIRExp2Op) = 
    CIRExp2OpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRExpOpExpr(expr: Op.CIRExpOp) = 
    CIRExpOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRFAbsOpExpr(expr: Op.CIRFAbsOp) = 
    CIRFAbsOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRFMaxOpExpr(expr: Op.CIRFMaxOp) = 
    CIRFMaxOpExpr(
        buildMLIRValue(expr.lhs),
        buildMLIRValue(expr.rhs),
        buildMLIRTypeID(expr.result),
)

fun buildCIRFMinOpExpr(expr: Op.CIRFMinOp) = 
    CIRFMinOpExpr(
        buildMLIRValue(expr.lhs),
        buildMLIRValue(expr.rhs),
        buildMLIRTypeID(expr.result),
)

fun buildCIRFModOpExpr(expr: Op.CIRFModOp) = 
    CIRFModOpExpr(
        buildMLIRValue(expr.lhs),
        buildMLIRValue(expr.rhs),
        buildMLIRTypeID(expr.result),
)

fun buildCIRFloorOpExpr(expr: Op.CIRFloorOp) = 
    CIRFloorOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRGetGlobalOpExpr(expr: Op.CIRGetGlobalOp) = 
    CIRGetGlobalOpExpr(
        buildMLIRFlatSymbolRefAttr(expr.name),
        if (expr.hasTls()) buildMLIRUnitAttr(expr.tls) else null,
        buildMLIRTypeID(expr.addr),
)

fun buildCIRGetMemberOpExpr(expr: Op.CIRGetMemberOp) = 
    CIRGetMemberOpExpr(
        buildMLIRValue(expr.addr),
        buildMLIRStringAttr(expr.name),
        buildMLIRIntegerAttr(expr.indexAttr),
        buildMLIRTypeID(expr.result),
)

fun buildCIRIsConstantOpExpr(expr: Op.CIRIsConstantOp) = 
    CIRIsConstantOpExpr(
        buildMLIRValue(expr.getVal()),
        buildMLIRTypeID(expr.result),
)

fun buildCIRIsFPClassOpExpr(expr: Op.CIRIsFPClassOp) = 
    CIRIsFPClassOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRIntegerAttr(expr.flags),
        buildMLIRTypeID(expr.result),
)

fun buildCIRIterBeginOpExpr(expr: Op.CIRIterBeginOp) = 
    CIRIterBeginOpExpr(
        buildMLIRValue(expr.container),
        buildMLIRFlatSymbolRefAttr(expr.originalFn),
        buildMLIRTypeID(expr.result),
)

fun buildCIRIterEndOpExpr(expr: Op.CIRIterEndOp) = 
    CIRIterEndOpExpr(
        buildMLIRValue(expr.container),
        buildMLIRFlatSymbolRefAttr(expr.originalFn),
        buildMLIRTypeID(expr.result),
)

fun buildCIRLLrintOpExpr(expr: Op.CIRLLrintOp) = 
    CIRLLrintOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRLLroundOpExpr(expr: Op.CIRLLroundOp) = 
    CIRLLroundOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRLog10OpExpr(expr: Op.CIRLog10Op) = 
    CIRLog10OpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRLog2OpExpr(expr: Op.CIRLog2Op) = 
    CIRLog2OpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRLogOpExpr(expr: Op.CIRLogOp) = 
    CIRLogOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRLrintOpExpr(expr: Op.CIRLrintOp) = 
    CIRLrintOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRLroundOpExpr(expr: Op.CIRLroundOp) = 
    CIRLroundOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRNearbyintOpExpr(expr: Op.CIRNearbyintOp) = 
    CIRNearbyintOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRObjSizeOpExpr(expr: Op.CIRObjSizeOp) = 
    CIRObjSizeOpExpr(
        buildMLIRValue(expr.ptr),
        buildCIRSizeInfoType(expr.kind),
        if (expr.hasDynamic()) buildMLIRUnitAttr(expr.dynamic) else null,
        buildMLIRTypeID(expr.result),
)

fun buildCIRPowOpExpr(expr: Op.CIRPowOp) = 
    CIRPowOpExpr(
        buildMLIRValue(expr.lhs),
        buildMLIRValue(expr.rhs),
        buildMLIRTypeID(expr.result),
)

fun buildCIRPtrDiffOpExpr(expr: Op.CIRPtrDiffOp) = 
    CIRPtrDiffOpExpr(
        buildMLIRValue(expr.lhs),
        buildMLIRValue(expr.rhs),
        buildMLIRTypeID(expr.result),
)

fun buildCIRPtrMaskOpExpr(expr: Op.CIRPtrMaskOp) = 
    CIRPtrMaskOpExpr(
        buildMLIRValue(expr.ptr),
        buildMLIRValue(expr.mask),
        buildMLIRTypeID(expr.result),
)

fun buildCIRPtrStrideOpExpr(expr: Op.CIRPtrStrideOp) = 
    CIRPtrStrideOpExpr(
        buildMLIRValue(expr.base),
        buildMLIRValue(expr.stride),
        buildMLIRTypeID(expr.result),
)

fun buildCIRRintOpExpr(expr: Op.CIRRintOp) = 
    CIRRintOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRRotateOpExpr(expr: Op.CIRRotateOp) = 
    CIRRotateOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRValue(expr.amt),
        if (expr.hasLeft()) buildMLIRUnitAttr(expr.left) else null,
        buildMLIRTypeID(expr.result),
)

fun buildCIRRoundOpExpr(expr: Op.CIRRoundOp) = 
    CIRRoundOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRSelectOpExpr(expr: Op.CIRSelectOp) = 
    CIRSelectOpExpr(
        buildMLIRValue(expr.condition),
        buildMLIRValue(expr.trueValue),
        buildMLIRValue(expr.falseValue),
        buildMLIRTypeID(expr.result),
)

fun buildCIRShiftOpExpr(expr: Op.CIRShiftOp) = 
    CIRShiftOpExpr(
        buildMLIRValue(expr.value),
        buildMLIRValue(expr.amount),
        if (expr.hasIsShiftleft()) buildMLIRUnitAttr(expr.isShiftleft) else null,
        buildMLIRTypeID(expr.result),
)

fun buildCIRSignBitOpExpr(expr: Op.CIRSignBitOp) = 
    CIRSignBitOpExpr(
        buildMLIRValue(expr.input),
        buildMLIRTypeID(expr.res),
)

fun buildCIRSinOpExpr(expr: Op.CIRSinOp) = 
    CIRSinOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRSqrtOpExpr(expr: Op.CIRSqrtOp) = 
    CIRSqrtOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRTruncOpExpr(expr: Op.CIRTruncOp) = 
    CIRTruncOpExpr(
        buildMLIRValue(expr.src),
        buildMLIRTypeID(expr.result),
)

fun buildCIRUnaryOpExpr(expr: Op.CIRUnaryOp) = 
    CIRUnaryOpExpr(
        buildMLIRValue(expr.input),
        buildCIRUnaryOpKind(expr.kind),
        buildMLIRTypeID(expr.result),
)

fun buildCIRVTTAddrPointOpExpr(expr: Op.CIRVTTAddrPointOp) = 
    CIRVTTAddrPointOpExpr(
        if (expr.hasSymAddr()) buildMLIRValue(expr.symAddr) else null,
        if (expr.hasName()) buildMLIRFlatSymbolRefAttr(expr.name) else null,
        buildMLIRIntegerAttr(expr.offset),
        buildMLIRTypeID(expr.addr),
)

fun buildCIRVTableAddrPointOpExpr(expr: Op.CIRVTableAddrPointOp) = 
    CIRVTableAddrPointOpExpr(
        if (expr.hasSymAddr()) buildMLIRValue(expr.symAddr) else null,
        if (expr.hasName()) buildMLIRFlatSymbolRefAttr(expr.name) else null,
        buildMLIRIntegerAttr(expr.vtableIndex),
        buildMLIRIntegerAttr(expr.addressPointIndex),
        buildMLIRTypeID(expr.addr),
)
