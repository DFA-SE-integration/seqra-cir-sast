@file:JvmName("CIRUsagesExt")
@file:OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)

package org.seqra.ir.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.cfg.CIRFunction
import java.util.concurrent.Future

class SyncCIRUsagesExtension(private val cp: CIRClasspath) {

    fun findUsages(method: CIRFunction): Sequence<CIRFunction> {
        val req = CIRUsageFeatureRequest(setOf(method.name))
        return CIRUsages.syncQuery(cp, req)
            .mapNotNull { cp.findFunctionOrNull(it.source.functionID) }
            .distinct()
    }
}

suspend fun CIRClasspath.usagesExt(): SyncCIRUsagesExtension {
    if (!db.isInstalled(CIRUsages)) {
        throw IllegalStateException("This extension requires `CIRUsages` feature to be installed")
    }
    return SyncCIRUsagesExtension(this)
}

fun CIRClasspath.asyncUsages(): Future<SyncCIRUsagesExtension> = GlobalScope.future { usagesExt() }

suspend fun CIRClasspath.findUsages(method: CIRFunction) = usagesExt().findUsages(method)
