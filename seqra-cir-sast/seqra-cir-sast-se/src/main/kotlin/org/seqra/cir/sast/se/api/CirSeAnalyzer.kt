package org.seqra.cir.sast.se.api

import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import java.nio.file.Path

interface CirSeAnalyzer {
    fun verifyTrace(
        trace: VulnerabilityWithTrace,
        cirFile: Path,
    ): Boolean
}
