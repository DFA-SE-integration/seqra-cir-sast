package org.seqra.cir.sast.dataflow

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CWE416UseAfterFreeBadEntrypointsTest {
    @ParameterizedTest(name = "{2}")
    @MethodSource("cwe416BadCases")
    fun useAfterFreeFixtureBad(fileName: String, companionFileNames: List<String>, entrypoint: String) {
        Assumptions.assumeTrue(!System.getenv("CIRTAC_COMPILER").isNullOrBlank(), "CIRTAC_COMPILER is required")

        // The interfile helper for these fixtures cannot be lowered to CIR by
        // the bundled ClangIR (compiler crashes with
        //   "Missing visitor for AggExprEmitter Stmt: IntegerLiteral"
        // on `new TwoIntsClass[100]` / `new twoIntsStruct[100]`). The
        // entrypoint side compiles fine, but without the helper body the
        // analyzer cannot see the malloc/free pair, so we cannot meaningfully
        // assert anything yet. Skip rather than fail — see the TODO in
        // `CWE416JulietFixtures.interfileCompanions`.
        // TODO Take branch erichkeane:emitArrayInit (https://github.com/llvm/llvm-project/pull/192666/changes)
        Assumptions.assumeTrue(
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

            assertTrue(vulnerabilities.isNotEmpty(), "No vulnerabilities found!")
            vulnerabilities.forEach { v ->
                assertNotNull(v.trace, "Cannot build vulnerability trace!")
            }
        }
    }

    companion object {
        @JvmStatic
        fun cwe416BadCases(): Stream<Arguments> =
            CWE416JulietFixtures.fixtureArgumentStream(
                findEntrypoint = CWE416JulietFixtures::findBadEntrypoint,
                missingEntrypointDescription = "_bad entrypoint",
            )
    }
}
