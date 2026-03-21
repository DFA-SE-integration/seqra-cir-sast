package builders

import org.seqra.ir.impl.grpc.Attr
import org.seqra.ir.impl.grpc.Model
import org.seqra.ir.impl.grpc.Model.MLIRBlockList
import org.seqra.ir.impl.grpc.Model.CIRFunction
import org.seqra.ir.impl.grpc.Op.CIRFuncOp
import org.seqra.ir.impl.grpc.Setup
import org.seqra.ir.impl.grpc.Setup.MLIRBlockArgument
import org.seqra.ir.impl.grpc.Setup.MLIRBlockID
import org.seqra.ir.impl.grpc.Setup.MLIRModuleID
import org.seqra.ir.impl.grpc.Setup.MLIRTypeID
import org.seqra.ir.impl.grpc.Setup.MLIRValue
import org.seqra.ir.impl.grpc.Type
import org.seqra.ir.impl.grpc.Type.MLIRType

data class RPCFunctionBuilderResult(val function: CIRFunction, val registeredTypes: List<MLIRTypeID>)

class RPCFunctionBuilder(
    private val name: String,
    private val moduleID: String,
    private val knownFunctions: HashMap<String, MLIRTypeID>,
) {
    private var instCounter = CounterReference()
    private var blockCounter: Long = 0

    private val blocks = mutableListOf<Model.MLIRBlock>()

    private var argumentTypeIDs = emptyList<MLIRTypeID>()
    private var returnTypeID: MLIRTypeID = "void".asTypeID()

    private val registeredTypes = hashSetOf<MLIRTypeID>()

    private fun String.asTypeID(): MLIRTypeID =
        MLIRTypeID.newBuilder().setId(this).setModuleId(MLIRModuleID.newBuilder().setId(moduleID).build()).build()

    init {
        val old = knownFunctions.put(name, returnTypeID)
        if (old != null) {
            throw RuntimeException("Function with name $name has been already registered in the module $moduleID")
        }
    }

    private fun List<MLIRTypeID>.asString() = buildString {
        for (arg in this@asString) {
            append(arg.id)
        }
    }

    // Function type information
    private val functionTypeID
        get() = "${returnTypeID.id} $name(${argumentTypeIDs.asString()})".asTypeID()
    val functionType: MLIRType
        get() {
            val funcType =
                Type.CIRFuncType.newBuilder().addAllInputs(argumentTypeIDs).setReturnType(returnTypeID).build()
            return MLIRType.newBuilder().setCirFuncType(funcType).setId(functionTypeID).build()
        }

    fun arg(idx: Int): MLIRValue {
        // We may want to use arguments while constructing the first block
        val firstBlockID = if (blocks.isEmpty()) {
            MLIRBlockID.newBuilder().setId(blockCounter).build()
        } else {
            blocks.first().id
        }

        val firstBlockArgument =
            MLIRBlockArgument.newBuilder().setArgNumber(idx.toLong()).setOwner(firstBlockID).build()
        return MLIRValue.newBuilder().setBlockArgument(firstBlockArgument).setType(argumentTypeIDs[idx]).build()
    }

    fun build(): CIRFunction {
        val functionInfo =
            CIRFuncOp.newBuilder().setFunctionType(Attr.MLIRTypeAttr.newBuilder().setValue(functionTypeID)).build()
        val blockList = MLIRBlockList.newBuilder().addAllBlock(blocks)
        return CIRFunction.newBuilder().setBlocks(blockList).setId(
            Setup.CIRFunctionID.newBuilder().setId(name)
                .setModuleId(Setup.MLIRModuleID.newBuilder().setId(moduleID).build()).build()
        ).setInfo(functionInfo).build()
    }

    fun withArgumentTypes(vararg args: String) = apply {
        argumentTypeIDs = args.map { it.asTypeID() }
    }

    fun withReturnType(type: String) = apply {
        returnTypeID = type.asTypeID()
        knownFunctions[name] = returnTypeID
    }

    private fun appendBlockFromBlockBuilder(blockAndTypes: RPCBlockBuilderResult) {
        registeredTypes.addAll(blockAndTypes.registeredTypes)
        blocks.add(blockAndTypes.block)
    }

    fun withBlock(builder: RPCBlockBuilder.() -> Unit) = apply {
        val blockBuilder = if (blocks.isEmpty()) {
            RPCBlockBuilder(knownFunctions, instCounter, blockCounter++, moduleID, *argumentTypeIDs.toTypedArray())
        } else {
            RPCBlockBuilder(knownFunctions, instCounter, blockCounter++, moduleID)
        }
        blockBuilder.builder()
        appendBlockFromBlockBuilder(blockBuilder.build())
    }
}
