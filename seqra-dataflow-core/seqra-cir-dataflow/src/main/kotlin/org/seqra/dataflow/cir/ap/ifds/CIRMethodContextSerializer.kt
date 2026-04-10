package org.seqra.dataflow.cir.ap.ifds

import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.dataflow.ap.ifds.EmptyMethodContext
import org.seqra.dataflow.ap.ifds.MethodContext
import org.seqra.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.seqra.dataflow.ap.ifds.serialization.readEnum
import org.seqra.dataflow.ap.ifds.serialization.writeEnum
import java.io.DataInputStream
import java.io.DataOutputStream

class CIRMethodContextSerializer(
    private val cp: CIRClasspath,
) : MethodContextSerializer {

    override fun DataOutputStream.writeMethodContext(methodContext: MethodContext) {
        when (methodContext) {
            EmptyMethodContext -> writeEnum(ContextType.EMPTY)
            is CIRInstanceTypeMethodContext -> {
                writeEnum(ContextType.CIR_INSTANCE_TYPE)
                writeUTF(CIRFieldTypeEncoding.encode(methodContext.receiverTypeId))
            }
            else -> error("Unknown method context: $methodContext")
        }
    }

    override fun DataInputStream.readMethodContext(): MethodContext {
        return when (readEnum<ContextType>()) {
            ContextType.EMPTY -> EmptyMethodContext
            ContextType.CIR_INSTANCE_TYPE -> {
                val typeId = CIRFieldTypeEncoding.decodeOrNull(readUTF())
                    ?: error("Invalid CIR receiver type in method context")
                check(cp.findTypeOrNull(typeId) != null) { "Unknown CIR receiver type in method context: $typeId" }
                CIRInstanceTypeMethodContext(typeId)
            }
        }
    }

    private enum class ContextType {
        EMPTY,
        CIR_INSTANCE_TYPE
    }
}
