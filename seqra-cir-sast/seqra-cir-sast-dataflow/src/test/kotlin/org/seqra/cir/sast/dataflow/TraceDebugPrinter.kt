package org.seqra.cir.sast.dataflow

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.AnyAccessor
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.FieldAccessor
import org.seqra.dataflow.ap.ifds.FinalAccessor
import org.seqra.dataflow.ap.ifds.ReferenceAccessor
import org.seqra.dataflow.ap.ifds.TaintMarkAccessor
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.trace.MethodTraceResolver
import org.seqra.dataflow.ap.ifds.trace.TraceResolver
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.seqra.ir.api.cir.cfg.CIRInst

/**
 * Pretty-printer for `TraceResolver.Trace` that surfaces every fact-carrying edge
 * inside a vulnerability trace. Enabled by `SEQRA_TRACE_DEBUG=1` (any non-blank value).
 *
 * Output goes to stdout; tests already enable `testLogging.showStandardStreams`, so
 * it lands in the Gradle test report. Fact notation matches slide 2b:
 *   `var(42).&.![use-after-free].$`
 *   `arg(0).field{Foo.bar:i32}[*]`
 */
object TraceDebugPrinter {
    private fun enabled(): Boolean =
        System.getenv("SEQRA_TRACE_DEBUG")?.takeIf { it.isNotBlank() } != null

    fun maybePrint(label: String, vulnerabilities: List<VulnerabilityWithTrace>) {
        if (!enabled()) return
        println("==== TRACE-DEBUG: $label (${vulnerabilities.size} vulnerabilities) ====")
        for ((i, v) in vulnerabilities.withIndex()) {
            println("---- vuln[$i] ----")
            val trace = v.trace
            if (trace == null) {
                println("  (no trace)")
                continue
            }
            printTrace(trace)
        }
        println("==== /TRACE-DEBUG ====")
    }

    private fun printTrace(t: TraceResolver.Trace) {
        t.entryPointToStart?.let { ep ->
            println("  entryPointToStart:")
            for (n in ep.entryPoints) {
                println("    EP ${nodeStr(n)}")
            }
            for ((from, tos) in ep.successors) {
                for (to in tos) {
                    println("    ${nodeStr(from)} -> ${nodeStr(to)}")
                }
            }
        }
        val sts = t.sourceToSinkTrace
        println("  sourceToSinkTrace:")
        println("    startNodes:")
        sts.startNodes.forEachIndexed { i, n -> printSrcSinkNode("start[$i]", n) }
        println("    sinkNodes:")
        sts.sinkNodes.forEachIndexed { i, n -> printSrcSinkNode("sink[$i]", n) }
        if (sts.successors.isNotEmpty()) {
            println("    inter-procedural calls:")
            for ((from, calls) in sts.successors) {
                for (c in calls) {
                    println("      ${nodeStr(from)} --${c.kind}-> stmt=${stmtStr(c.statement)}")
                }
            }
        }
    }

    private fun printSrcSinkNode(label: String, n: TraceResolver.SourceToSinkTraceNode) {
        when (n) {
            is TraceResolver.SimpleTraceNode ->
                println("      $label SIMPLE stmt=${stmtStr(n.statement)} method=${n.methodEntryPoint.method.name}")
            is TraceResolver.InterProceduralFullTraceNode -> {
                println("      $label FULL method=${n.methodEntryPoint.method.name}")
                printFullTrace("        ", n.trace)
            }
            is TraceResolver.InterProceduralSummaryTraceNode -> {
                println("      $label SUMMARY method=${n.methodEntryPoint.method.name}")
                printSummaryTrace("        ", n.trace)
            }
        }
    }

