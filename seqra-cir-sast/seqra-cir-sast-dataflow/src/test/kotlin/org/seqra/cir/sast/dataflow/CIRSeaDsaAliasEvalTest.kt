package org.seqra.cir.sast.dataflow

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRFunctionAliasData
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRCastKind
import org.seqra.ir.api.cir.cfg.CIRCastOpExpr
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.MLIRBlockValue
import org.seqra.ir.api.cir.cfg.MLIRCallSiteLoc
import org.seqra.ir.api.cir.cfg.MLIRFileLineColLoc
import org.seqra.ir.api.cir.cfg.MLIRFusedLoc
import org.seqra.ir.api.cir.cfg.MLIRLocation
import org.seqra.ir.api.cir.cfg.MLIRNameLoc
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIROpaqueLoc
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Validates that the SeaDsa-derived alias info shipped through the protobuf
 * (`<module>.alias.pb` written by `cir-ser-proto --emit-alias`) reproduces the
 * verdicts of the canonical `--sea-dsa-aa-eval` invocation against the
 * matching `.ll` file.
 *
 * Ground-truth source: `results/Test-Suite/Sea-DSA-llvm20/butd-cs/<sub>.log`
 * which contains lines such as:
 *
 *   FAILURE :NOALIAS check at (tests/Test-Suite/src/flow/ben11.c:17:2)
 *   SUCCESS :MUSTALIAS check at (tests/Test-Suite/src/flow/ben11.c:18:2)
 *
 * Each `*ALIAS` annotation in the source maps 1:1 to a callsite in the CIR.
 * For each annotated callsite we compute `sameGroup` from our protobuf and
 * compare it against the verdict that sea-dsa actually returned (encoded in
 * the log line). This way:
 *
 *  - a `FAILURE :NOALIAS` (sea-dsa over-approximated) translates to
 *    `sameGroup expected = true` on our side — we are *aligned* with sea-dsa,
 *    not arguing with its precision;
 *  - a `SUCCESS :MUSTALIAS` translates to `sameGroup expected = true`;
 *  - a `SUCCESS :NOALIAS` translates to `sameGroup expected = false`;
 *  - a `FAILURE :MUSTALIAS` (sea-dsa missed an obvious must) translates to
 *    `sameGroup expected = false`.
 *
 * Test failure means our cir↔llvm bridge is dropping or mis-routing a value,
 * not that sea-dsa is imprecise.
 */
class CIRSeaDsaAliasEvalTest {

    @TestFactory
    fun seaDsaEvalParity(): List<DynamicTest> = SAMPLES.map { sample ->
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
            val instById: Map<Long, CIRInst> = fn.allInstructions.associateBy { it.id.id }
            val resolver = AccessPathResolver(instById)

            val annotated = collectAliasAnnotations(fn)
            if (annotated.isEmpty()) {
                fail("no MUSTALIAS/NOALIAS/... calls found in $entryFn (${cir.fileName})")
            }

            val groupsByBase = cp.aliasGroupsByBase(fn, resolver)

            val errors = mutableListOf<String>()
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
                val verdict = ac.computeSameGroup(groupsByBase, resolver)
                val ok = when (verdict) {
                    is Verdict.SameGroup -> verdict.value == expected
                    is Verdict.Unmappable -> false
                }
                if (!ok) {
                    errors += "[$idx] ${ac.callee} @${cir.fileName}:$line " +
                        "(operands=${ac.a}, ${ac.b}): expected sameGroup=$expected, " +
                        "actual=${verdict.describe()}"
                }
            }

