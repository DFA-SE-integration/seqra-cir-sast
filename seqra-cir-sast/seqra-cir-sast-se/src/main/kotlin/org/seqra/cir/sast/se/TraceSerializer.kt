package org.seqra.cir.sast.se

import org.seqra.cir.sast.dataflow.proto.ap.APAccessor
import org.seqra.cir.sast.dataflow.proto.ap.APBase
import org.seqra.cir.sast.dataflow.proto.ap.AnyAccessor
import org.seqra.cir.sast.dataflow.proto.ap.Argument
import org.seqra.cir.sast.dataflow.proto.ap.ElementAccessor
import org.seqra.cir.sast.dataflow.proto.ap.Exception
import org.seqra.cir.sast.dataflow.proto.ap.FieldAccessor
import org.seqra.cir.sast.dataflow.proto.ap.FinalAccessor
import org.seqra.cir.sast.dataflow.proto.ap.LocalVar
import org.seqra.cir.sast.dataflow.proto.ap.ReferenceAccessor
import org.seqra.cir.sast.dataflow.proto.ap.Return
import org.seqra.cir.sast.dataflow.proto.ap.TaintMarkAccessor
import org.seqra.cir.sast.dataflow.proto.ap.This
import org.seqra.cir.sast.dataflow.proto.trace.CallTraceNode
import org.seqra.cir.sast.dataflow.proto.trace.CIRFunctionID
import org.seqra.cir.sast.dataflow.proto.trace.EntryPointToStartTrace
import org.seqra.cir.sast.dataflow.proto.trace.EntryPointTraceNode
import org.seqra.cir.sast.dataflow.proto.trace.FactAp
import org.seqra.cir.sast.dataflow.proto.trace.FullTraceNode
import org.seqra.cir.sast.dataflow.proto.trace.InterProceduralCall
import org.seqra.cir.sast.dataflow.proto.trace.SummaryTraceNode
import org.seqra.cir.sast.dataflow.proto.trace.MLIRModuleID
import org.seqra.cir.sast.dataflow.proto.trace.MLIROpID
import org.seqra.cir.sast.dataflow.proto.trace.SimpleTraceNode
import org.seqra.cir.sast.dataflow.proto.trace.SourceToSinkTrace
import org.seqra.cir.sast.dataflow.proto.trace.SourceToSinkTraceNode
import org.seqra.cir.sast.dataflow.proto.trace.Trace as ProtoTrace
import org.seqra.cir.sast.dataflow.proto.trace.TraceNode
import org.seqra.cir.sast.dataflow.proto.trace.method.FullTrace as ProtoFullTrace
import org.seqra.cir.sast.dataflow.proto.trace.method.SummaryTrace as ProtoSummaryTrace
import org.seqra.cir.sast.dataflow.proto.trace.method.MethodTraceEdge
import org.seqra.cir.sast.dataflow.proto.trace.method.SourceTraceEdge
import org.seqra.cir.sast.dataflow.proto.trace.method.TraceEdge as ProtoMethodTraceEdge
import org.seqra.cir.sast.dataflow.proto.trace.method.TraceEntry as ProtoTraceEntry
import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.ElementAccessor as ElementAcc
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.trace.MethodTraceResolver
import org.seqra.dataflow.ap.ifds.trace.TraceResolver
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.MLIRModuleID as CirMLIRModuleID
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.cfg.CommonInst

private fun emptyTrace(): Nothing = throw RuntimeException("Trace not found!")

private fun notModeled(): Nothing = throw RuntimeException("Not modeled yet!")

/**
 * Per-graph stable ids for protobuf map keys (uint32).
 */
private fun <T> assignSequentialIds(items: Iterable<T>): LinkedHashMap<T, Int> {
    val map = LinkedHashMap<T, Int>()
    var next = 0
    for (item in items) {
        map.putIfAbsent(item, next++)
    }
    return map
}

