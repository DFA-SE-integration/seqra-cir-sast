package org.seqra.cir.sast.se.api

import org.seqra.cir.sast.se.NoOpCirSeAnalyzer
import org.seqra.cir.sast.se.klee.KleeCirSeAnalyzer
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import java.nio.file.Path

interface CirSeAnalyzer {
    /**
     * @param cirFiles CIR modules in classpath order (e.g. Juliet `_62a.cir` then `_62b.cir`).
     * `cir-klee` merges later files into the first so linkable bodies match IFDS / protocir.
     */
    fun verifyTrace(
        trace: VulnerabilityWithTrace,
        cirFiles: List<Path>,
    ): Boolean

    companion object {
        // SEQRA_SE_MODE=klee (default) — full IFDS + cir-klee verification.
        // SEQRA_SE_MODE=none        — baseline, accept every IFDS trace as-is.
        fun fromEnv(): CirSeAnalyzer =
            when (val mode = System.getenv("SEQRA_SE_MODE")?.trim()?.lowercase()) {
                null, "", "klee" -> KleeCirSeAnalyzer
                "none" -> NoOpCirSeAnalyzer
                else -> error("Unknown SEQRA_SE_MODE='$mode' (expected: klee, none)")
            }
    }
}
