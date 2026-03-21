package org.seqra.ir.api.cir

import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.common.CommonProject
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRGlobal
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.MLIRType
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import java.io.Closeable

interface CIRClasspath : Closeable, CommonProject {
    val db: CIRDatabase

    val registeredLocations: List<RegisteredLocation>
    val registeredLocationIds: Set<Long>
    val features: List<CIRClasspathFeature>

    val moduleNames: List<String>

    // Getters for functions
    fun findFunctionOrNull(functionID: CIRFunctionID): CIRFunction?

    // Getters for types
    fun findTypeOrNull(typeID: MLIRTypeID): MLIRType?

    // Getters for globals
    fun findGlobalOrNull(globalID: CIRGlobalID): CIRGlobal?

    fun getGlobalConstructors(): List<CIRFunctionID>
    fun getGlobalDestructors(): List<CIRFunctionID>
}

interface CIRClasspathFeature {
    fun on(event: CIRFeatureEvent) {
    }

    fun event(result: Any): CIRFeatureEvent? = null
}

interface CIRFeatureEvent {
    val feature: CIRClasspathFeature
    val result: Any
}

interface CIRClasspathExtFeature : CIRClasspathFeature {
    interface CIRResolvedFunctionResult {
        val name: CIRFunctionID
        val function: CIRFunction?
    }

    interface CIRResolvedTypeResult {
        val name: MLIRTypeID
        val type: MLIRType?
    }

    interface CIRResolvedGlobalResult {
        val name: CIRGlobalID
        val global: CIRGlobal?
    }

    fun tryFindGlobal(globalID: CIRGlobalID): CIRResolvedGlobalResult? = null
    fun tryFindType(typeID: MLIRTypeID): CIRResolvedTypeResult? = null
    fun tryFindFunction(functionID: CIRFunctionID): CIRResolvedFunctionResult? = null
}

interface CIRInstExtFeature : CIRClasspathFeature {
    fun transformBlockList(function: CIRFunction, blockList: CIRBlockList): CIRBlockList
}
