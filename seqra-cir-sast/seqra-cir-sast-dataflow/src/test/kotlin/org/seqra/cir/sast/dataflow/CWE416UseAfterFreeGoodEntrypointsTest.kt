package org.seqra.cir.sast.dataflow

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CWE416UseAfterFreeGoodEntrypointsTest {
    @ParameterizedTest(name = "{2}")
    @MethodSource("cwe416GoodCases")
    fun useAfterFreeFixtureGood(fileName: String, companionFileNames: List<String>, entrypoint: String) {
        assumeTrue(!System.getenv("CIRTAC_COMPILER").isNullOrBlank(), "CIRTAC_COMPILER is required")

        assumeTrue(
            fileName !in CWE416JulietFixtures.BLOCKED_BY_CLANGIR_AGGREGATE_INIT,
            "Blocked by ClangIR codegen bug on `new TwoIntsClass[100]` / " +
                "`new twoIntsStruct[100]` in companion `_62b.cpp` — companion `.cir` cannot be generated.",
        )

        val root = CWE416JulietFixtures.repoRoot()
        val fixture = root.resolve(CWE416JulietFixtures.FIXTURES_DIR.resolve(fileName))
        assertTrue(fixture.exists(), "Missing CIR fixture: $fixture")
        val companions = companionFileNames.map { name ->
            val companion = root.resolve(CWE416JulietFixtures.FIXTURES_DIR.resolve(name))
            assertTrue(companion.exists(), "Missing CIR companion fixture: $companion")
            companion
        }
        val allFixtures = listOf(fixture) + companions

        CIRTaintAnalyzer.loadCirFiles(allFixtures).use { loaded ->
            val entrypoint = assertNotNull(
                loaded.analyzer.cp.findFunctionBySymbolName(entrypoint),
            )

            val vulnerabilities = loaded.analyzer.analyzeWithIfds(listOf(entrypoint))

            assertTrue(vulnerabilities.isEmpty(), "vulnerabilities found!")
        }
    }

    companion object {
        @JvmStatic
        fun cwe416GoodCases(): Stream<Arguments> =
            CWE416JulietFixtures.fixtureArgumentStream(
                findEntrypoint = CWE416JulietFixtures::findGoodEntrypoint,
                missingEntrypointDescription = "_good entrypoint",
            )
    }
}
