package org.seqra.cir.sast

import org.seqra.cir.sast.dataflow.CIRTaintAnalyzer
import org.seqra.cir.sast.se.api.CirSeAnalyzer
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import java.nio.file.Files
import java.nio.file.Path
import kotlin.use

object CIRProjectAnalyzer {
    private val seAnalyzer: CirSeAnalyzer by lazy { CirSeAnalyzer.fromEnv() }

    private val julietInterfileSplitEntryPattern = Regex("""^(.*)_(62|63|64)a\.cir$""")

    /** Juliet `_Na.cir` + `_Nb.cir` split: load companion so IFDS sees malloc/free bodies. */
    private fun julietInterfileCompanionCir(main: Path): List<Path> {
        val name = main.fileName.toString()
        val m = julietInterfileSplitEntryPattern.matchEntire(name) ?: return emptyList()
        val companion = main.resolveSibling("${m.groupValues[1]}_${m.groupValues[2]}b.cir")
        return if (Files.exists(companion)) listOf(companion) else emptyList()
    }

    fun analyze(cirFixture: Path, entrypoint: String): List<VulnerabilityWithTrace> {
        val cirPaths = buildList {
            add(cirFixture)
            addAll(julietInterfileCompanionCir(cirFixture))
        }
        CIRTaintAnalyzer.loadCirFiles(cirPaths).use { loaded ->
            val entryFn = loaded.analyzer.cp.findFunctionBySymbolName(entrypoint)
                ?: throw RuntimeException("Missing entrypoint $entrypoint in $cirFixture")

            // Drop sink hits whose interprocedural trace graph is degenerate (empty start/sink
            // nodes). Otherwise collection order can surface e.g. `libc.memset` before `printLine`
            // on Juliet `_17_bad` loops — the sink fires forward, but backward IFDS edge matching
            // yields no summary trace, while the real UAF at `printLine` resolves fine.
            return loaded.analyzer.analyzeWithIfds(listOf(entryFn)).filter { vwt ->
                val t = vwt.trace ?: return@filter false
                val hasTraceNodes = t.sourceToSinkTrace.startNodes.isNotEmpty() &&
                    t.sourceToSinkTrace.sinkNodes.isNotEmpty()
                hasTraceNodes && seAnalyzer.verifyTrace(vwt, cirPaths)
            }.toList()
        }
    }
}