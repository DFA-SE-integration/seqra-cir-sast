package org.seqra.cir.sast.se.klee

import mu.KLogging
import org.seqra.cir.sast.se.api.CirSeAnalyzer
import org.seqra.cir.sast.se.serialize
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import java.nio.file.Files
import java.nio.file.Path

object KleeCirSeAnalyzer : CirSeAnalyzer {

    private val logger = object : KLogging() {}.logger

    override fun verifyTrace(trace: VulnerabilityWithTrace, cirFile: Path): Boolean {
        val cirKlee = System.getenv("CIRTAC_KLEE")?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("CIRTAC_KLEE must be set to the cir-klee executable path")

        val pbFile = Files.createTempFile("cir-klee-trace-", ".pb")
        try {
            Files.write(pbFile, trace.serialize())

            val proc = ProcessBuilder(
                cirKlee,
                cirFile.toAbsolutePath().toString(),
                pbFile.toAbsolutePath().toString(),
            )
                .redirectErrorStream(true)
                .start()

            val out = proc.inputStream.bufferedReader().readText()
            val rc = proc.waitFor()
            logger.info { "cir-klee exit=$rc\n$out" }
            return rc == 0
        } finally {
            Files.deleteIfExists(pbFile)
        }
    }
}
