package org.seqra.cir.sast.se.klee

import mu.KLogging
import org.seqra.cir.sast.se.api.CirSeAnalyzer
import org.seqra.cir.sast.se.serialize
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import java.nio.file.Files
import java.nio.file.Path

object KleeCirSeAnalyzer : CirSeAnalyzer {

    private val logger = object : KLogging() {}.logger

    override fun verifyTrace(trace: VulnerabilityWithTrace, cirFiles: List<Path>): Boolean {
        require(cirFiles.isNotEmpty()) { "verifyTrace requires at least one .cir file" }
        val cirKlee = System.getenv("CIRTAC_KLEE")?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("CIRTAC_KLEE must be set to the cir-klee executable path")

        val pbFile = Files.createTempFile("cir-klee-trace-", ".pb")
        try {
            Files.write(pbFile, trace.serialize())

            val proc = ProcessBuilder(
                buildList {
                    add(cirKlee)
                    cirFiles.forEach { add(it.toAbsolutePath().toString()) }
                    add(pbFile.toAbsolutePath().toString())
                },
            )
                .redirectErrorStream(true)
                .start()

            val out = proc.inputStream.bufferedReader().readText()
            // Sink reached flag
            val rc = out.contains("abort failure")
            logger.info { "cir-klee exit=$rc\n$out" }
            return rc
        } finally {
            Files.deleteIfExists(pbFile)
        }
    }
}
