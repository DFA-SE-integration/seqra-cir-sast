package builders

import org.seqra.ir.api.cir.cfg.MLIRLocation
import org.seqra.ir.api.cir.cfg.MLIRStringAttr
import org.seqra.ir.impl.grpc.Attr
import org.seqra.ir.impl.grpc.Attr.MLIRUnknownLoc
import org.seqra.ir.impl.grpc.Model
import org.seqra.ir.impl.grpc.Op
import org.seqra.ir.impl.grpc.Setup
import org.seqra.ir.impl.grpc.Setup.MLIRBlockID
import org.seqra.ir.impl.grpc.Setup.MLIRTypeID
import org.seqra.ir.impl.sources.CIRLocation

data class RPCBlockBuilderResult(
    val block: Model.MLIRBlock, val registeredTypes: Set<MLIRTypeID>
)

class RPCBlockBuilder(
    private val knownFunctions: HashMap<String, MLIRTypeID>,
    private val instCounter: CounterReference,
    private val id: Long,
    moduleID: String,
    vararg args: MLIRTypeID
) {
    private val instructions = mutableListOf<Op.MLIROp>()
    private var arguments = args.asList()

    // Type building
    private val typeBuilder = RPCTypeIDBuilder.inModule(moduleID)

    private val registeredTypes = hashSetOf<MLIRTypeID>()

    private fun RPCTypeIDBuilder.buildRegistered(): MLIRTypeID {
        val typeId = this.build()
        registeredTypes.add(typeId)
        return typeId
    }

    private fun createUnknownLocation() =
        Attr.MLIRLocation.newBuilder().setUnknownLoc(MLIRUnknownLoc.newBuilder()).build()

    fun build(): RPCBlockBuilderResult {
        val block = Model.MLIRBlock.newBuilder().addAllOperations(instructions).addAllArgumentTypes(arguments)
            .setId(MLIRBlockID.newBuilder().setId(id).build()).build()
        return RPCBlockBuilderResult(block, registeredTypes)
    }

    fun appendAlloca(allocaType: String): Setup.MLIRValue {
        val id = instCounter.getAndInc()
        val allocaTypeId = typeBuilder.withType(allocaType).buildRegistered()

        val allocaReferencedTypeId = typeBuilder.withType(allocaType).referenced().buildRegistered()
        val alloca =
            Op.MLIROp.newBuilder().allocaOpBuilder.setAllocaType(Attr.MLIRTypeAttr.newBuilder().setValue(allocaTypeId))
                .setAddr(allocaReferencedTypeId)

        instructions.add(
            Op.MLIROp.newBuilder().setAllocaOp(alloca).setId(id.asOpID()).setLocation(createUnknownLocation()).build()
        )
        return id.asTypedValue(allocaReferencedTypeId)
    }

    fun appendLoad(address: Setup.MLIRValue): Setup.MLIRValue {
        val id = instCounter.getAndInc()
        val type = typeBuilder.withType(address.type).dereferenced().buildRegistered()
        val load = Op.MLIROp.newBuilder().loadOpBuilder.setAddr(address).setResult(type)
        //
        instructions.add(
            Op.MLIROp.newBuilder().setLoadOp(load).setId(id.asOpID()).setLocation(createUnknownLocation()).build()
        )
        return id.asTypedValue(type)
    }

    fun appendStore(address: Setup.MLIRValue, value: Setup.MLIRValue) {
        val id = instCounter.getAndInc()
        val store = Op.MLIROp.newBuilder().storeOpBuilder.setAddr(address).setValue(value).build()
        instructions.add(
            Op.MLIROp.newBuilder().setStoreOp(store).setId(id.asOpID()).setLocation(createUnknownLocation()).build()
        )
    }

    fun appendCall(functionName: String, vararg ts: Setup.MLIRValue): Setup.MLIRValue {
        val id = instCounter.getAndInc()
        val callBuilder = Op.MLIROp.newBuilder().callOpBuilder.setCallee(
            Attr.MLIRFlatSymbolRefAttr.newBuilder()
                .setRootReference(Attr.MLIRStringAttr.newBuilder().setValue(functionName))
        )

        // Ha-ha, did you really think in Kotlin you may declare functions in any order? =)
        // In fact, it is a way to avoid to specifying `returnType` as an argument of the `appendCall`
        val returnType = knownFunctions[functionName] ?: throw RuntimeException("Undefined reference to $functionName")

        callBuilder.addAllArgOps(ts.asIterable()).setResult(returnType)
        instructions.add(
            Op.MLIROp.newBuilder().setCallOp(callBuilder.build()).setId(id.asOpID())
                .setLocation(createUnknownLocation()).build()
        )
        return id.asTypedValue(returnType)
    }

    fun appendReturn(value: Setup.MLIRValue) {
        val id = instCounter.getAndInc()
        val `return` = Op.MLIROp.newBuilder().returnOpBuilder.addInput(0, value).build()
        instructions.add(
            Op.MLIROp.newBuilder().setReturnOp(`return`).setId(id.asOpID()).setLocation(createUnknownLocation()).build()
        )
    }
}

