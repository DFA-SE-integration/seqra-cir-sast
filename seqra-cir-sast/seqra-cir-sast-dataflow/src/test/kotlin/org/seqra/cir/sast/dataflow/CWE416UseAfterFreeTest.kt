package org.seqra.cir.sast.dataflow

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.streams.asSequence
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CWE416UseAfterFreeTest {
    @ParameterizedTest(name = "{1}")
    @MethodSource("cwe416Cases")
    fun useAfterFreeFixture(fileName: String, entrypoint: String) {
        assumeTrue(!System.getenv("CIRTAC_COMPILER").isNullOrBlank(), "CIRTAC_COMPILER is required")

        val fixture = repoRoot().resolve(
            FIXTURES_DIR.resolve(fileName),
        )
        assertTrue(fixture.exists(), "Missing CIR fixture: $fixture")

        CIRTaintAnalyzer.loadSingleCirFile(fixture).use { loaded ->
            val entrypoint = assertNotNull(
                loaded.analyzer.cp.findFunctionBySymbolName(entrypoint),
            )

            val freeFn = loaded.analyzer.cp.findFunctionBySymbolName("free")
            val mallocFn = loaded.analyzer.cp.findFunctionBySymbolName("malloc")
            val printLineFn = loaded.analyzer.cp.findFunctionBySymbolName("printLine")
//
            val vulnerabilities = loaded.analyzer.analyzeWithIfds(listOf(entrypoint))

            val debugMessage = buildString {
                append("fixture=")
                append(fileName)
                append(" entrypoint=")
                append(entrypoint.id)
                append(" free=")
                append(freeFn?.id?.id)
                append(" malloc=")
                append(mallocFn?.id?.id)
                append(" printLine=")
                append(printLineFn?.id?.id)
                append(" vulnerabilities=")
                append(vulnerabilities.size)
            }

            assertTrue(vulnerabilities.isNotEmpty(), debugMessage)
        }
    }

    private fun repoRoot(): Path = Path.of("").toAbsolutePath().normalize().resolve("..").resolve("..").normalize()

    companion object {
        private val helperOnlySplitFixturePattern = Regex(""".*_(62|63|64)b\.cir$""")
        private val functionDefinitionPattern = Regex("""^\s*cir\.func\b.*@([^\s(]+)\([^)]*\).*\{$""", setOf(RegexOption.MULTILINE))

        private val FIXTURES_DIR = Path.of(
            "juliet-c",
            "samples",
            "CWE416_Use_After_Free",
        )

        @JvmStatic
        fun cwe416Cases(): Stream<Arguments> {
            val fixturesDir = Path.of("").toAbsolutePath().normalize().resolve("..").resolve("..").normalize().resolve(FIXTURES_DIR)
            val debugFixtureFilter = System.getenv("SEQRA_CWE416_FIXTURE_FILTER")?.takeIf { it.isNotBlank() }

            return Files.list(fixturesDir).use { paths ->
                paths.asSequence()
                    .filter { path -> path.fileName.toString().endsWith(".cir") }
                    .filter { path -> debugFixtureFilter == null || path.fileName.toString().contains(debugFixtureFilter) }
                    .sortedBy { path -> path.fileName.toString() }
                    .mapNotNull { path ->
                        val fileName = path.fileName.toString()
                        val entrypoint = findBadEntrypoint(path)
                        when {
                            entrypoint != null -> {
                                Arguments.of(fileName, entrypoint)
                            }

                            helperOnlySplitFixturePattern.matches(fileName) -> null

                            else -> error(
                                "Fixture lacks top-level _bad entrypoint and is not a known helper-only split file: $fileName",
                            )
                        }
                    }
                    .toList()
                    .stream()
            }
        }

        private fun findBadEntrypoint(path: Path): String? {
            val content = Files.readString(path)

            val candidates = functionDefinitionPattern.findAll(content)
                .map { match -> match.groupValues[1] }
                .filter { symbol ->
                    symbol.contains("bad") &&
                        !symbol.contains("badSource") &&
                        !symbol.contains("badSink")
                }
                .toList()

            return when (candidates.size) {
                0 -> null
                1 -> candidates.single()
                else -> error("Expected a single top-level bad entrypoint in ${path.fileName}, found: $candidates")
            }
        }
    }
}
