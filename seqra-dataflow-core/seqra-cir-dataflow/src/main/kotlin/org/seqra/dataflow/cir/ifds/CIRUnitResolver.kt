@file:Suppress("FunctionName")

package org.seqra.dataflow.cir.ifds

import org.seqra.dataflow.ifds.SingletonUnit
import org.seqra.dataflow.ifds.UnitResolver
import org.seqra.dataflow.ifds.UnitType
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.MLIRModuleID

data class FunctionUnit(val function: CIRFunction) : UnitType {
    override fun toString(): String {
        return "FunctionUnit(${function.name})"
    }
}

data class ModuleUnit(val moduleId: MLIRModuleID) : UnitType {
    override fun toString(): String {
        return "ModuleUnit(${moduleId.id})"
    }
}

fun interface CIRUnitResolver : UnitResolver<CIRFunction>

val FunctionUnitResolver = CIRUnitResolver { function ->
    FunctionUnit(function)
}

val ModuleUnitResolver = CIRUnitResolver { function ->
    ModuleUnit(function.id.moduleID)
}

val SingletonUnitResolver = CIRUnitResolver {
    SingletonUnit
}