private fun serializeMlirModuleId(id: CirMLIRModuleID): MLIRModuleID =
    MLIRModuleID.newBuilder().setId(id.id).build()

private fun serializeCirFunctionId(method: CommonMethod): CIRFunctionID {
    val cir = method as? CIRFunction ?: notModeled()
    return CIRFunctionID.newBuilder()
        .setModuleId(serializeMlirModuleId(cir.id.moduleID))
        .setId(cir.id.id)
        .build()
}

private fun serializeMlirOpId(inst: CommonInst): MLIROpID {
    val cirInst = inst as? CIRInst ?: notModeled()
    return MLIROpID.newBuilder()
        .setFunId(serializeCirFunctionId(cirInst.method))
        .setId(cirInst.id.id)
        .build()
}

private fun serializeApBase(base: AccessPathBase): APBase =
    APBase.newBuilder().apply {
        when (base) {
            AccessPathBase.This -> setThis(This.getDefaultInstance())
            is AccessPathBase.LocalVar -> setLv(LocalVar.newBuilder().setIdx(base.idx).build())
            is AccessPathBase.Argument -> setArg(Argument.newBuilder().setIdx(base.idx).build())
            AccessPathBase.Return -> setRet(Return.getDefaultInstance())
            AccessPathBase.Exception -> setExcept(Exception.getDefaultInstance())
            is AccessPathBase.Constant, is AccessPathBase.ClassStatic -> notModeled()
        }
    }.build()

private fun serializeApAccessor(accessor: Accessor): APAccessor =
    APAccessor.newBuilder().apply {
        when (accessor) {
            is org.seqra.dataflow.ap.ifds.TaintMarkAccessor ->
                setTaintMarkAcc(TaintMarkAccessor.newBuilder().setMark(accessor.mark).build())
            is org.seqra.dataflow.ap.ifds.FieldAccessor ->
                setFieldAcc(
                    FieldAccessor.newBuilder()
                        .setClassName(accessor.className)
                        .setFieldName(accessor.fieldName)
                        .setFieldType(accessor.fieldType)
                        .build(),
                )
            ElementAcc -> setElemAcc(ElementAccessor.getDefaultInstance())
            org.seqra.dataflow.ap.ifds.FinalAccessor -> setFinalAcc(FinalAccessor.getDefaultInstance())
            org.seqra.dataflow.ap.ifds.AnyAccessor -> setAnyAcc(AnyAccessor.getDefaultInstance())
            org.seqra.dataflow.ap.ifds.ReferenceAccessor -> setRefAcc(ReferenceAccessor.getDefaultInstance())
        }
    }.build()

private fun serializeFactAp(fact: InitialFactAp): FactAp =
    FactAp.newBuilder()
        .setBase(serializeApBase(fact.base))
        .addAllAccessors(fact.getAllAccessors().map(::serializeApAccessor))
        .build()

private fun serializeProtoTraceKind(kind: MethodTraceResolver.TraceKind): ProtoFullTrace.TraceKind =
    when (kind) {
        MethodTraceResolver.TraceKind.TraceToFact -> ProtoFullTrace.TraceKind.TRACE_KIND_TRACE_TO_FACT
        MethodTraceResolver.TraceKind.TraceToFactAfterStatement ->
            ProtoFullTrace.TraceKind.TRACE_KIND_TRACE_TO_FACT_AFTER_STATEMENT
        MethodTraceResolver.TraceKind.SummaryTrace -> ProtoFullTrace.TraceKind.TRACE_KIND_SUMMARY
    }

private fun orderedFullTraceEntries(trace: MethodTraceResolver.FullTrace): List<MethodTraceResolver.TraceEntry> =
    buildList {
        add(trace.startEntry)
        add(trace.final)
        trace.successors.forEach { (from, tos) ->
            add(from)
            addAll(tos)
        }
    }.distinct()

