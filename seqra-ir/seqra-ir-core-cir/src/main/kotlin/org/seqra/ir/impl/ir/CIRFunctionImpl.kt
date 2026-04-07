package org.seqra.ir.impl.ir

import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRFunctionSource
import org.seqra.ir.api.cir.cfg.*
import org.seqra.ir.impl.cfg.builder.buildCIRFuncOp
import org.seqra.ir.impl.features.CIRFeaturesChain
import org.seqra.ir.impl.features.CIRFunctionExtFeature
import org.seqra.ir.impl.grpc.Op

class CIRFunctionImpl(
    private val source: CIRFunctionSource,
    private val featuresChain: CIRFeaturesChain,
    override val classpath: CIRClasspath,
) : CIRFunction {
    // ID
    override val id: CIRFunctionID = source.functionID

    // Info
    override val info: CIRFuncOp
        get() = buildCIRFuncOp(Op.CIRFuncOp.parseFrom(source.infoNode))

    // Types
    private val typeResolver = CIRFunctionTypeResolver(classpath = classpath, function = this)
    override val returnType: MLIRTypeID
        get() = typeResolver.returnType()
    override val parameters: List<CIRFunctionParameter>
        get() = typeResolver.parameterTypes().mapIndexed { idx, typeId -> CIRParameterImpl(typeId, idx, this) }

    // Graphs
    override fun flowGraph() =
        featuresChain.run<CIRFunctionExtFeature, CIRFunctionExtFeature.CIRFlowGraphResult> { it.flowGraph(this) }!!.flowGraph

    override val blocks: CIRBlockList
        get() = featuresChain.run<CIRFunctionExtFeature, CIRFunctionExtFeature.CIRBlockListResult> { it.blockList(this) }!!.blockList

    // Utils
    override fun <T> withIRNode(body: (ByteArray?) -> T): T {
        return body(source.bytecodeNode)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is CIRFunctionImpl) {
            return false
        }
        return other.id == id
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        val signature = buildString {
            append("fun $name(")
            parameters.forEachIndexed { idx, param ->
                append(param)
                if (idx + 1 != parameters.size) {
                    append(", ")
                }
            }
            append(") -> ${returnType.id}")
        }
        return "$signature:\n${blocks}"
    }
}