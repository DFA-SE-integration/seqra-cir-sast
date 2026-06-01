package org.seqra.cir.sast

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CWE416UseAfterFreeBadEntrypointsTest {
    @ParameterizedTest(name = "{2}")
    @MethodSource("cwe416BadCases")
    fun useAfterFreeFixtureBad(fileName: String, companionFileNames: List<String>, entrypoint: String) {
        Assumptions.assumeTrue(!System.getenv("CIRTAC_COMPILER").isNullOrBlank(), "CIRTAC_COMPILER is required")

        Assumptions.assumeTrue(
            fileName !in CWE416JulietFixtures.BLOCKED_BY_CLANGIR_AGGREGATE_INIT,
            "Blocked by ClangIR codegen bug on `new TwoIntsClass[100]` / " +
                    "`new twoIntsStruct[100]` in companion `_62b.cpp` — companion `.cir` cannot be generated.",
        )
        Assumptions.assumeTrue(
            fileName !in CWE416JulietFixtures.BLOCKED_BY_CLANGIR_NEW_ARRAY_SIZE,
            "Blocked by ClangIR codegen bug on `new T[100]` allocation size lowering.",
        )

        val root = CWE416JulietFixtures.repoRoot()
        val fixture = root.resolve(CWE416JulietFixtures.FIXTURES_DIR.resolve(fileName))
        assertTrue(fixture.exists(), "Missing CIR fixture: $fixture")

        //TODO companions
        val traces = CIRProjectAnalyzer.analyze(fixture, entrypoint)
        assertNotEquals(traces.size, 0)
        val t = traces.first().trace
        assertNotNull(t)
        assertNotNull(t.entryPointToStart)
        assertTrue(t.entryPointToStart!!.entryPoints.isNotEmpty())
        assertTrue(t.sourceToSinkTrace.startNodes.isNotEmpty())
        assertTrue(t.sourceToSinkTrace.sinkNodes.isNotEmpty())
    }

    companion object {
        @JvmStatic
        fun cwe416BadCases(): Stream<Arguments> =
            CWE416JulietFixtures.fixtureArgumentStream(
                findEntrypoint = CWE416JulietFixtures::findBadEntrypoint,
                missingEntrypointDescription = "_bad entrypoint",
                hasComplementEntrypoint = CWE416JulietFixtures::findGoodEntrypoint,
            )
    }
}
