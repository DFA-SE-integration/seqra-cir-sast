package org.seqra.cir.sast.se.api

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
}
