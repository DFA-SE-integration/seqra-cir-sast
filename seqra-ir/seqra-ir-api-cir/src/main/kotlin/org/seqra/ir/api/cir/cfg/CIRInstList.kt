package org.seqra.ir.api.cir.cfg

interface CIRInstList<INST> : Iterable<INST> {
    val instructions: List<INST>
    val size: Int
    val indices: IntRange
    val lastIndex: Int

    operator fun get(index: Int): INST
    fun getOrNull(index: Int): INST?

    fun toMutableList(): CIRMutableInstList<INST>
    fun isEmpty() = size == 0
}

interface CIRMutableInstList<INST> : CIRInstList<INST> {
    fun insertBefore(inst: INST, vararg newInstructions: INST)
    fun insertBefore(inst: INST, newInstructions: Collection<INST>)
    fun insertAfter(inst: INST, vararg newInstructions: INST)
    fun insertAfter(inst: INST, newInstructions: Collection<INST>)
    fun remove(inst: INST): Boolean
    fun removeAll(inst: Collection<INST>): Boolean
}