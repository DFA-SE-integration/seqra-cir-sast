package org.seqra.ir.api.cir.cfg

import org.seqra.ir.api.common.CommonType
import org.seqra.ir.api.common.cfg.CommonExpr
import org.seqra.ir.api.common.cfg.CommonInst
import org.seqra.ir.api.common.cfg.CommonValue

// Common

/** CIRRegionBranchOpInterface interface */
interface CIRRegionBranchOpInterface

interface CIRThrowInterface

interface CIRCatchInterface

// Inst

interface CIRInst : CommonInst {
    override val location: CIRInstLocation
    val id: MLIROpID

    val method: CIRFunction
        get() = location.method
}

/** Terminator trait */
interface CIRTerminatingInst : CIRInst

// Expressions

interface CIRExpr : CommonExpr {
    val type: MLIRTypeID
    override val typeName: String
        get() = type.id
}

// Values

open class MLIRValue(
    override val type: MLIRTypeID,
) : CIRExpr, CommonValue

data class MLIROpValue(
    override val type: MLIRTypeID,
    val opIndex: MLIROpID,
    val resultIndex: Long,
) : MLIRValue(type)

data class MLIRBlockValue(
    override val type: MLIRTypeID,
    val blockIndex: MLIRBlockID,
    val argIndex: Long,
) : MLIRValue(type)

data class MLIRValueRef(val value: MLIRValue) : MLIRValue(value.type)

// Types

interface MLIRType : CommonType {
    val id: MLIRTypeID
    override val nullable: Boolean
        get() = false

    override val typeName: String
        get() = id.typeName
}
