package builders

import org.seqra.ir.impl.grpc.Model.CIRFunction
import org.seqra.ir.impl.grpc.Model.MLIRModule
import org.seqra.ir.impl.grpc.Setup
import org.seqra.ir.impl.grpc.Setup.MLIROpID
import org.seqra.ir.impl.grpc.Setup.MLIROpResult
import org.seqra.ir.impl.grpc.Setup.MLIRTypeID
import org.seqra.ir.impl.grpc.Type.MLIRType
import java.io.File

class CounterReference(private var counter: Long = 0) {
    fun getAndInc() = counter++
}

internal fun Long.asOpID() = MLIROpID.newBuilder().setId(this).build()

internal fun Long.asValue(): Setup.MLIRValue {
    val MLIROpID = MLIROpID.newBuilder().setId(this).build()
    val MLIROpResult = MLIROpResult.newBuilder().setOwner(MLIROpID).build()
    return Setup.MLIRValue.newBuilder().setOpResult(MLIROpResult).build()
}

internal fun Long.asTypedValue(type: MLIRTypeID): Setup.MLIRValue {
    val MLIROpID = MLIROpID.newBuilder().setId(this).build()
    val MLIROpResult = MLIROpResult.newBuilder().setOwner(MLIROpID).build()
    return Setup.MLIRValue.newBuilder().setOpResult(MLIROpResult).setType(type).build()
}

class RPCModuleBuilder(private val moduleId: String) {
    private val functionsList = mutableListOf<CIRFunction>()
    private val functionReturnTypes = hashMapOf<String, MLIRTypeID>()

    private val types = mutableListOf<MLIRType>()

    fun buildToFile(): File {
        val builder =
            MLIRModule.newBuilder().addAllFunctions(functionsList).addAllTypes(types).setId(moduleId.toModuleID())
        val file = File.createTempFile("test", ".protocir")
        file.writeBytes(builder.build().toByteArray())

//        val tmp = File("/home/sergey/Documents/usvm/usvm-cpp-dataflow/src/test/resources/$moduleId.protocir")
//        tmp.createNewFile()
//        tmp.writeBytes(builder.build().toByteArray())

        return file
    }

    fun withFunction(functionName: String, builder: RPCFunctionBuilder.() -> Unit) = apply {
        val functionBuilder = RPCFunctionBuilder(functionName, moduleId, functionReturnTypes)
        functionBuilder.builder()
        val function = functionBuilder.build()
        types.add(functionBuilder.functionType)
        functionsList.add(function)
    }

    private fun String.toModuleID(): Setup.MLIRModuleID {
        return Setup.MLIRModuleID.newBuilder().setId(this).build()
    }
}

