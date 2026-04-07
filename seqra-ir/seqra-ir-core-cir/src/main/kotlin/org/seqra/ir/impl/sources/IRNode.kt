package org.seqra.ir.impl.sources

import org.seqra.ir.api.cir.IRNode
import org.seqra.ir.impl.types.CIRFunctionInfo
import org.seqra.ir.impl.types.CIRGlobalInfo
import org.seqra.ir.impl.types.CIRModuleInfo
import org.seqra.ir.impl.types.CIRTypeInfo

interface ModuleIRNode : IRNode {
    fun asModuleInfo(): CIRModuleInfo
}

interface FunctionIRNode : IRNode {
    fun asFunctionInfo(): CIRFunctionInfo
}

interface TypeIRNode : IRNode {
    fun asTypeInfo(): CIRTypeInfo
}

interface GlobalIRNode: IRNode {
    fun asGlobalInfo(): CIRGlobalInfo
}