private fun serializeSourceTraceEdge(edge: MethodTraceResolver.TraceEdge.SourceTraceEdge): SourceTraceEdge =
    SourceTraceEdge.newBuilder().setFact(serializeFactAp(edge.fact)).build()

private fun serializeMethodTraceEdge(edge: MethodTraceResolver.TraceEdge.MethodTraceEdge): MethodTraceEdge =
    MethodTraceEdge.newBuilder()
        .setInitialFact(serializeFactAp(edge.initialFact))
        .setFact(serializeFactAp(edge.fact))
        .build()

private fun serializeTraceEdge(edge: MethodTraceResolver.TraceEdge): ProtoMethodTraceEdge =
    ProtoMethodTraceEdge.newBuilder().apply {
        when (edge) {
            is MethodTraceResolver.TraceEdge.SourceTraceEdge -> setSourceTraceEdge(serializeSourceTraceEdge(edge))
            is MethodTraceResolver.TraceEdge.MethodTraceEdge -> setMethodTraceEdge(serializeMethodTraceEdge(edge))
            is MethodTraceResolver.TraceEdge.MethodTraceNDEdge -> notModeled()
        }
    }.build()

private fun serializeTraceEntryKind(entry: MethodTraceResolver.TraceEntry): ProtoTraceEntry.Kind =
    when (entry) {
        is MethodTraceResolver.TraceEntry.Action -> ProtoTraceEntry.Kind.KIND_ACTION
        is MethodTraceResolver.TraceEntry.Unchanged -> ProtoTraceEntry.Kind.KIND_UNCHANGED
        is MethodTraceResolver.TraceEntry.Final -> ProtoTraceEntry.Kind.KIND_FINAL
        is MethodTraceResolver.TraceEntry.MethodEntry -> ProtoTraceEntry.Kind.KIND_METHOD_ENTRY
        is MethodTraceResolver.TraceEntry.SourceStartEntry -> ProtoTraceEntry.Kind.KIND_SOURCE_START
    }

private fun serializeTraceEntry(entry: MethodTraceResolver.TraceEntry): ProtoTraceEntry =
    ProtoTraceEntry.newBuilder()
        .setKind(serializeTraceEntryKind(entry))
        .setStatement(serializeMlirOpId(entry.statement))
        .addAllEdges(entry.edges.map(::serializeTraceEdge))
        .build()

private fun serializeProtoFullTrace(trace: MethodTraceResolver.FullTrace): ProtoFullTrace {
    if (trace.traceKind == MethodTraceResolver.TraceKind.SummaryTrace) notModeled()
    val ordered = orderedFullTraceEntries(trace)
    val idMap = assignSequentialIds(ordered)
    val b = ProtoFullTrace.newBuilder()
    b.setMethod(serializeCirFunctionId(trace.method.method))
    b.setStartEntryId(idMap.getValue(trace.startEntry))
    b.setFinalEntryId(idMap.getValue(trace.final))
    b.setTraceKind(serializeProtoTraceKind(trace.traceKind))
    for ((entry, id) in idMap) {
        b.putIdToTraceEntry(id, serializeTraceEntry(entry))
    }
    for ((from, succs) in trace.successors) {
        val fromId = idMap.getValue(from)
        val idsBuilder = ProtoFullTrace.TraceEntryIds.newBuilder()
        for (s in succs) {
            idsBuilder.addIds(idMap.getValue(s))
        }
        b.putSuccessors(fromId, idsBuilder.build())
    }
    return b.build()
}

private const val SUMMARY_TRACE_FINAL_ENTRY_ID: Int = 0

private fun serializeProtoSummaryTrace(trace: MethodTraceResolver.SummaryTrace): ProtoSummaryTrace =
    ProtoSummaryTrace.newBuilder()
        .setMethod(serializeCirFunctionId(trace.method.method))
        .setFinalEntryId(SUMMARY_TRACE_FINAL_ENTRY_ID)
        .setTraceKind(serializeProtoTraceKind(trace.traceKind))
        .putIdToTraceEntry(SUMMARY_TRACE_FINAL_ENTRY_ID, serializeTraceEntry(trace.final))
        .build()

