package org.seqra.ir.impl.features

import org.seqra.ir.api.cir.ByteCodeIndexer
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.CIRFeature
import org.seqra.ir.api.cir.CIRFunctionSource
import org.seqra.ir.api.cir.CIRSignal
import org.seqra.ir.api.cir.IRNode
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.storage.ers.links
import org.seqra.ir.impl.sources.ModuleIRNode
import org.seqra.ir.impl.sources.PersistenceCIRFunctionSource
import org.seqra.ir.impl.grpc.Model
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.or

internal fun CIRFunctionID.consolidatedId(): String = "${moduleID.id}#$id"

private object CIRUsagesEntity {
    const val CALLEE = "CIRCallee"
    object Callee {
        const val SYMBOL = "calleeSymbol"
        const val LOCATION = "locationId"
    }

    const val CALL = "CIRCall"
    object Call {
        const val CALLER_CONSOLIDATED = "callerConsolidated"
        const val OFFSETS = "offsets"
    }

    const val LINK_CALLS = "calls"
}

/** Deduplicated linear op indices within a caller function (fits in [Short]). */
private class OpIndexCollector(private val maxOps: Int) {
    private val ticks = ByteArray((maxOps - 1).coerceAtLeast(1) / 8 + 1)
    private val array = ShortArray(maxOps.coerceAtLeast(1))
    private var position = 0

    fun tick(index: Int) {
        require(index in 0 until maxOps) { "op index $index out of range [0, $maxOps)" }
        val arrayIndex = index shr 3
        if ((ticks[arrayIndex] and (1 shl (index and 7)).toByte()) == 0.toByte()) {
            array[position] = index.toShort()
            ticks[arrayIndex] = ticks[arrayIndex] or (1 shl (index and 7)).toByte()
            position++
        }
    }

    fun result(): ByteArray {
        val pos = position
        return ByteBuffer.allocate(pos * 2).also { it.asShortBuffer().put(array, 0, pos) }.array()
    }
}

private class CIRUsagesIndexer(
    private val db: CIRDatabase,
    private val location: RegisteredLocation,
) : ByteCodeIndexer {

    private val usages = hashMapOf<String, HashMap<String, OpIndexCollector>>()

    override fun index(moduleNode: IRNode) {
        val module = moduleNode as? ModuleIRNode ?: return
        val moduleInfo = module.asModuleInfo()
        for (functionInfo in moduleInfo.functions) {
            val blockList = try {
                Model.MLIRBlockList.parseFrom(functionInfo.blocksNode)
            } catch (_: Exception) {
                continue
            }
            val totalOps = blockList.blockList.sumOf { it.operationsCount }
            if (totalOps == 0) {
                continue
            }
            val callerCons = functionInfo.id.consolidatedId()
            var opIndex = 0
            for (block in blockList.blockList) {
                for (op in block.operationsList) {
                    when {
                        op.hasCallOp() && op.callOp.hasCallee() -> {
                            val sym = op.callOp.callee.rootReference.value.intern()
                            record(sym, callerCons, totalOps, opIndex)
                        }

                        op.hasTryCallOp() && op.tryCallOp.hasCallee() -> {
                            val sym = op.tryCallOp.callee.rootReference.value.intern()
                            record(sym, callerCons, totalOps, opIndex)
                        }
                    }
                    opIndex++
                }
            }
        }
    }

    private fun record(calleeSymbol: String, callerConsolidated: String, callerOpCount: Int, opIndex: Int) {
        usages.getOrPut(calleeSymbol) { hashMapOf() }
            .getOrPut(callerConsolidated) { OpIndexCollector(callerOpCount) }
            .tick(opIndex)
    }

    override fun flush() {
        if (usages.isEmpty()) {
            return
        }
        db.persistence.write { txn ->
            txn.find(CIRUsagesEntity.CALLEE, CIRUsagesEntity.Callee.LOCATION, location.id).toList().forEach { callee ->
                callee.getLinks(CIRUsagesEntity.LINK_CALLS).toList().forEach { it.delete() }
                callee.delete()
            }
            usages.forEach { (calleeSymbol, callers) ->
                val callee = txn.newEntity(CIRUsagesEntity.CALLEE).also { e ->
                    e[CIRUsagesEntity.Callee.SYMBOL] = calleeSymbol
                    e[CIRUsagesEntity.Callee.LOCATION] = location.id
                }
                val calls = links(callee, CIRUsagesEntity.LINK_CALLS)
                callers.forEach { (callerConsolidated, collector) ->
                    val call = txn.newEntity(CIRUsagesEntity.CALL)
                    call[CIRUsagesEntity.Call.CALLER_CONSOLIDATED] = callerConsolidated
                    call.setRawBlob(CIRUsagesEntity.Call.OFFSETS, collector.result())
                    calls += call
                }
            }
        }
        usages.clear()
    }
}

