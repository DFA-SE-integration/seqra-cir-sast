package org.seqra.ir.impl.cfg.builder

import org.seqra.ir.api.cir.cfg.*

import org.seqra.ir.impl.grpc.Attr
import org.seqra.ir.impl.grpc.Setup

fun buildMLIRAttributeArray(proto: List<Attr.MLIRAttribute>) : ArrayList<MLIRAttribute> {
    val list : ArrayList<MLIRAttribute> = arrayListOf()
    for (attr in proto) {
        list.add(buildMLIRAttribute(attr))
    }
    return list
}

fun buildMLIRNamedAttrArray(proto: List<Attr.MLIRNamedAttr>) : ArrayList<MLIRNamedAttr> {
    val list : ArrayList<MLIRNamedAttr> = arrayListOf()
    for (attr in proto) {
        list.add(buildMLIRNamedAttr(attr))
    }
    return list
}

fun buildMLIRLocationArray(proto: List<Attr.MLIRLocation>) : ArrayList<MLIRLocation> {
    val list : ArrayList<MLIRLocation> = arrayListOf()
    for (attr in proto) {
        list.add(buildMLIRLocation(attr))
    }
    return list
}

fun buildMLIRTypeIDArray(proto: List<Setup.MLIRTypeID>) : ArrayList<MLIRTypeID> {
    val list : ArrayList<MLIRTypeID> = arrayListOf()
    for (id in proto) {
        list.add(buildMLIRTypeID(id))
    }
    return list
}

fun buildBooleanArray(proto: List<Boolean>) : ArrayList<Boolean> {
    val list : ArrayList<Boolean> = arrayListOf()
    for (int in proto) {
        list.add(int)
    }
    return list
}

fun buildI32Array(proto: List<Int>) : ArrayList<Int> {
    val list : ArrayList<Int> = arrayListOf()
    for (int in proto) {
        list.add(int)
    }
    return list
}

fun buildI64Array(proto: List<Long>) : ArrayList<Long> {
    val list : ArrayList<Long> = arrayListOf()
    for (int in proto) {
        list.add(int)
    }
    return list
}

fun buildMLIRValue(value: Setup.MLIRValue): MLIRValue {
    return when (value.valueCase!!) {
        Setup.MLIRValue.ValueCase.OP_RESULT -> {
            MLIROpValue(
                buildMLIRTypeID(value.type), buildMLIROpID(value.opResult.owner), value.opResult.resultNumber
            )
        }

        Setup.MLIRValue.ValueCase.BLOCK_ARGUMENT -> {
            MLIRBlockValue(
                buildMLIRTypeID(value.type), buildMLIRBlockID(value.blockArgument.owner), value.blockArgument.argNumber
            )
        }

        Setup.MLIRValue.ValueCase.VALUE_NOT_SET -> throw Exception()
    }
}

fun buildMLIRValueArray(proto: List<Setup.MLIRValue>) : ArrayList<MLIRValue> {
    val list : ArrayList<MLIRValue> = arrayListOf()
    for (value in proto) {
        list.add(buildMLIRValue(value))
    }
    return list
}

fun buildMLIRValueArrayArray(proto: List<Setup.MLIRValueList>) : ArrayList<ArrayList<MLIRValue>> {
    val list: ArrayList<ArrayList<MLIRValue>> = arrayListOf()
    for (valueList in proto) {
        val innerList: ArrayList<MLIRValue> = arrayListOf()
        for (value in valueList.listList) {
            innerList.add(buildMLIRValue(value))
        }
        list.add(innerList)
    }
    return list
}

fun buildMLIROpID(proto: Setup.MLIROpID) : MLIROpID {
    return MLIROpID(proto.id)
}

fun buildMLIRBlockID(proto: Setup.MLIRBlockID) : MLIRBlockID {
    return MLIRBlockID(proto.id)
}

fun buildMLIRBlockIDArray(proto: List<Setup.MLIRBlockID>) : ArrayList<MLIRBlockID> {
    val list : ArrayList<MLIRBlockID> = arrayListOf()
    for (block in proto) {
        list.add(buildMLIRBlockID(block))
    }
    return list
}