            if (errors.isNotEmpty()) {
                fail(
                    buildString {
                        appendLine("Bridge↔sea-dsa parity discrepancies (${errors.size}/${annotated.size}):")
                        appendLine("  cir=$cir")
                        appendLine("  log=$log")
                        appendLine("  groupsByBase=$groupsByBase")
                        errors.forEach { appendLine("  $it") }
                    },
                )
            }
        }
    }

    private data class Sample(val cirSubpath: String, val entryFn: String = "main")

    private companion object {
        private val SAMPLES = listOf(
            Sample("flow/ben11.c.cir"),
        )

        private val LOG_PATTERN = Regex(
            """\s*(SUCCESS|FAILURE)\s*:(\w+)\s+check\s+at\s+\([^)]*?:(\d+):\d+\)""",
        )
    }

    /** key for indexing log entries: source line + annotation kind. */
    private data class LogKey(val line: Int, val kind: String)

    /**
     * For each (line, kind) we record the *sea-dsa actual verdict* as
     * `expectedSameGroup`:
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
    ) {
        fun computeSameGroup(
            groups: Map<AccessPathBase, Set<AccessPathBase>>,
            resolver: AccessPathResolver,
        ): Verdict {
            val ba = resolver.basesFor(a)
            if (ba.isEmpty()) return Verdict.Unmappable("A")
            val bb = resolver.basesFor(b)
            if (bb.isEmpty()) return Verdict.Unmappable("B")
            // Treat operand bases as a small set: any pair of them being
            // co-grouped is enough to claim sameGroup. This handles cases
            // where the LLVM-side anchor (e.g. an alloca) sits in the
            // bucket while the CIR operand we passed in is a transparent
            // bitcast of it -- after stripping, both reduce to the same
            // alloca and we already account for that here.
            for (a in ba) {
                val grp = groups[a] ?: continue
                for (b in bb) if (grp.contains(b)) return Verdict.SameGroup(true, a, b)
            }
            // Pick representative bases for the diagnostic message.
            return Verdict.SameGroup(false, ba.first(), bb.first())
        }
    }

    private sealed class Verdict {
        data class SameGroup(val value: Boolean, val a: AccessPathBase, val b: AccessPathBase) : Verdict()
        data class Unmappable(val side: String) : Verdict()

        fun describe(): String = when (this) {
            is SameGroup -> "$value (a=$a, b=$b)"
            is Unmappable -> "unmappable operand $side"
        }
    }

    /**
     * Maps an `MLIRValue` into the canonical `AccessPathBase`(s) under
     * which sea-dsa puts the underlying memory location. Transparent
     * pointer casts (`bitcast`, `array_to_ptrdecay`, `address_space`)
     * are stripped: they share their operand's cell on the LLVM side
     * (sea-dsa calls `stripPointerCasts` in `mkCell`/`getCell`), so on
     * the cir side we follow `cir.cast` chains to the producing
     * alloca/load/argument anchor before consulting the alias data.
     *
     * Returns an emptyset when nothing maps. Returns multiple bases
     * only when the resolver would otherwise short-circuit too eagerly
     * (currently never -- bitcast is single-source -- but kept as Set
     * to make the call-site read sanely).
     */
    private class AccessPathResolver(private val instById: Map<Long, CIRInst>) {
        fun basesFor(value: MLIRValue): Set<AccessPathBase> {
            val seen = HashSet<Long>()
            return resolve(value, seen)
        }

        private fun resolve(value: MLIRValue, seen: MutableSet<Long>): Set<AccessPathBase> = when (value) {
            is MLIRValueRef -> resolve(value.value, seen)
            is MLIRBlockValue -> setOf(AccessPathBase.Argument(value.argIndex.toInt()))
            is MLIROpValue -> {
                val opId = value.opIndex.id
                if (!seen.add(opId)) emptySet()
                else stripIfTransparent(opId)?.let { resolve(it, seen) }
                    ?: setOf(AccessPathBase.LocalVar(opId.toInt()))
            }
            else -> emptySet()
        }

        /**
         * If the op behind `opId` is a CIR cast that pointer-aliases its
         * source (bitcast, array-to-ptrdecay, address_space), return
         * the source MLIRValue. Otherwise return null.
         */
        private fun stripIfTransparent(opId: Long): MLIRValue? {
            val inst = instById[opId] as? CIRAssignInst ?: return null
            val cast = inst.rhv as? CIRCastOpExpr ?: return null
            return when (cast.kind) {
                CIRCastKind.Bitcast,
                CIRCastKind.ArrayToPtrdecay,
                CIRCastKind.AddressSpace -> cast.src
                else -> null
            }
        }
    }

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

    private fun CIRClasspath.aliasGroupsByBase(
        fn: CIRFunction,
        resolver: AccessPathResolver,
    ): Map<AccessPathBase, Set<AccessPathBase>> {
        val data: CIRFunctionAliasData = findFunctionAliasData(fn.id) ?: return emptyMap()
        val out = HashMap<AccessPathBase, MutableSet<AccessPathBase>>()
        for (group in data.groups) {
            val bases = group.members
                .flatMap(resolver::basesFor)
                .toHashSet()
            if (bases.size < 2) continue
            for (b in bases) out.getOrPut(b) { hashSetOf() }.addAll(bases)
        }
        return out
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
