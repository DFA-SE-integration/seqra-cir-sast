package org.seqra.ir.impl.sources

import java.nio.file.Files
import java.nio.file.Path

class RPCSourceLoader : CIRSourceLoader {
    override fun loadModuleFromFile(path: Path): ModuleRPCNode {
        val bytes = Files.readAllBytes(path)
        return ModuleRPCNode(bytes)
    }

    override fun loadModuleFromBytes(byteArray: ByteArray) = ModuleRPCNode(byteArray)
    override fun loadFunctionFromBytes(byteArray: ByteArray) = FunctionRPCNode(byteArray)
    override fun loadTypeFromBytes(byteArray: ByteArray) = TypeRPCNode(byteArray)
    override fun loadGlobalFromBytes(byteArray: ByteArray) = GlobalRPCNode(byteArray)
}
