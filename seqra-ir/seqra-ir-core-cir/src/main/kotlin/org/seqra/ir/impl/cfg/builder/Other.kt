package org.seqra.ir.impl.cfg.builder

import org.seqra.ir.api.cir.cfg.*
import org.seqra.ir.impl.cfg.CIRInstListImpl
import org.seqra.ir.impl.grpc.Model
import org.seqra.ir.impl.grpc.Op

import org.seqra.ir.impl.grpc.Setup


fun buildMLIRModuleID(module: Setup.MLIRModuleID) = MLIRModuleID(module.id)
fun buildCIRGlobalID(global: Setup.CIRGlobalID) = CIRGlobalID(buildMLIRModuleID(global.moduleId), global.id)
fun buildCIRFunctionID(function: Setup.CIRFunctionID) =
    CIRFunctionID(buildMLIRModuleID(function.moduleId), function.id)

fun buildMLIRTypeID(type: Setup.MLIRTypeID) = MLIRTypeID(buildMLIRModuleID(type.moduleId), type.id)


fun buildBlock(instBuilder: CIRInstBuilder, block: Model.MLIRBlock): MLIRBasicBlock {
    val instructions = arrayListOf<CIRInst>()
    for (op in block.operationsList) {
        instructions.add(instBuilder.buildInst(op))
    }

    val arguments = arrayListOf<MLIRTypeID>()
    for (type in block.argumentTypesList) {
        arguments.add(buildMLIRTypeID(type))
    }

    return MLIRBasicBlock(
        buildMLIRBlockID(block.id),
        CIRInstListImpl(instructions),
        arguments,
    )
}

fun buildCIRFuncOp(func: Op.CIRFuncOp): CIRFuncOp {
    return CIRFuncOp(
        buildMLIRStringAttr(func.symName),
        buildCIRVisibilityAttr(func.globalVisibility),
        buildMLIRTypeAttr(func.functionType),
        if (func.hasBuiltin()) buildMLIRUnitAttr(func.builtin) else null,
        if (func.hasCoroutine()) buildMLIRUnitAttr(func.coroutine) else null,
        if (func.hasLambda()) buildMLIRUnitAttr(func.lambda) else null,
        if (func.hasNoProto()) buildMLIRUnitAttr(func.noProto) else null,
        if (func.hasDsolocal()) buildMLIRUnitAttr(func.dsolocal) else null,
        buildCIRGlobalLinkageKind(func.linkage),
        buildCIRCallingConv(func.callingConv),
        buildCIRExtraFuncAttributesAttr(func.extraAttrs),
        if (func.hasSymVisibility()) buildMLIRStringAttr(func.symVisibility) else null,
        if (func.hasComdat()) buildMLIRUnitAttr(func.comdat) else null,
        if (func.hasArgAttrs()) buildMLIRArrayAttr(func.argAttrs) else null,
        if (func.hasResAttrs()) buildMLIRArrayAttr(func.resAttrs) else null,
        if (func.hasAliasee()) buildMLIRFlatSymbolRefAttr(func.aliasee) else null,
        if (func.hasGlobalCtor()) buildCIRGlobalCtorAttr(func.globalCtor) else null,
        if (func.hasGlobalDtor()) buildCIRGlobalDtorAttr(func.globalDtor) else null,
        if (func.hasAnnotations()) buildMLIRArrayAttr(func.annotations) else null,
        if (func.hasAst()) buildMLIRAttribute(func.ast) else null,

        )
}

fun buildBlocks(function: CIRFunction, blockList: Model.MLIRBlockList): List<MLIRBasicBlock> {
    val blocks = arrayListOf<MLIRBasicBlock>()
    val instBuilder = CIRInstBuilder(function)
    for (block in blockList.blockList) {
        blocks.add(buildBlock(instBuilder, block))
    }
    return blocks
}

fun buildCIRGlobalOp(global: Op.CIRGlobalOp): CIRGlobalOp {
    return CIRGlobalOp(
        buildMLIRStringAttr(global.symName),
        buildCIRVisibilityAttr(global.globalVisibility),
        if (global.hasSymVisibility()) buildMLIRStringAttr(global.symVisibility) else null,
        buildMLIRTypeAttr(global.symType),
        buildCIRGlobalLinkageKind(global.linkage),
        if (global.hasAddrSpace()) buildCIRAddressSpaceAttr(global.addrSpace) else null,
        if (global.hasTlsModel()) buildCIRTLSModel(global.tlsModel) else null,
        if (global.hasInitialValue()) buildMLIRAttribute(global.initialValue) else null,
        if (global.hasComdat()) buildMLIRUnitAttr(global.comdat) else null,
        if (global.hasConstant()) buildMLIRUnitAttr(global.constant) else null,
        if (global.hasDsolocal()) buildMLIRUnitAttr(global.dsolocal) else null,
        if (global.hasAlignment()) buildMLIRIntegerAttr(global.alignment) else null,
        if (global.hasSection()) buildMLIRStringAttr(global.section) else null,
        if (global.hasAnnotations()) buildMLIRArrayAttr(global.annotations) else null,

        )
}