package org.seqra.cir.sast

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.test.assertTrue

/**
 * Runs the IFDS + symbolic-execution pipeline (identical to the Juliet CWE416 entrypoint tests)
 * over large real-world SARD/Wireshark `.cir` listed in the `SEQRA_BIGPROJ_PLAN` plan file
 * (see [BigProjectsFixtures]). Each row is one (entrypoint, set-of-.cir) case.
 *
 * Findings are appended to `CIR_KLEE_RESULTS_TSV` exactly like `stats.sh`, so the same SE-mode
 * matrix (none / nopass / guide / assert / full) can be compared across configurations.
 *
 * The assertion mirrors the SARD ground truth carried in the plan: a `bad` (buggy) case must
 * surface at least one confirmed UAF trace (true positive); a `good` (fixed) case must surface
 * none (no false positive). Cases are driven entirely by the plan file, so when the runner has
 * not been configured the single sentinel row is skipped rather than failing the suite.
 */
class BigProjectsEntrypointsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun bigProject(label: String, expectFinding: Boolean, entry: String, cirPaths: List<Path>) {
        Assumptions.assumeTrue(label != BigProjectsFixtures.NO_PLAN_LABEL, "SEQRA_BIGPROJ_PLAN is not set")
        Assumptions.assumeTrue(!System.getenv("CIRTAC_COMPILER").isNullOrBlank(), "CIRTAC_COMPILER is required")
        Assumptions.assumeTrue(
            cirPaths.isNotEmpty() && cirPaths.all { it.exists() },
            "Missing .cir for $label: $cirPaths",
        )

        val traces = CIRProjectAnalyzer.analyze(cirPaths, entry)

        if (expectFinding) {
            assertTrue(traces.isNotEmpty(), "Expected a use-after-free in $label ($entry) but found none")
        } else {
            assertTrue(traces.isEmpty(), "False positive in $label ($entry): ${traces.size} confirmed trace(s)")
        }
    }

    companion object {
        @JvmStatic
        fun cases(): Stream<Arguments> = BigProjectsFixtures.argumentStream()
    }
}
