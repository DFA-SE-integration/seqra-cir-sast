package org.seqra.cir.sast.se

import mu.KLogging
import org.seqra.cir.sast.se.api.CirSeAnalyzer
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Baseline analyzer: accepts every IFDS-produced trace without symbolic verification.
 * Selected by `SEQRA_SE_MODE=none` to measure IFDS-only F1 on the Juliet suite.
 *
 * Appends a row to `CIR_KLEE_RESULTS_TSV` so baseline and IFDS+KLEE runs share one
 * data file (mode=none rows carry zero KLEE-side columns).
 */
object NoOpCirSeAnalyzer : CirSeAnalyzer {

    private val logger = object : KLogging() {}.logger

    private val tsvLock = Any()
    private val resultsTsv: Path? by lazy {
        System.getenv("CIR_KLEE_RESULTS_TSV")?.trim()?.takeIf { it.isNotEmpty() }?.let { Paths.get(it) }
    }
    private val TSV_HEADER = listOf(
        "timestamp_ms",
        "fixture",
        "entry",
        "mode",
        "no_trace_guide",
        "confirmed",
        "klee_instructions",
        "klee_completed_paths",
        "klee_explored_paths",
        "klee_ms",
        "trace_guide_ms",
        "trace_assert_ms",
        "cir_to_llvm_ms",
        "llvm_as_ms",
    ).joinToString("\t")

    override fun verifyTrace(trace: VulnerabilityWithTrace, cirFiles: List<Path>): Boolean {
        appendResultsTsv(trace, cirFiles)
        return true
    }

    private fun appendResultsTsv(trace: VulnerabilityWithTrace, cirFiles: List<Path>) {
        val tsv = resultsTsv ?: return
        val entry = trace.trace?.entryPointToStart?.entryPoints?.singleOrNull()?.method?.name ?: "?"
        val fixture = cirFiles.firstOrNull()?.fileName?.toString() ?: "?"
        val cols = listOf(
            System.currentTimeMillis().toString(),
            fixture,
            entry,
            "none",
            "false",
            "true",
            "0", "0", "0",
            "0.0", "0.0", "0.0", "0.0", "0.0",
        )
        synchronized(tsvLock) {
            try {
                val needsHeader = !Files.exists(tsv) || Files.size(tsv) == 0L
                Files.createDirectories(tsv.parent ?: Paths.get("."))
                Files.newBufferedWriter(
                    tsv,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                ).use { w ->
                    if (needsHeader) {
                        w.write(TSV_HEADER); w.newLine()
                    }
                    w.write(cols.joinToString("\t")); w.newLine()
                }
            } catch (e: Exception) {
                logger.warn(e) { "failed to append to CIR_KLEE_RESULTS_TSV=$tsv" }
            }
        }
    }
}
