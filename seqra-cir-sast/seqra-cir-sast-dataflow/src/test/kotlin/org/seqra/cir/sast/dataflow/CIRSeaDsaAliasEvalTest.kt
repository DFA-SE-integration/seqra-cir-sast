package org.seqra.cir.sast.dataflow

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.seqra.dataflow.cir.ap.ifds.CIRLanguageManager
import org.seqra.dataflow.cir.ap.ifds.CIRLocalAliasAnalysis
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.MLIRCallSiteLoc
import org.seqra.ir.api.cir.cfg.MLIRFileLineColLoc
import org.seqra.ir.api.cir.cfg.MLIRFusedLoc
import org.seqra.ir.api.cir.cfg.MLIRLocation
import org.seqra.ir.api.cir.cfg.MLIRNameLoc
import org.seqra.ir.api.cir.cfg.MLIROpaqueLoc
import org.seqra.ir.api.cir.cfg.MLIRValue
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * End-to-end parity check: the `MUSTALIAS` / `NOALIAS` / ... annotations
 * embedded in the source-level `.c` test fixture are compared with the
 * verdicts that [CIRLocalAliasAnalysis] produces for the same operands.
 *
 * The test deliberately exercises the *whole* analysis pipeline, not the
 * raw protobuf payload:
 *   1. Sea-dsa runs over LLVM-IR and emits `CIRModuleAliasData` (cir-tac).
 *   2. `CIRModuleAliasData` is embedded into `MLIRModule` and persisted by
 *      `CIRErsPersistenceImpl` as a side-blob.
 *   3. `CIRClasspathImpl.findFunctionAliasData` decodes it into
 *      [org.seqra.ir.api.cir.CIRFunctionAliasData].
 *   4. [CIRLocalAliasAnalysis] expands every group with cast/get_member/
 *      ptr_stride derivatives and *transitively closes* groups that share
 *      a CIR-side base. This is the step the test cares about most -
 *      sea-dsa already gives correct cells, but our enrichment can either
 *      under- or over-merge.
 *
 * Ground truth: `results/Test-Suite/Sea-DSA-llvm20/butd-cs/<sub>.log` lines
 * such as
 *
 *   FAILURE :NOALIAS check at (tests/Test-Suite/src/flow/ben11.c:17:2)
 *   SUCCESS :MUSTALIAS check at (tests/Test-Suite/src/flow/ben11.c:18:2)
 *
 * Each `*ALIAS(a, b)` annotation in the source maps 1:1 to a callsite in
 * the CIR. For each annotated callsite we ask
 * [CIRLocalAliasAnalysis.areAliased] for `(a, b)` and compare with the
 * sea-dsa verdict encoded in the log:
 *
 *  - `FAILURE :NOALIAS`   → expected `areAliased = true`  (sea-dsa over-approximated, we should follow it)
 *  - `SUCCESS :MUSTALIAS` → expected `areAliased = true`
 *  - `SUCCESS :NOALIAS`   → expected `areAliased = false`
 *  - `FAILURE :MUSTALIAS` → expected `areAliased = false`
 *
 * A diverging verdict means our enrichment + transitive closure either
 * dropped a sea-dsa pairing or fabricated one - both are bugs we want to
 * catch here, not in production.
 */
class CIRSeaDsaAliasEvalTest {

    @TestFactory
    fun analysisAgreesWithSeaDsa(): List<DynamicTest> = SAMPLES.map { sample ->
        DynamicTest.dynamicTest(sample.cirSubpath) {
            assumeTrue(
                !System.getenv("CIRTAC_COMPILER").isNullOrBlank(),
                "CIRTAC_COMPILER is required",
            )
            val cir = repoRoot().resolve("Test-Suite/build/bc/llvm-20/${sample.cirSubpath}")
            assumeTrue(
                cir.exists(),
                "Missing CIR fixture $cir — run `make testsuite` (inside docker-shell) first",
            )
            val log = repoRoot()
                .resolve("results/Test-Suite/Sea-DSA-llvm20/butd-cs/${sample.cirSubpath.removeSuffix(".cir") + ".log"}")
            assumeTrue(log.exists(), "Missing sea-dsa eval log $log — run alias-analysis-research first")

            runOne(cir, log, sample.entryFn)
        }
    }

