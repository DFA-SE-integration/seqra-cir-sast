package samples

import builders.RPCModuleBuilder

val doubleModuleMainFile = RPCModuleBuilder("mainModule").withFunction("source") {
        withReturnType("void *").build()
    }.withFunction("sink") {
        withArgumentTypes("void *").build()
    }.withFunction("main") {
        withBlock {
            val `0` = appendAlloca("int")
            val `1` = appendAlloca("void *")
            val `2` = appendCall("source")
            appendStore(
                `1`, `2`
            )
            val `3` = appendLoad(`1`)
            appendCall("sink", `3`)
            val `4` = appendLoad(`0`)
            appendReturn(`4`)
        }.build()
    }.buildToFile()


val doubleModuleHelperFile = RPCModuleBuilder("helperModule").withFunction("internalSource") {
    withReturnType("void*").build()
}.withFunction("internalSink") {
    withArgumentTypes("void*").build()
}.withFunction("source") {
    withReturnType("void*").withBlock {
        val `0` = appendAlloca("void*")
        val `1` = appendCall("internalSource")
        appendStore(`0`, `1`)
        val `2` = appendLoad(`0`)
        appendReturn(`2`)
    }.build()
}.withFunction("sink") {
    withArgumentTypes("void*").withBlock {
        val `1` = appendAlloca("void*")
        appendStore(`1`, arg(0))
        val `2` = appendLoad(`1`)
        appendCall("internalSink", `2`)
        appendReturn(`1`)
    }.build()
}.buildToFile()
