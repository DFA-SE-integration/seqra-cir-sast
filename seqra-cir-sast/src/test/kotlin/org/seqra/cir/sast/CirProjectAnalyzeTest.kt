package org.seqra.cir.sast

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertNotEquals

/**
 * End-to-end pipeline on one .cir file:
 *   loadSingleCirFile -> CIRTaintAnalyzer.analyzeWithIfds -> KleeAnalyzer.analyzeTrace
 *
 * Mirrors `seqra-jvm-sast` ProjectAnalyzerTester.kt's role for the CIR side.
 */
class CirProjectAnalyzeTest {

    @Test
    fun `dataflow + se on malloc_free_char_15 trace`() {
        Assumptions.assumeTrue(!System.getenv("CIRTAC_COMPILER").isNullOrBlank(), "CIRTAC_COMPILER is required")

        val entryName = "CWE416_Use_After_Free__malloc_free_char_15_bad"
        val cirFixture = locateBundledJulietCirFixture()
        Assumptions.assumeTrue(
            Files.isRegularFile(cirFixture),
            "Bundled Juliet CIR fixture missing: $cirFixture",
        )

        val traces = CIRProjectAnalyzer.analyze(cirFixture, entryName)
        assertNotEquals(traces.size, 0)
    }

    /** Checked-in CIR for `malloc_free_char_15` (seqra-ir test resources). */
    private val bundledJulietMallocFreeChar15Cir: Path = Path.of(
        "juliet-c",
        "samples",
        "CWE416_Use_After_Free",
        "CWE416_Use_After_Free__malloc_free_char_15.cir",
    )

    private fun locateBundledJulietCirFixture(): Path = repoRoot().resolve(bundledJulietMallocFreeChar15Cir)

    private fun repoRoot(): Path = Path.of("").toAbsolutePath().normalize().resolve("..").normalize()
}