    private fun runOne(cir: Path, log: Path, entryFn: String) {
        val expectations = parseSeaDsaEvalLog(log)
        assumeTrue(expectations.isNotEmpty(), "no SUCCESS/FAILURE entries in $log")

        CIRTaintAnalyzer.loadSingleCirFile(cir).use { loaded ->
            val cp = loaded.analyzer.cp
            val fn = assertNotNull(
                cp.findFunctionBySymbolName(entryFn),
                "$entryFn function not resolved in classpath of $cir",
            )

            val annotated = collectAliasAnnotations(fn)
            if (annotated.isEmpty()) {
                fail("no MUSTALIAS/NOALIAS/... calls found in $entryFn (${cir.fileName}) - compiler dropped annotations?")
            }

            // The analysis is constructed from any instruction in the function
            // (it grabs the enclosing CIRFunction off the location); we pick
            // the entry inst by convention.
            val firstInst = fn.allInstructions.firstOrNull()
                ?: fail("$entryFn has no instructions in ${cir.fileName}")
            val analysis = CIRLocalAliasAnalysis(firstInst, CIRLanguageManager(cp))

            val errors = mutableListOf<String>()
            // Track how many positive (areAliased=true) vs negative
            // (areAliased=false) checks were exercised. A fixture that only
            // exercises one direction is dangerous: e.g. an analysis that
            // unconditionally returns true would pass a positives-only suite.
            var positives = 0
            var negatives = 0
            for ((idx, ac) in annotated.withIndex()) {
                val line = ac.sourceLine
                if (line == null) {
                    errors += "[$idx] ${ac.callee}: cannot extract source line from $ac"
                    continue
                }
                val key = LogKey(line, ac.callee)
                val expected = expectations[key]
                if (expected == null) {
                    // Annotation has no matching log entry — usually because
                    // sea-dsa-aa-eval skipped it (e.g. EXPECTEDFAIL_* sites).
                    continue
                }
                if (expected) positives++ else negatives++
                val actual = analysis.areAliased(ac.a, ac.b)
                if (actual != expected) {
                    errors += "[$idx] ${ac.callee} @${cir.fileName}:$line " +
                        "(operands=${ac.a}, ${ac.b}): expected areAliased=$expected, actual=$actual"
                }
            }

            if (errors.isNotEmpty()) {
                fail(
                    buildString {
                        appendLine("CIRLocalAliasAnalysis ↔ sea-dsa parity discrepancies (${errors.size}/${annotated.size}):")
                        appendLine("  cir=$cir")
                        appendLine("  log=$log")
                        appendLine("  exercised: areAliased=true x$positives, areAliased=false x$negatives")
                        errors.forEach { appendLine("  $it") }
                    },
                )
            }
            globalPositives.addAndGet(positives)
            globalNegatives.addAndGet(negatives)
        }
    }

    /**
     * Sanity invariant on top of [analysisAgreesWithSeaDsa]: across all
     * [SAMPLES] that actually ran (i.e. their CIR + sea-dsa log were both
     * present), at least one fixture must contribute a `areAliased = false`
     * expectation. Without this guard, a regression in the analysis that
     * mistakenly reports everything as aliased would still pass the parity
     * test as long as no fixture happens to exercise the negative direction.
     *
     * Skipped (not failed) when the TestFactory itself was filtered out -
     * e.g. when running this method in isolation - because we cannot
     * meaningfully assert coverage we did not measure.
     */
    @Test
    fun negativeAssertionsAreExercised() {
        val total = globalPositives.get() + globalNegatives.get()
        assumeTrue(
            total > 0,
            "No parity assertions exercised - run [analysisAgreesWithSeaDsa] first " +
                "(or run the whole class instead of just this method).",
        )
        check(globalNegatives.get() > 0) {
            "Parity suite covers only positive (areAliased=true) cases - add a sample whose sea-dsa log " +
                "contains 'SUCCESS :NOALIAS' or 'FAILURE :MUSTALIAS' so the negative direction is exercised. " +
                "positives=${globalPositives.get()}, negatives=${globalNegatives.get()}"
        }
    }