private fun serializeEntryPointTraceNode(node: TraceResolver.EntryPointTraceNode): EntryPointTraceNode =
    EntryPointTraceNode.newBuilder().setMethod(serializeCirFunctionId(node.method)).build()

private fun serializeCallTraceNode(node: TraceResolver.CallTraceNode): CallTraceNode =
    CallTraceNode.newBuilder()
        .setStatement(serializeMlirOpId(node.statement))
        .setMethod(serializeCirFunctionId(node.methodEntryPoint.method))
        .build()

private fun serializeSimpleTraceNode(node: TraceResolver.SimpleTraceNode): SimpleTraceNode =
    SimpleTraceNode.newBuilder()
        .setStatement(serializeMlirOpId(node.statement))
        .setMethod(serializeCirFunctionId(node.methodEntryPoint.method))
        .build()

private fun serializeFullTraceNode(node: TraceResolver.InterProceduralFullTraceNode): FullTraceNode =
    FullTraceNode.newBuilder()
        .setMethod(serializeCirFunctionId(node.methodEntryPoint.method))
        .setTrace(serializeProtoFullTrace(node.trace))
        .build()

private fun serializeSummaryTraceNode(node: TraceResolver.InterProceduralSummaryTraceNode): SummaryTraceNode =
    SummaryTraceNode.newBuilder()
        .setMethod(serializeCirFunctionId(node.methodEntryPoint.method))
        .setTrace(serializeProtoSummaryTrace(node.trace))
        .build()

private fun serializeSourceToSinkTraceNode(node: TraceResolver.SourceToSinkTraceNode): SourceToSinkTraceNode =
    SourceToSinkTraceNode.newBuilder().apply {
        when (node) {
            is TraceResolver.SimpleTraceNode -> setSimple(serializeSimpleTraceNode(node))
            is TraceResolver.InterProceduralFullTraceNode -> setFull(serializeFullTraceNode(node))
            is TraceResolver.InterProceduralSummaryTraceNode -> setSummary(serializeSummaryTraceNode(node))
        }
    }.build()

private fun serializeTraceNodeGraph(node: TraceResolver.TraceNode): TraceNode =
    TraceNode.newBuilder().apply {
        when (node) {
            is TraceResolver.EntryPointTraceNode -> setEntry(serializeEntryPointTraceNode(node))
            is TraceResolver.CallTraceNode -> setCall(serializeCallTraceNode(node))
            is TraceResolver.SimpleTraceNode -> setSimple(serializeSimpleTraceNode(node))
            is TraceResolver.InterProceduralFullTraceNode -> setFull(serializeFullTraceNode(node))
            is TraceResolver.InterProceduralSummaryTraceNode -> setSummary(serializeSummaryTraceNode(node))
        }
    }.build()

private fun serializeEntryPointToStartTrace(t: TraceResolver.EntryPointToStartTrace): EntryPointToStartTrace {
    val singleEp = t.entryPoints.singleOrNull() ?: notModeled()
    val nodes = LinkedHashSet<TraceResolver.TraceNode>()
    nodes.addAll(t.entryPoints)
    t.successors.forEach { (from, tos) ->
        nodes.add(from)
        nodes.addAll(tos)
    }
    val idMap = assignSequentialIds(nodes)
    val b = EntryPointToStartTrace.newBuilder()
    b.setEntryPoint(serializeEntryPointTraceNode(singleEp))
    for ((node, id) in idMap) {
        b.putIdToTraceNode(id, serializeTraceNodeGraph(node))
    }
    for ((from, succs) in t.successors) {
        val fromId = idMap.getValue(from)
        val idsBuilder = EntryPointToStartTrace.TraceNodeIds.newBuilder()
        for (s in succs) {
            idsBuilder.addIds(idMap.getValue(s))
        }
        b.putSuccessors(fromId, idsBuilder.build())
    }
    return b.build()
}

