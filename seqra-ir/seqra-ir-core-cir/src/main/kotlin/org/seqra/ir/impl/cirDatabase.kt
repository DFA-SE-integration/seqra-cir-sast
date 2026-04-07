package org.seqra.ir.impl

import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.impl.sources.RPCSourceLoader

fun cirDatabase(builder: CIRSettings.() -> Unit): CIRDatabase {
    return cirDatabase(CIRSettings().also(builder))
}

fun cirDatabase(settings: CIRSettings): CIRDatabase {
    return CIRDatabaseImpl(settings, RPCSourceLoader())
}