    private fun printFullTrace(indent: String, ft: MethodTraceResolver.FullTrace) {
        println("${indent}kind=${ft.traceKind} method=${ft.method.method.name}")
        val ordered = orderForPrint(ft)
        val idMap = ordered.withIndex().associate { (i, e) -> e to i }
        val startIdx = idMap[ft.startEntry] ?: -1
        val finalIdx = idMap[ft.final] ?: -1
        println("${indent}start=#$startIdx final=#$finalIdx (${ordered.size} entries)")
        for ((idx, e) in ordered.withIndex()) {
            println("${indent}#$idx ${entryKindStr(e)} stmt=${stmtStr(e.statement)}")
            val facts = factSet(e)
            if (facts.isNotEmpty()) {
                println("${indent}    facts = {${facts.joinToString(", ")}}")
            }
            for (edge in e.edges) {
                println("${indent}    ${edgeStr(edge)}")
            }
            val successors = ft.successors[e].orEmpty()
            if (successors.isNotEmpty()) {
                val ids = successors.mapNotNull { idMap[it] }.sorted()
                println("${indent}    -> ${ids.joinToString(",") { "#$it" }}")
            }
        }
    }

    private fun printSummaryTrace(indent: String, st: MethodTraceResolver.SummaryTrace) {
        println("${indent}kind=${st.traceKind} method=${st.method.method.name}")
        val e = st.final
        println("${indent}final: ${entryKindStr(e)} stmt=${stmtStr(e.statement)}")
        val facts = factSet(e)
        if (facts.isNotEmpty()) {
            println("${indent}    facts = {${facts.joinToString(", ")}}")
        }
        for (edge in e.edges) {
            println("${indent}    ${edgeStr(edge)}")
        }
    }

    private fun orderForPrint(ft: MethodTraceResolver.FullTrace): List<MethodTraceResolver.TraceEntry> {
        val seen = LinkedHashSet<MethodTraceResolver.TraceEntry>()
        fun dfs(e: MethodTraceResolver.TraceEntry) {
            if (!seen.add(e)) return
            ft.successors[e].orEmpty().forEach(::dfs)
        }
        dfs(ft.startEntry)
        // Append any orphans (final / disconnected) deterministically.
        seen.add(ft.final)
        for (k in ft.successors.keys) seen.add(k)
        for (vs in ft.successors.values) for (v in vs) seen.add(v)
        return seen.toList()
    }

    private fun entryKindStr(e: MethodTraceResolver.TraceEntry): String = when (e) {
        is MethodTraceResolver.TraceEntry.Action -> "ACTION"
        is MethodTraceResolver.TraceEntry.Unchanged -> "UNCHANGED"
        is MethodTraceResolver.TraceEntry.Final -> "FINAL"
        is MethodTraceResolver.TraceEntry.MethodEntry -> "METHOD_ENTRY"
        is MethodTraceResolver.TraceEntry.SourceStartEntry -> "SOURCE_START"
    }

    // Slide-style fact set at this entry: union of `edge.fact` over all edges,
    // matching the `{var(data).![use-after-free], var(%v).![use-after-free]}`
    // notation on slide 7. Sorted for stable diffs.
    private fun factSet(e: MethodTraceResolver.TraceEntry): List<String> {
        val out = sortedSetOf<String>()
        for (edge in e.edges) {
            val f = when (edge) {
                is MethodTraceResolver.TraceEdge.SourceTraceEdge -> edge.fact
                is MethodTraceResolver.TraceEdge.MethodTraceEdge -> edge.fact
                is MethodTraceResolver.TraceEdge.MethodTraceNDEdge -> edge.fact
            }
            out.add(factStr(f))
        }
        return out.toList()
    }

    private fun edgeStr(edge: MethodTraceResolver.TraceEdge): String = when (edge) {
        is MethodTraceResolver.TraceEdge.SourceTraceEdge ->
            "SOURCE  fact=${factStr(edge.fact)}"
        is MethodTraceResolver.TraceEdge.MethodTraceEdge ->
            "METHOD  initial=${factStr(edge.initialFact)}  fact=${factStr(edge.fact)}"
        is MethodTraceResolver.TraceEdge.MethodTraceNDEdge -> {
            val inits = edge.initialFacts.joinToString(prefix = "{", postfix = "}") { factStr(it) }
            "METHOD_ND initials=$inits  fact=${factStr(edge.fact)}"
        }
    }

    private fun factStr(f: InitialFactAp): String {
        val sb = StringBuilder()
        sb.append(baseStr(f.base))
        for (acc in f.getAllAccessors()) sb.append(accessorStr(acc))
        return sb.toString()
    }

    private fun baseStr(base: AccessPathBase): String = when (base) {
        AccessPathBase.This -> "this"
        is AccessPathBase.LocalVar -> "var(${base.idx})"
        is AccessPathBase.Argument -> "arg(${base.idx})"
        AccessPathBase.Return -> "ret"
        AccessPathBase.Exception -> "exc"
        else -> "<base?$base>"
    }

    private fun accessorStr(acc: Accessor): String = when (acc) {
        is TaintMarkAccessor -> ".![${acc.mark}]"
        is FieldAccessor -> ".{${acc.className}.${acc.fieldName}:${acc.fieldType}}"
        ElementAccessor -> "[*]"
        FinalAccessor -> ".$"
        AnyAccessor -> ".?"
        ReferenceAccessor -> ".&"
    }

    private fun nodeStr(n: TraceResolver.TraceNode): String = when (n) {
        is TraceResolver.EntryPointTraceNode -> "EP{${n.method.name}}"
        is TraceResolver.CallTraceNode -> "CALL{${n.methodEntryPoint.method.name}@${stmtStr(n.statement)}}"
        is TraceResolver.SimpleTraceNode -> "SIMPLE{${n.methodEntryPoint.method.name}@${stmtStr(n.statement)}}"
        is TraceResolver.InterProceduralFullTraceNode -> "FULL{${n.methodEntryPoint.method.name}}"
        is TraceResolver.InterProceduralSummaryTraceNode -> "SUMMARY{${n.methodEntryPoint.method.name}}"
    }

    private fun stmtStr(inst: org.seqra.ir.api.common.cfg.CommonInst): String {
        val cir = inst as? CIRInst ?: return inst.toString()
        return "op#${cir.id.id}"
    }
}