object CIRUsages : CIRFeature<CIRUsageFeatureRequest, CIRUsageFeatureResponse> {

    override suspend fun query(classpath: CIRClasspath, req: CIRUsageFeatureRequest): Sequence<CIRUsageFeatureResponse> {
        return syncQuery(classpath, req)
    }

    fun syncQuery(classpath: CIRClasspath, req: CIRUsageFeatureRequest): Sequence<CIRUsageFeatureResponse> {
        if (req.calleeSymbols.isEmpty()) {
            return emptySequence()
        }
        val locationIds = classpath.registeredLocationIds
        val persistence = classpath.db.persistence
        return persistence.read { txn ->
            val byCaller = linkedMapOf<String, MutableList<Short>>()
            for (symbol in req.calleeSymbols) {
                txn.find(CIRUsagesEntity.CALLEE, CIRUsagesEntity.Callee.SYMBOL, symbol)
                    .filter { it.get<Long>(CIRUsagesEntity.Callee.LOCATION)!! in locationIds }
                    .forEach { callee ->
                        callee.getLinks(CIRUsagesEntity.LINK_CALLS).forEach { call ->
                            val callerCons = call.get<String>(CIRUsagesEntity.Call.CALLER_CONSOLIDATED)!!
                            val offsets = call.getRawBlob(CIRUsagesEntity.Call.OFFSETS)!!.toShortArray()
                            val merged = byCaller.getOrPut(callerCons) { mutableListOf() }
                            for (o in offsets) {
                                if (o !in merged) {
                                    merged.add(o)
                                }
                            }
                        }
                    }
            }
            byCaller.map { (callerCons, shorts) ->
                val fid = parseConsolidatedFunctionId(callerCons)
                val source: CIRFunctionSource = PersistenceCIRFunctionSource(
                    classpath,
                    fid,
                    fid.moduleID.id,
                )
                CIRUsageFeatureResponse(source, shorts.sorted().toShortArray())
            }
        }.asSequence()
    }

    override fun newIndexer(jcdb: CIRDatabase, location: RegisteredLocation): ByteCodeIndexer =
        CIRUsagesIndexer(jcdb, location)

    override fun onSignal(signal: CIRSignal) {
        val db = signal.jcdb
        when (signal) {
            is CIRSignal.BeforeIndexing -> db.persistence.write { txn ->
                if (signal.clearOnStart) {
                    dropUsages(txn)
                }
            }

            is CIRSignal.LocationRemoved -> db.persistence.write { txn ->
                removeLocationUsages(txn, signal.location.id)
            }

            is CIRSignal.Drop -> db.persistence.write { txn ->
                dropUsages(txn)
            }

            else -> Unit
        }
    }

    private fun dropUsages(txn: org.seqra.ir.api.storage.ers.Transaction) {
        txn.all(CIRUsagesEntity.CALLEE).toList().forEach { callee ->
            callee.getLinks(CIRUsagesEntity.LINK_CALLS).toList().forEach { it.delete() }
            callee.delete()
        }
    }

    private fun removeLocationUsages(txn: org.seqra.ir.api.storage.ers.Transaction, locationId: Long) {
        txn.find(CIRUsagesEntity.CALLEE, CIRUsagesEntity.Callee.LOCATION, locationId).toList().forEach { callee ->
            callee.getLinks(CIRUsagesEntity.LINK_CALLS).toList().forEach { it.delete() }
            callee.delete()
        }
    }
}

private fun parseConsolidatedFunctionId(consolidated: String): CIRFunctionID {
    val sep = consolidated.indexOf('#')
    require(sep > 0 && sep < consolidated.length - 1) { "Bad consolidated function id: $consolidated" }
    return CIRFunctionID(
        MLIRModuleID(consolidated.substring(0, sep)),
        consolidated.substring(sep + 1),
    )
}

private fun ByteArray.toShortArray(): ShortArray {
    return ShortArray(size / 2).also {
        ByteBuffer.wrap(this).asShortBuffer().get(it)
    }
}
