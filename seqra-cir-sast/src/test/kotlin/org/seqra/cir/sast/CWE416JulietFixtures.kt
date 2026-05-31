package org.seqra.cir.sast

import org.junit.jupiter.params.provider.Arguments
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.streams.asSequence

internal object CWE416JulietFixtures {
    val helperOnlySplitFixturePattern = Regex(""".*_(62|63|64)b\.cir$""")

    // Juliet's interfile split: `_<n>a.cir` holds the entrypoint and a forward
    // declaration of the helper, while `_<n>b.cir` holds the helper body
    // (which contains the actual malloc / free / new / delete calls). To
    // analyze use-after-free we must load both files into the same CP.
    private val interfileEntrypointPattern = Regex("""^(.*)_(62|63|64)a\.cir$""")
    private val functionDefinitionPattern =
        Regex("""^\s*cir\.func\b.*@([^\s(]+)\([^)]*\).*\{$""", setOf(RegexOption.MULTILINE))

    val FIXTURES_DIR = Path.of(
        "juliet-c",
        "samples",
        "CWE416_Use_After_Free",
    )

    // Interfile fixtures whose companion `_62b.cir` cannot be generated
    // because of a ClangIR codegen bug on aggregate-init / non-POD array
    // construction inside `badSource()`. Re-evaluate (re-run the cir-tac
    // compiler) once the upstream fix lands.
    val BLOCKED_BY_CLANGIR_AGGREGATE_INIT = setOf(
        "CWE416_Use_After_Free__new_delete_array_class_62a.cir",
        "CWE416_Use_After_Free__new_delete_array_struct_62a.cir",
    )

    fun repoRoot(): Path = Path.of("").toAbsolutePath().normalize().resolve("..").normalize()

    fun interfileCompanions(path: Path): List<String> {
        val match = interfileEntrypointPattern.matchEntire(path.fileName.toString()) ?: return emptyList()
        val (prefix, variant) = match.destructured
        val companion = "${prefix}_${variant}b.cir"
        val companionPath = path.resolveSibling(companion)
        return if (Files.exists(companionPath)) listOf(companion) else emptyList()
    }

    /**
     * Mirrors [findGoodEntrypoint]: Juliet emits unmangled `CWE416_…_bad` or mangled symbols that still embed
     * `CWE416_Use_After_Free`. Require that substring so local helpers like `bad1` are not mistaken for the testcase entrypoint.
     * Exclude `badSource` / `badSink` helpers.
     */
    fun findBadEntrypoint(path: Path): String? {
        val content = Files.readString(path)
        val fileName = path.fileName.toString()

        val candidates = functionDefinitionPattern.findAll(content)
            .map { match -> match.groupValues[1] }
            .filter { symbol ->
                symbol.contains("CWE416_Use_After_Free") &&
                    symbol.contains("bad") &&
                    !symbol.contains("badSource") &&
                    !symbol.contains("badSink")
            }
            .toList()

        val preferred = preferredBadSymbolFromJulietFileName(fileName)
        if (preferred in candidates) {
            return preferred
        }

        val withoutMangled = candidates.filter { !it.startsWith("_ZN") }
        return when {
            candidates.isEmpty() -> null
            withoutMangled.size == 1 -> withoutMangled.single()
            candidates.size == 1 -> candidates.single()
            else -> error(
                "Expected a single top-level bad entrypoint in ${path.fileName}, found: $candidates",
            )
        }
    }

    /**
     * Mirrors [findBadEntrypoint]: Juliet emits unmangled `CWE416_…_good` or mangled symbols that still embed
     * `CWE416_Use_After_Free`. Require that substring so local helpers like `good1` are not mistaken for the testcase entrypoint.
     * Exclude `goodSource` / `goodSink` / `goodG2B*` / `goodB2G*` helpers.
     */
    fun findGoodEntrypoint(path: Path): String? {
        val content = Files.readString(path)
        val fileName = path.fileName.toString()

        val candidates = functionDefinitionPattern.findAll(content)
            .map { match -> match.groupValues[1] }
            .filter { symbol ->
                symbol.contains("CWE416_Use_After_Free") &&
                    symbol.contains("good") &&
                    !symbol.contains("goodSource") &&
                    !symbol.contains("goodSink") &&
                    !symbol.contains("goodG2B") &&
                    !symbol.contains("goodB2G")
            }
            .toList()

        val preferred = preferredGoodSymbolFromJulietFileName(fileName)
        if (preferred in candidates) {
            return preferred
        }

        val numberedGood = Regex("""^.*_(good\d+)\.cir$""").matchEntire(fileName)
        if (numberedGood != null) {
            val variant = numberedGood.groupValues[1]
            val matching = candidates.filter { it.contains(variant, ignoreCase = true) }
            if (matching.size == 1) {
                return matching.single()
            }
        }

        val withoutMangled = candidates.filter { !it.startsWith("_ZN") }
        return when {
            candidates.isEmpty() -> null
            withoutMangled.size == 1 -> withoutMangled.single()
            candidates.size == 1 -> candidates.single()
            else -> error(
                "Expected a single top-level good entrypoint in ${path.fileName}, found: $candidates",
            )
        }
    }

    /**
     * For Juliet interfile splits (`*_62a.cir`, `*_63a.cir`, `*_64a.cir`) ClangIR also emits mangled
     * `::good` helpers (`_ZN…634goodEv`). Prefer the stable C symbol `…_63_good` so we analyze the
     * testcase entrypoint, not an inner template instantiation.
     */
    private fun preferredGoodSymbolFromJulietFileName(fileName: String): String {
        val stem = fileName.removeSuffix(".cir")
        val interfile = Regex("""^(.*)_(62|63|64)a$""").matchEntire(stem) ?: return "${stem}_good"
        return "${interfile.groupValues[1]}_${interfile.groupValues[2]}_good"
    }

    private fun preferredBadSymbolFromJulietFileName(fileName: String): String {
        val stem = fileName.removeSuffix(".cir")
        val interfile = Regex("""^(.*)_(62|63|64)a$""").matchEntire(stem) ?: return "${stem}_bad"
        return "${interfile.groupValues[1]}_${interfile.groupValues[2]}_bad"
    }

    fun fixtureArgumentStream(
        findEntrypoint: (Path) -> String?,
        missingEntrypointDescription: String,
        hasComplementEntrypoint: ((Path) -> String?)? = null,
    ): Stream<Arguments> {
        val fixturesDir = repoRoot().resolve(FIXTURES_DIR)
        val debugFixtureFilter = System.getenv("SEQRA_CWE416_FIXTURE_FILTER")?.takeIf { it.isNotBlank() }

        return Files.list(fixturesDir).use { paths ->
            paths.asSequence()
                .filter { path -> path.fileName.toString().endsWith(".cir") }
                .filter { path -> debugFixtureFilter == null || path.fileName.toString().contains(debugFixtureFilter) }
                .sortedBy { path -> path.fileName.toString() }
                .mapNotNull { path ->
                    val fileName = path.fileName.toString()
                    val entrypoint = findEntrypoint(path)
                    when {
                        entrypoint != null -> {
                            val companions = interfileCompanions(path)
                            Arguments.of(fileName, companions, entrypoint)
                        }

                        helperOnlySplitFixturePattern.matches(fileName) -> null

                        hasComplementEntrypoint?.invoke(path) != null -> null

                        else -> error(
                            "Fixture lacks top-level $missingEntrypointDescription and is not a known helper-only split file: $fileName",
                        )
                    }
                }
                .toList()
                .stream()
        }
    }
}
