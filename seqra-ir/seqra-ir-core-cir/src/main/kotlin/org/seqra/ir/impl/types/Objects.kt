package org.seqra.ir.impl.types

import org.seqra.ir.api.cir.cfg.*
import org.seqra.ir.impl.sources.FunctionIRNode
import org.seqra.ir.impl.sources.GlobalIRNode
import org.seqra.ir.impl.sources.ModuleIRNode
import org.seqra.ir.impl.sources.TypeIRNode

enum class FunctionKind {
    DECLARATION, DEFINITION
}

//@Serializable
class CIRFunctionInfo(
    val id: CIRFunctionID,

    // Function info
    val info: CIRFuncOp,

    // From the CIR reference manual:
    //   "An external function declaration (used when referring to a function
    //    declared in some other module) has no body."
    // Since there is no appropriate field in the CIRFuncOp,
    // the only thing to do is to set it manually.
    val definitionOrDeclaration: FunctionKind,

    // Bytecode node
    val infoNode: FunctionIRNode, val blocksNode: ByteArray
)

//@Serializable
class CIRTypeInfo(
    val id: MLIRTypeID,

    // Bytecode node
    val bytecodeNode: TypeIRNode
)

//@Serializable
class CIRGlobalInfo(
    val id: CIRGlobalID, val info: CIRGlobalOp,

    // Bytecode node
    val bytecodeNode: GlobalIRNode
)

//@Serializable
class CIRModuleInfo(
    val id: MLIRModuleID,

    // Enumeration of all entities in the module
    val functions: List<CIRFunctionInfo>,
    val globals: List<CIRGlobalInfo>,
    val types: List<CIRTypeInfo>,

    val attributes: List<MLIRNamedAttr>,

    // TODO: required for splitting by functions
    // Bytecode node
    val bytecodeNode: ModuleIRNode,
)
