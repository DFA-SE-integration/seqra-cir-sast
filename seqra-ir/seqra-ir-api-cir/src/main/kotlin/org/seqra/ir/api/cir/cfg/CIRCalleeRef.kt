package org.seqra.ir.api.cir.cfg

import org.seqra.ir.api.cir.CIRClasspath

class CIRCalleeRef(
    val symbolName: String,
    private val classpath: CIRClasspath,
) {
    val function: CIRFunction? by lazy {
        classpath.findFunctionBySymbolName(symbolName)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CIRCalleeRef) return false
        return symbolName == other.symbolName && classpath === other.classpath
    }

    override fun hashCode(): Int = 31 * symbolName.hashCode() + System.identityHashCode(classpath)

    override fun toString(): String = "CIRCalleeRef($symbolName)"
}
