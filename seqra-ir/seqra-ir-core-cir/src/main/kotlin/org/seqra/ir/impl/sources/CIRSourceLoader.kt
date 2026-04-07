package org.seqra.ir.impl.sources

import java.nio.file.Path

interface CIRSourceLoader {
    fun loadModuleFromFile(path: Path): ModuleIRNode

    fun loadModuleFromBytes(byteArray: ByteArray): ModuleIRNode
    fun loadFunctionFromBytes(byteArray: ByteArray): FunctionIRNode
    fun loadTypeFromBytes(byteArray: ByteArray): TypeIRNode
    fun loadGlobalFromBytes(byteArray: ByteArray): GlobalIRNode
}
