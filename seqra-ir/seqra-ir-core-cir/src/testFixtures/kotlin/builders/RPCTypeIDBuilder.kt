package builders

import org.seqra.ir.impl.grpc.Setup.MLIRModuleID
import org.seqra.ir.impl.grpc.Setup.MLIRTypeID

class RPCTypeIDBuilder private constructor(private val moduleID: String) {
    companion object {
        fun inModule(moduleID: String) = RPCTypeIDBuilder(moduleID)
    }

    private var type: String = ""

    fun withType(type: String) = apply {
        this.type = type
    }

    fun withType(type: MLIRTypeID) = apply {
        this.type = type.id
    }

    fun dereferenced() = apply {
        assert(this.type.last() == '*')
        this.type.dropLast(1)
    }

    fun referenced() = apply {
        this.type += "*"
    }

    fun build(): MLIRTypeID =
        MLIRTypeID.newBuilder().setModuleId(MLIRModuleID.newBuilder().setId(moduleID).build()).setId(type).build()
}