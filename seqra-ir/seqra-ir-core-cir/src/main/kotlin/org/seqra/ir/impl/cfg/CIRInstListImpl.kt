package org.seqra.ir.impl.cfg

import org.seqra.ir.api.cir.cfg.CIRInstList
import org.seqra.ir.api.cir.cfg.CIRMutableInstList
import org.seqra.ir.api.common.cfg.MutableInstList

open class CIRInstListImpl<INST>(
    instructions: List<INST>,
) : Iterable<INST>, CIRInstList<INST> {
    protected val mutableInstructions = instructions.toMutableList()

    override val instructions: List<INST> get() = mutableInstructions

    override val size get() = instructions.size
    override val indices get() = instructions.indices
    override val lastIndex get() = instructions.lastIndex

    override operator fun get(index: Int) = instructions[index]
    override fun getOrNull(index: Int) = instructions.getOrNull(index)
    fun getOrElse(index: Int, defaultValue: (Int) -> INST) = instructions.getOrElse(index, defaultValue)
    override fun iterator(): Iterator<INST> = instructions.iterator()

    override fun toMutableList() = CIRMutableInstListImpl(mutableInstructions)

    override fun toString(): String = mutableInstructions.joinToString(separator = "\n") {
        when (it) {
//            is JcRawLabelInst -> "$it"
            else -> "  $it"
        }
    }
}

class CIRMutableInstListImpl<INST>(
    instructions: List<INST>,
) : CIRInstListImpl<INST>(instructions), CIRMutableInstList<INST> {

    override fun insertBefore(inst: INST, vararg newInstructions: INST) = insertBefore(inst, newInstructions.toList())
    override fun insertBefore(inst: INST, newInstructions: Collection<INST>) {
        val index = mutableInstructions.indexOf(inst)
        assert(index >= 0)
        mutableInstructions.addAll(index, newInstructions)
    }

    override fun insertAfter(inst: INST, vararg newInstructions: INST) = insertAfter(inst, newInstructions.toList())
    override fun insertAfter(inst: INST, newInstructions: Collection<INST>) {
        val index = mutableInstructions.indexOf(inst)
        assert(index >= 0)
        mutableInstructions.addAll(index + 1, newInstructions)
    }

    override fun remove(inst: INST): Boolean {
        return mutableInstructions.remove(inst)
    }

    override fun removeAll(inst: Collection<INST>): Boolean {
        return mutableInstructions.removeAll(inst)
    }
}

fun <T> mutableInstListOf(vararg inst: T) = CIRMutableInstListImpl(inst.toList())
fun <T> instListOf(vararg inst: T) = CIRInstListImpl(inst.toList())
