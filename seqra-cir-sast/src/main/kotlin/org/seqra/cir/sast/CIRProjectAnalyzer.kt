package org.seqra.cir.sast

import org.seqra.cir.sast.dataflow.CIRTaintAnalyzer
import org.seqra.cir.sast.se.klee.KleeCirSeAnalyzer
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import java.nio.file.Path
import kotlin.use

object CIRProjectAnalyzer {
    fun analyze(cirFixture: Path, entrypoint: String): List<VulnerabilityWithTrace> {
        CIRTaintAnalyzer.loadSingleCirFile(cirFixture).use { loaded ->
            val entryFn = loaded.analyzer.cp.findFunctionBySymbolName(entrypoint)
                ?: throw RuntimeException("Missing entrypoint $entrypoint in $cirFixture")

            return loaded.analyzer.analyzeWithIfds(listOf(entryFn)).filter { trace ->
                KleeCirSeAnalyzer.verifyTrace(trace, cirFixture)
            }.toList()
        }
    }
}