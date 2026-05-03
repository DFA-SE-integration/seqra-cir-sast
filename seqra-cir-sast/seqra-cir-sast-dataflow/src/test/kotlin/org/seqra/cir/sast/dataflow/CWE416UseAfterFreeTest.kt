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
    @ParameterizedTest(name = "{2}")
    @MethodSource("cwe416Cases")
    fun useAfterFreeFixture(fileName: String, companionFileNames: List<String>, entrypoint: String) {
        assumeTrue(!System.getenv("CIRTAC_COMPILER").isNullOrBlank(), "CIRTAC_COMPILER is required")

        // The interfile helper for these fixtures cannot be lowered to CIR by
        // the bundled ClangIR (compiler crashes with
        //   "Missing visitor for AggExprEmitter Stmt: IntegerLiteral"
        // on `new TwoIntsClass[100]` / `new twoIntsStruct[100]`). The
        // entrypoint side compiles fine, but without the helper body the
        // analyzer cannot see the malloc/free pair, so we cannot meaningfully
        // assert anything yet. Skip rather than fail — see the TODO in
        // `interfileCompanions`.
        // TODO Take branch erichkeane:emitArrayInit (https://github.com/llvm/llvm-project/pull/192666/changes)
        assumeTrue(
            fileName !in BLOCKED_BY_CLANGIR_AGGREGATE_INIT,
            "Blocked by ClangIR codegen bug on `new TwoIntsClass[100]` / " +
                "`new twoIntsStruct[100]` in companion `_62b.cpp` — companion `.cir` cannot be generated.",
        )

        val root = repoRoot()
        val fixture = root.resolve(FIXTURES_DIR.resolve(fileName))
        assertTrue(fixture.exists(), "Missing CIR fixture: $fixture")
        val companions = companionFileNames.map { name ->
            val companion = root.resolve(FIXTURES_DIR.resolve(name))
            assertTrue(companion.exists(), "Missing CIR companion fixture: $companion")
            companion
        }
        val allFixtures = listOf(fixture) + companions

        CIRTaintAnalyzer.loadCirFiles(allFixtures).use { loaded ->
            val entrypoint = assertNotNull(
                loaded.analyzer.cp.findFunctionBySymbolName(entrypoint),
            )

            val freeFn = loaded.analyzer.cp.findFunctionBySymbolName("free")
            val mallocFn = loaded.analyzer.cp.findFunctionBySymbolName("malloc")
            val printLineFn = loaded.analyzer.cp.findFunctionBySymbolName("printLine")

            val vulnerabilities = loaded.analyzer.analyzeWithIfds(listOf(entrypoint))

            val debugMessage = buildString {
                append("fixture=")
                append(fileName)
                if (companionFileNames.isNotEmpty()) {
                    append(" companions=")
                    append(companionFileNames)
                }
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

        // Juliet's interfile split: `_<n>a.cir` holds the entrypoint and a forward
        // declaration of the helper, while `_<n>b.cir` holds the helper body
        // (which contains the actual malloc / free / new / delete calls). To
        // detect the use-after-free we must load both files into the same CP.
        private val interfileEntrypointPattern = Regex("""^(.*)_(62|63|64)a\.cir$""")
        private val functionDefinitionPattern = Regex("""^\s*cir\.func\b.*@([^\s(]+)\([^)]*\).*\{$""", setOf(RegexOption.MULTILINE))

        private val FIXTURES_DIR = Path.of(
            "juliet-c",
            "samples",
            "CWE416_Use_After_Free",
        )

        // Interfile fixtures whose companion `_62b.cir` cannot be generated
        // because of a ClangIR codegen bug on aggregate-init / non-POD array
        // construction inside `badSource()`. Re-evaluate (re-run the cir-tac
        // compiler) once the upstream fix lands.
        private val BLOCKED_BY_CLANGIR_AGGREGATE_INIT = setOf(
            "CWE416_Use_After_Free__new_delete_array_class_62a.cir",
            "CWE416_Use_After_Free__new_delete_array_struct_62a.cir",
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
                                val companions = interfileCompanions(path)
                                Arguments.of(fileName, companions, entrypoint)
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

        private fun interfileCompanions(path: Path): List<String> {
            val match = interfileEntrypointPattern.matchEntire(path.fileName.toString()) ?: return emptyList()
            val (prefix, variant) = match.destructured
            val companion = "${prefix}_${variant}b.cir"
            val companionPath = path.resolveSibling(companion)
            return if (Files.exists(companionPath)) listOf(companion) else emptyList()
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
