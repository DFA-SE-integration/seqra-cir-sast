package org.seqra.ir.impl

import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.impl.sources.RPCSourceLoader

fun jacodb(builder: CIRSettings.() -> Unit): CIRDatabase {
    return jacodb(CIRSettings().also(builder))
}

fun jacodb(settings: CIRSettings): CIRDatabase {
    return CIRDatabaseImpl(settings, RPCSourceLoader())
}
