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
    @Suppress("UNUSED_PARAMETER") cp: CIRClasspath,
) : MethodContextSerializer {

    override fun DataOutputStream.writeMethodContext(methodContext: MethodContext) {
        when (methodContext) {
            EmptyMethodContext -> writeEnum(ContextType.EMPTY)
            else -> error("Unknown method context: $methodContext")
        }
    }

    override fun DataInputStream.readMethodContext(): MethodContext {
        return when (readEnum<ContextType>()) {
            ContextType.EMPTY -> EmptyMethodContext
        }
    }

    private enum class ContextType {
        EMPTY
    }
}
