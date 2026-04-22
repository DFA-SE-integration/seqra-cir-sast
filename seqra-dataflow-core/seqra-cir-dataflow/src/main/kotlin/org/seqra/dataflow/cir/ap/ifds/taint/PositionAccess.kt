package org.seqra.dataflow.cir.ap.ifds.taint

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor

sealed interface PositionAccess {
    data class Simple(val base: AccessPathBase) : PositionAccess
    data class Complex(val base: PositionAccess, val accessor: Accessor) : PositionAccess
}