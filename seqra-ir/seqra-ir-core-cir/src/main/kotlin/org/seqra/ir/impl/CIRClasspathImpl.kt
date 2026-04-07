package org.seqra.ir.impl

import mu.KLogging
import org.seqra.ir.api.cir.*
import org.seqra.ir.api.cir.CIRClasspathExtFeature.*
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRGlobal
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.MLIRType
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.impl.cfg.builder.buildCIRGlobalID
import org.seqra.ir.impl.cfg.builder.buildCIRGlobalOp
import org.seqra.ir.impl.cfg.builder.buildMLIRType
import org.seqra.ir.impl.features.AbstractCIRResolvedResult.*
import org.seqra.ir.impl.features.CIRFeatureEventImpl
import org.seqra.ir.impl.features.CIRFeaturesChain
import org.seqra.ir.impl.grpc.Model
import org.seqra.ir.impl.grpc.Type
import org.seqra.ir.impl.ir.CIRFunctionImpl

val logger = object : KLogging() {}.logger

class CIRClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: CIRDatabase,
    override val features: List<CIRClasspathFeature>
) : CIRClasspath {
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations
    override val registeredLocationIds: Set<Long> = registeredLocations.map { it.id }.toSet()

    private val featuresChain = CIRFeaturesChain(features + CIRClasspathFeatureImpl())

    init {
        assert(registeredLocationIds.isNotEmpty())
    }

    override val moduleNames: List<String>
        get() = db.persistence.findModules(this)

    // Searchers
    override fun findFunctionOrNull(functionID: CIRFunctionID) =
        featuresChain.run<CIRClasspathExtFeature, CIRResolvedFunctionResult> {
            it.tryFindFunction(functionID)
        }?.function

    override fun findTypeOrNull(typeID: MLIRTypeID) = featuresChain.run<CIRClasspathExtFeature, CIRResolvedTypeResult> {
        it.tryFindType(typeID)
    }?.type

    override fun findGlobalOrNull(globalID: CIRGlobalID) =
        featuresChain.run<CIRClasspathExtFeature, CIRResolvedGlobalResult> {
            it.tryFindGlobal(globalID)
        }?.global

    // TODO: for now it is a stub
    //  Surely, it must use features chain etc.
    //  But now it is just a hack
    fun findAllFunctionsWithSignature(typeID: MLIRTypeID) = db.persistence.findFunctionsByType(this, typeID)

    override fun getGlobalConstructors(): List<CIRFunctionID> = db.persistence.findGlobalCtors(this)
    override fun getGlobalDestructors(): List<CIRFunctionID> = db.persistence.findGlobalDtors(this)

    // Constructors
    private fun newFunction(source: CIRFunctionSource): CIRFunction {
        return CIRFunctionImpl(source, featuresChain, this)
    }

    private fun newType(source: CIRTypeSource): MLIRType {
        return buildMLIRType(Type.MLIRType.parseFrom(source.node.byteBuffer))
    }

    private fun newGlobal(source: CIRGlobalSource): CIRGlobal {
        val global = Model.CIRGlobal.parseFrom(source.node.byteBuffer)
        return CIRGlobal(buildCIRGlobalID(global.id), buildCIRGlobalOp(global.info))
    }

    // Classpath controllers
    override fun close() {
        locationsRegistrySnapshot.close()
    }

    private inner class CIRClasspathFeatureImpl : CIRClasspathExtFeature {
        override fun tryFindFunction(functionID: CIRFunctionID): CIRResolvedFunctionResult? {
            val functions = db.persistence.findFunctionSources(this@CIRClasspathImpl, functionID)
            assert(functions.size <= 1)

            val singleFunction = functions.firstOrNull()
            return singleFunction?.let { CIRResolvedFunctionResultImpl(functionID, newFunction(singleFunction)) }
        }

        override fun tryFindType(typeID: MLIRTypeID): CIRResolvedTypeResult? {
            val types = db.persistence.findTypeSources(this@CIRClasspathImpl, typeID)
            logger.warn("Found ${types.size} types with name \"${typeID.id}\"")
            return types.firstOrNull()?.let { CIRResolvedTypeResultImpl(typeID, newType(it)) }
        }

        override fun tryFindGlobal(globalID: CIRGlobalID): CIRResolvedGlobalResult? {
            val globals = db.persistence.findGlobalSources(this@CIRClasspathImpl, globalID)
            logger.warn("Found ${globals.size} globals with name \"${globalID.id}\"")
            return globals.firstOrNull()?.let { CIRResolvedGlobalResultImpl(globalID, newGlobal(it)) }
        }

        override fun event(result: Any): CIRFeatureEvent {
            return CIRFeatureEventImpl(this, result)
        }
    }
}
