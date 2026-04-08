package org.seqra.dataflow.cir.ifds

import org.seqra.dataflow.ifds.SingletonUnit
import org.seqra.dataflow.ifds.UnitResolver
import org.seqra.dataflow.ifds.UnitType
import org.seqra.ir.api.cir.cfg.CIRFunction

data class CIRFunctionUnit(val function: CIRFunction) : UnitType {
    override fun toString(): String = "CIRFunctionUnit(${function.name})"
}

data class CIRModuleUnit(val moduleName: String) : UnitType {
    override fun toString(): String = "CIRModuleUnit($moduleName)"
}

fun interface CIRUnitResolver : UnitResolver<CIRFunction>

val CIRSingletonUnitResolver = CIRUnitResolver { SingletonUnit }

val CIRFunctionUnitResolver = CIRUnitResolver { method -> CIRFunctionUnit(method) }

val CIRModuleUnitResolver = CIRUnitResolver { method -> CIRModuleUnit(method.id.moduleID.id) }
