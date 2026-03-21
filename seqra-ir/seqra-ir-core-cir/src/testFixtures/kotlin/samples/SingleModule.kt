package samples

import builders.RPCModuleBuilder

val singleModuleFile = RPCModuleBuilder("singleModule").withFunction("source") {
    withReturnType("void*").build()
}.withFunction("sink") {
    withArgumentTypes("void*").build()
}.withFunction("main") {
    withBlock {
        val `return` = appendAlloca("int")
        val `1` = appendAlloca("void*")
        val `2` = appendCall("source")
        appendStore(`1`, `2`)
        val `3` = appendLoad(`1`)
        appendCall("sink", `3`)
        appendReturn(`return`)
    }.build()
}.buildToFile()