    private data class Sample(val cirSubpath: String, val entryFn: String = "main")

    private companion object {
        private val SAMPLES = listOf(
            // ben11.c: 3x MUSTALIAS (SUCCESS, expected=true) + 2x NOALIAS that
            // sea-dsa over-approximated to FAILURE :NOALIAS (expected=true).
            Sample("flow/ben11.c.cir"),
            // ben1.c: 4x MUSTALIAS (SUCCESS, expected=true) + 1x NOALIAS where
            // sea-dsa produced a precise SUCCESS :NOALIAS (expected=false).
            // This is the only fixture in the current SAMPLES set that
            // honestly exercises the areAliased=false direction.
            Sample("flow/ben1.c.cir"),
        )

        private val LOG_PATTERN = Regex(
            """\s*(SUCCESS|FAILURE)\s*:(\w+)\s+check\s+at\s+\([^)]*?:(\d+):\d+\)""",
        )

        // Cross-test counters consumed by [negativeAssertionsAreExercised].
        // AtomicInteger because TestFactory may run dynamic tests in parallel.
        private val globalPositives = java.util.concurrent.atomic.AtomicInteger(0)
        private val globalNegatives = java.util.concurrent.atomic.AtomicInteger(0)
    }

    /** key for indexing log entries: source line + annotation kind. */
    private data class LogKey(val line: Int, val kind: String)

    /**
     * For each (line, kind) we record the *sea-dsa actual verdict* as
     * `expectedAreAliased`:
     *   - `SUCCESS :MUSTALIAS`/`MAYALIAS`/`PARTIALALIAS` → true (sea-dsa says alias)
     *   - `FAILURE :MUSTALIAS`/`MAYALIAS`/`PARTIALALIAS` → false (sea-dsa returned NoAlias)
     *   - `SUCCESS :NOALIAS` → false
     *   - `FAILURE :NOALIAS` → true (sea-dsa over-approximated)
     */
    private fun parseSeaDsaEvalLog(log: Path): Map<LogKey, Boolean> {
        val out = HashMap<LogKey, Boolean>()
        for (raw in log.readLines()) {
            val m = LOG_PATTERN.find(raw) ?: continue
            val (status, kind, lineStr) = m.destructured
            val isNoAlias = kind.equals("NOALIAS", ignoreCase = true)
            val seaDsaSaidAlias = if (isNoAlias) status == "FAILURE" else status == "SUCCESS"
            out[LogKey(lineStr.toInt(), kind.uppercase())] = seaDsaSaidAlias
        }
        return out
    }

    private data class AnnotatedCall(
        val callee: String,
        val a: MLIRValue,
        val b: MLIRValue,
        val sourceLine: Int?,
    )

    private fun collectAliasAnnotations(fn: CIRFunction): List<AnnotatedCall> {
        val recognised = setOf("MUSTALIAS", "MAYALIAS", "PARTIALALIAS", "NOALIAS")
        return fn.allInstructions
            .filterIsInstance<CIRCallOpInst>()
            .mapNotNull { call ->
                val callee = call.callee?.rootReference?.value ?: return@mapNotNull null
                if (callee !in recognised) return@mapNotNull null
                if (call.arg_ops.size != 2) return@mapNotNull null
                AnnotatedCall(
                    callee = callee,
                    a = call.arg_ops[0],
                    b = call.arg_ops[1],
                    sourceLine = call.location.location.sourceLine(),
                )
            }
    }

    private fun MLIRLocation.sourceLine(): Int? = when (this) {
        is MLIRFileLineColLoc -> line
        is MLIRCallSiteLoc -> caller.sourceLine() ?: callee.sourceLine()
        is MLIRFusedLoc -> locations.firstNotNullOfOrNull { it.sourceLine() }
        is MLIRNameLoc -> childLoc.sourceLine()
        is MLIROpaqueLoc -> fallbackLocation.sourceLine()
        else -> null
    }

    private fun repoRoot(): Path =
        Path.of("").toAbsolutePath().normalize().resolve("..").resolve("..").normalize()
}
