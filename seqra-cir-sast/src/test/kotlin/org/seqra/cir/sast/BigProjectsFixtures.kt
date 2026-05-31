package org.seqra.cir.sast

import org.junit.jupiter.params.provider.Arguments
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Feeds large real-world SARD/Wireshark translation units (under `big-projects/`) into the same
 * IFDS + symbolic-execution pipeline the Juliet CWE416 tests use — but without the Juliet naming
 * convention. The set of cases is read at runtime from a plan file instead of being discovered by
 * symbol-name heuristics, because real functions are named `tvb_free_chain` / `dissect_radius`,
 * not `CWE416_Use_After_Free__..._bad`.
 *
 * Plan file: TSV at the path in env `SEQRA_BIGPROJ_PLAN`, one row per (entrypoint, .cir-set):
 *
 *     label <TAB> expected(bad|good) <TAB> entry_symbol <TAB> cir1,cir2,...
 *
 * - `cirN` are resolved against env `SEQRA_BIGPROJ_DIR` when relative (absolute paths kept as-is).
 * - All `.cir` of a project are listed together so IFDS sees cross-translation-unit free→use paths.
 * - Blank lines and lines starting with `#` are ignored.
 *
 * The plan (and `.cir`) is produced by `big_projects.sh`.
 */
object BigProjectsFixtures {
    const val NO_PLAN_LABEL = "__no-plan__"

    data class Case(
        val label: String,
        val expectFinding: Boolean,
        val entry: String,
        val cirPaths: List<Path>,
    )

    fun plan(): List<Case> {
        val planPath = System.getenv("SEQRA_BIGPROJ_PLAN")?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val planFile = Path.of(planPath)
        if (!planFile.exists()) return emptyList()

        val baseDir = System.getenv("SEQRA_BIGPROJ_DIR")?.trim()?.takeIf { it.isNotEmpty() }?.let { Path.of(it) }

        return planFile.readText().lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val cols = line.split("\t")
                if (cols.size < 4) return@mapNotNull null
                val label = cols[0]
                val expectFinding = cols[1].equals("bad", ignoreCase = true)
                val entry = cols[2]
                val cirPaths = cols[3].split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { raw ->
                        val p = Path.of(raw)
                        if (p.isAbsolute || baseDir == null) p else baseDir.resolve(raw)
                    }
                if (cirPaths.isEmpty()) null else Case(label, expectFinding, entry, cirPaths)
            }
            .toList()
    }

    /**
     * Always emits at least one element so an unconfigured `make test` run does not fail with
     * "no tests found": when the plan is empty the single sentinel case is skipped by the test.
     */
    fun argumentStream(): Stream<Arguments> {
        val cases = plan()
        if (cases.isEmpty()) {
            return Stream.of(Arguments.of(NO_PLAN_LABEL, false, "", emptyList<Path>()))
        }
        return cases.map { Arguments.of(it.label, it.expectFinding, it.entry, it.cirPaths) }.stream()
    }
}
