package org.seqra.ir.impl.sources

import org.seqra.ir.impl.cfg.builder.*
import org.seqra.ir.impl.grpc.Model.CIRFunction
import org.seqra.ir.impl.grpc.Model.CIRGlobal
import org.seqra.ir.impl.grpc.Model.MLIRModule
import org.seqra.ir.impl.grpc.Type.MLIRType
import org.seqra.ir.impl.types.CIRFunctionInfo
import org.seqra.ir.impl.types.CIRGlobalInfo
import org.seqra.ir.impl.types.CIRModuleInfo
import org.seqra.ir.impl.types.CIRTypeInfo
import org.seqra.ir.impl.types.FunctionKind

interface RpcNode

// From the CIR reference manual:
//   "An external function declaration (used when referring to a function
//    declared in some other module) has no body."
fun CIRFunction.kind() = if (blocks.blockList.isEmpty()) FunctionKind.DECLARATION else FunctionKind.DEFINITION

fun CIRFunction.asFunctionInfo() = CIRFunctionInfo(
    id = buildCIRFunctionID(id),
    info = buildCIRFuncOp(info),
    definitionOrDeclaration = kind(),
    infoNode = FunctionRPCNode(info.toByteArray()),
    blocksNode = blocks.toByteArray()
)

fun MLIRModule.asModuleInfo() = CIRModuleInfo(
    id = buildMLIRModuleID(id),
    functions = functionsList.map { it.asFunctionInfo() },
    globals = globalsList.map { it.asGlobalInfo() },
    types = typesList.map { it.asTypeInfo() },
    attributes = attributesList.map { buildMLIRNamedAttr(it) },
    bytecodeNode = ModuleRPCNode(toByteArray()),
)

fun MLIRType.asTypeInfo() = CIRTypeInfo(
    id = buildMLIRTypeID(id),
    bytecodeNode = TypeRPCNode(toByteArray()),
)

fun CIRGlobal.asGlobalInfo() = CIRGlobalInfo(
    id = buildCIRGlobalID(id), info = buildCIRGlobalOp(info), bytecodeNode = GlobalRPCNode(toByteArray())
)

class ModuleRPCNode(override val byteBuffer: ByteArray) : ModuleIRNode, RpcNode {
    override fun asModuleInfo(): CIRModuleInfo {
        val module = MLIRModule.parseFrom(byteBuffer)
        return module.asModuleInfo()
    }
}

class FunctionRPCNode(override val byteBuffer: ByteArray) : FunctionIRNode, RpcNode {
    override fun asFunctionInfo(): CIRFunctionInfo {
        val function = CIRFunction.parseFrom(byteBuffer)
        return function.asFunctionInfo()
    }
}

class TypeRPCNode(override val byteBuffer: ByteArray) : TypeIRNode, RpcNode {
    override fun asTypeInfo(): CIRTypeInfo {
        val type = MLIRType.parseFrom(byteBuffer)
        return type.asTypeInfo()
    }
}

class GlobalRPCNode(override val byteBuffer: ByteArray) : GlobalIRNode, RpcNode {
    override fun asGlobalInfo(): CIRGlobalInfo {
        val global = CIRGlobal.parseFrom(byteBuffer)
        return global.asGlobalInfo()
    }
}
