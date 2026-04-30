package org.seqra.ir.api.cir.cfg

import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.CommonMethodParameter
import org.seqra.ir.api.common.cfg.BytecodeGraph
import org.seqra.ir.api.common.cfg.CommonInst
import org.seqra.ir.api.common.cfg.ControlFlowGraph

data class MLIRBasicBlock(
    val id: MLIRBlockID,
    val instructions: CIRInstList<CIRInst>,
    val arguments: List<MLIRTypeID>,
) {
    override fun toString(): String = "block${id.id}:\n" + instructions.joinToString(separator = "") { "\t$it\n" }
}

interface CIRFunctionParameter : CommonMethodParameter {
    override val type: MLIRTypeID
    val index: Int
    val method: CIRFunction
}

data class CIRBlockList(val blocks: List<MLIRBasicBlock>) {
    operator fun iterator() = blocks.iterator()
    override fun toString(): String = blocks.joinToString(separator = "") { "$it\n" }
}

interface CIRFunction : CommonMethod {
    //
    val id: CIRFunctionID
    val blocks: CIRBlockList
    val allInstructions: List<CIRInst>

    //
    fun enclosingStructType(): CIRStructType? = null

    /**
     * [CIRAssignInst] indexed by [CIRAssignInst.lhv] (typically [MLIROpValue]).
     */
    val assignInstByLhv: Map<MLIRValue, CIRAssignInst>

    val info: CIRFuncOp
    val classpath: CIRClasspath

    fun <T> withIRNode(body: (ByteArray?) -> T): T

    override val name: String
        get() = id.id

    override val parameters: List<CIRFunctionParameter>
    override val returnType: MLIRTypeID

    override fun flowGraph(): BytecodeGraph<CIRInst>
}

data class CIRFuncOp(
    val symName: MLIRStringAttr,
    val globalVisibility: CIRVisibilityAttr,
    val functionType: MLIRTypeAttr,
    val builtin: MLIRUnitAttr?,
    val coroutine: MLIRUnitAttr?,
    val lambda: MLIRUnitAttr?,
    val noProto: MLIRUnitAttr?,
    val dsolocal: MLIRUnitAttr?,
    val linkage: CIRGlobalLinkageKind,
    val callingConv: CIRCallingConv,
    val extraAttrs: CIRExtraFuncAttributesAttr,
    val symVisibility: MLIRStringAttr?,
    val comdat: MLIRUnitAttr?,
    val argAttrs: MLIRArrayAttr?,
    val resAttrs: MLIRArrayAttr?,
    val aliasee: MLIRFlatSymbolRefAttr?,
    val globalCtor: CIRGlobalCtorAttr?,
    val globalDtor: CIRGlobalDtorAttr?,
    val annotations: MLIRArrayAttr?,
    val ast: MLIRAttribute?,
)

data class CIRGlobalOp(
    val symName: MLIRStringAttr,
    val globalVisibility: CIRVisibilityAttr,
    val symVisibility: MLIRStringAttr?,
    val symType: MLIRTypeAttr,
    val linkage: CIRGlobalLinkageKind,
    val addrSpace: CIRAddressSpaceAttr?,
    val tlsModel: CIRTLSModel?,
    val initialValue: MLIRAttribute?,
    val comdat: MLIRUnitAttr?,
    val constant: MLIRUnitAttr?,
    val dsolocal: MLIRUnitAttr?,
    val alignment: MLIRIntegerAttr?,
    val section: MLIRStringAttr?,
    val annotations: MLIRArrayAttr?,
) : CIRRegionBranchOpInterface

data class CIRGlobal(
    val id: CIRGlobalID,
    val info: CIRGlobalOp,
)

