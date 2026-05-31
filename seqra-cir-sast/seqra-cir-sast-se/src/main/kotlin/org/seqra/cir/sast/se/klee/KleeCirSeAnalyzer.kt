package org.seqra.cir.sast.se.klee

import mu.KLogging
import org.seqra.cir.sast.dataflow.proto.trace.KleeResult
import org.seqra.cir.sast.se.api.CirSeAnalyzer
import org.seqra.cir.sast.se.serialize
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

object KleeCirSeAnalyzer : CirSeAnalyzer {

    private val logger = object : KLogging() {}.logger

    private const val DEFAULT_TIMEOUT_SEC = 120L

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
        require(cirFiles.isNotEmpty()) { "verifyTrace requires at least one .cir file" }
        val cirKlee = System.getenv("CIRTAC_KLEE")?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("CIRTAC_KLEE must be set to the cir-klee executable path")

        val timeoutSec = System.getenv("CIRTAC_KLEE_TIMEOUT_SEC")?.trim()?.toLongOrNull()
            ?: DEFAULT_TIMEOUT_SEC
        val noTraceGuide = System.getenv("CIRTAC_KLEE_NO_TRACE_GUIDE")
            ?.let { it.equals("1") || it.equals("true", ignoreCase = true) }
            ?: false
        // The mandatory sink pass (klee_abort) always runs; only the optional
        // klee_assume alias constraints can be turned off here.
        val noTraceAssert = System.getenv("CIRTAC_KLEE_NO_TRACE_ASSERT")
            ?.let { it.equals("1") || it.equals("true", ignoreCase = true) }
            ?: false

        val pbFile = Files.createTempFile("cir-klee-trace-", ".pb")
        val resultFile = Files.createTempFile("cir-klee-result-", ".pb")
        val logFile = Files.createTempFile("cir-klee-log-", ".txt")
        try {
            Files.write(pbFile, trace.serialize())

            val command = buildList {
                add(cirKlee)
                if (noTraceGuide) add("--no-trace-guide")
                if (noTraceAssert) add("--no-trace-assert")
                add("--result=${resultFile.toAbsolutePath()}")
                cirFiles.forEach { add(it.toAbsolutePath().toString()) }
                add(pbFile.toAbsolutePath().toString())
            }

            // Redirect to file (not pipe): avoids the classic ProcessBuilder pipe-buffer
            // deadlock when KLEE produces more output than the OS buffer can hold.
            val proc = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start()

            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                proc.waitFor(5, TimeUnit.SECONDS)
                logger.warn { "cir-klee timed out after ${timeoutSec}s (entry=${trace.trace?.entryPointToStart?.entryPoints?.singleOrNull()?.method?.name}); log=$logFile" }
                return false
            }

            val result = readResult(resultFile)
            val confirmed = result?.let {
                logger.info {
                    "cir-klee exit=${proc.exitValue()} confirmed=${it.traceConfirmed} " +
                        "mem_errs=${it.kleeMemErrFilesCount} precond_violations=${it.kleeAbortFilesCount} " +
                        "klee_ms=${it.kleeMs} instr=${it.kleeInstructions} " +
                        "guide_ms=${it.traceGuideMs} assert_ms=${it.traceAssertMs}"
                }
                it.traceConfirmed
            } ?: run {
                val tail = runCatching { Files.readString(logFile).takeLast(2048) }.getOrDefault("")
                logger.warn { "cir-klee produced no result file (exit=${proc.exitValue()}); tail of log:\n$tail" }
                false
            }
            appendResultsTsv(trace, cirFiles, noTraceGuide, confirmed, result)
            return confirmed
        } finally {
            Files.deleteIfExists(pbFile)
            if (System.getenv("CIR_KLEE_KEEP_OUTPUT").isNullOrBlank()) {
                Files.deleteIfExists(resultFile)
                Files.deleteIfExists(logFile)
            } else {
                logger.info { "cir-klee artifacts kept: result=$resultFile log=$logFile" }
            }
        }
    }

    private fun readResult(path: Path): KleeResult? {
        if (!Files.exists(path) || Files.size(path) == 0L) return null
        return runCatching {
            Files.newInputStream(path).use { KleeResult.parseFrom(it) }
        }.getOrNull()
    }

    private fun appendResultsTsv(
        trace: VulnerabilityWithTrace,
        cirFiles: List<Path>,
        noTraceGuide: Boolean,
        confirmed: Boolean,
        result: KleeResult?,
    ) {
        val tsv = resultsTsv ?: return
        val entry = trace.trace?.entryPointToStart?.entryPoints?.singleOrNull()?.method?.name ?: "?"
        val fixture = cirFiles.firstOrNull()?.fileName?.toString() ?: "?"
        val cols = listOf(
            System.currentTimeMillis().toString(),
            fixture,
            entry,
            "klee",
            noTraceGuide.toString(),
            confirmed.toString(),
            (result?.kleeInstructions ?: 0L).toString(),
            (result?.kleeCompletedPaths ?: 0L).toString(),
            (result?.kleeExploredPaths ?: 0L).toString(),
            (result?.kleeMs ?: 0.0).toString(),
            (result?.traceGuideMs ?: 0.0).toString(),
            (result?.traceAssertMs ?: 0.0).toString(),
            (result?.cirToLlvmMs ?: 0.0).toString(),
            (result?.llvmAsMs ?: 0.0).toString(),
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