private fun serializeCallKind(kind: TraceResolver.CallKind): InterProceduralCall.CallKind =
    when (kind) {
        TraceResolver.CallKind.CallToSource -> InterProceduralCall.CallKind.CALL_KIND_CALL_TO_SOURCE
        TraceResolver.CallKind.CallToSink -> InterProceduralCall.CallKind.CALL_KIND_CALL_TO_SINK
    }

private fun serializeInterProceduralCall(call: TraceResolver.InterProceduralCall): InterProceduralCall {
    val b =
        InterProceduralCall.newBuilder()
            .setKind(serializeCallKind(call.kind))
            .setStatement(serializeMlirOpId(call.statement))
            .setCallSummary(serializeProtoSummaryTrace(call.summary))
    when (val node = call.node) {
        is TraceResolver.InterProceduralFullTraceNode -> b.setNode(serializeFullTraceNode(node))
        is TraceResolver.InterProceduralSummaryTraceNode -> b.setSummaryNode(serializeSummaryTraceNode(node))
    }
    return b.build()
}

private fun collectSourceToSinkInterProceduralNodes(t: TraceResolver.SourceToSinkTrace): List<TraceResolver.InterProceduralTraceNode> {
    val out = LinkedHashSet<TraceResolver.InterProceduralTraceNode>()
    fun addNode(n: TraceResolver.SourceToSinkTraceNode) {
        when (n) {
            is TraceResolver.InterProceduralFullTraceNode -> out.add(n)
            is TraceResolver.InterProceduralSummaryTraceNode -> out.add(n)
            is TraceResolver.SimpleTraceNode -> Unit
        }
    }
    t.startNodes.forEach(::addNode)
    t.sinkNodes.forEach(::addNode)
    for ((pred, calls) in t.successors) {
        addNode(pred)
        for (c in calls) {
            addNode(c.node)
        }
    }
    return out.toList()
}

private fun serializeSourceToSinkTraceGraph(t: TraceResolver.SourceToSinkTrace): SourceToSinkTrace {
    val b = SourceToSinkTrace.newBuilder()
    for (n in t.startNodes) {
        b.addStartNodes(serializeSourceToSinkTraceNode(n))
    }
    for (n in t.sinkNodes) {
        b.addSinkNodes(serializeSourceToSinkTraceNode(n))
    }
    val idMap = assignSequentialIds(collectSourceToSinkInterProceduralNodes(t))
    for ((pred, id) in idMap) {
        val calls = t.successors[pred].orEmpty()
        val setBuilder = SourceToSinkTrace.InterProceduralCallSet.newBuilder()
        for (c in calls) {
            setBuilder.addCalls(serializeInterProceduralCall(c))
        }
        b.putSuccessors(id, setBuilder.build())
    }
    return b.build()
}

private fun serializeProtoTrace(t: TraceResolver.Trace): ProtoTrace {
    val epToStart = t.entryPointToStart ?: notModeled()
    val nameEp = run {
        val s = epToStart.entryPoints.singleOrNull() ?: notModeled()
        // Align with `serializeCirFunctionId` / LLVM symbol: `name` is CIR `symName`
        // and can differ from the persisted function id string used in trace MLIROpIDs.
        val m = s.method
        (m as? CIRFunction)?.id?.id ?: m.name
    }
    return ProtoTrace.newBuilder()
        .setEntryPointName(nameEp)
        .setEntryPointToStart(serializeEntryPointToStartTrace(epToStart))
        .setSourceToSinkTrace(serializeSourceToSinkTraceGraph(t.sourceToSinkTrace))
        .build()
}

fun VulnerabilityWithTrace.serialize(): ByteArray {
    val trace = trace ?: emptyTrace()
    return serializeProtoTrace(trace).toByteArray()
}
