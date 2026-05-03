package org.seqra.cir.sast.dataflow

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.seqra.cir.graph.CApplicationGraphImpl
import org.seqra.cir.sast.dataflow.rules.TaintConfiguration
import org.seqra.cir.sast.dataflow.rules.anyNameMatcher
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.seqra.dataflow.ap.ifds.access.ApMode
import org.seqra.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.seqra.dataflow.ap.ifds.taint.TaintSinkTracker
import org.seqra.dataflow.ap.ifds.taint.TaintSinkTracker.TaintVulnerability
import org.seqra.dataflow.ap.ifds.trace.TraceResolver
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.seqra.dataflow.cir.ap.ifds.analysis.CIRAnalysisManager
import org.seqra.dataflow.cir.ap.ifds.taint.CIRTaintRulesProvider
import org.seqra.dataflow.cir.ifds.CIRUnitResolver
import org.seqra.dataflow.cir.ifds.SingletonUnitResolver
import org.seqra.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.seqra.dataflow.configuration.core.TaintCleaner
import org.seqra.dataflow.configuration.core.TaintEntryPointSource
import org.seqra.dataflow.configuration.core.TaintMethodEntrySink
import org.seqra.dataflow.configuration.core.TaintMethodSource
import org.seqra.dataflow.configuration.core.TaintMethodSink
import org.seqra.dataflow.configuration.core.TaintPassThrough
import org.seqra.dataflow.configuration.core.TaintSinkMeta
import org.seqra.dataflow.configuration.core.serialized.PositionBase
import org.seqra.dataflow.configuration.core.serialized.PositionBaseWithModifiers
import org.seqra.dataflow.configuration.core.serialized.SerializedCondition
import org.seqra.dataflow.configuration.core.serialized.SerializedFunctionNameMatcher
import org.seqra.dataflow.configuration.core.serialized.SerializedNameMatcher
import org.seqra.dataflow.configuration.core.serialized.SerializedRule
import org.seqra.dataflow.configuration.core.serialized.SerializedTaintAssignAction
import org.seqra.dataflow.configuration.core.serialized.SerializedTaintCleanAction
import org.seqra.dataflow.configuration.core.serialized.SerializedTaintConfig
import org.seqra.dataflow.configuration.core.serialized.SinkMetaData
import org.seqra.dataflow.ifds.UnitResolver
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.cfg.CommonInst
import org.seqra.ir.impl.CIRSettings
import org.seqra.ir.impl.CIRXodusKvErsSettings
import org.seqra.ir.impl.cirDatabase
import org.seqra.ir.impl.features.CIRLoadStoreFeature
import org.seqra.ir.impl.features.CIRUsages
import org.seqra.ir.impl.features.usagesExt
import org.seqra.util.analysis.ApplicationGraph
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.writeBytes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class CIRTaintAnalyzer(
    val cp: CIRClasspath,
//    val taintConfiguration: TaintRulesProvider, TODO ommited for concrete USE_AFTER_FREE
    val projectLocations: Set<RegisteredLocation>,
    val ifdsTimeout: Duration,
    val ifdsApMode: ApMode,
    val symbolicExecutionEnabled: Boolean,
    val analysisCwe: Set<Int>?,
    val summarySerializationContext: SummarySerializationContext,
    val storeSummaries: Boolean,
    val analysisUnit: CIRUnitResolver = SingletonUnitResolver,
    val debugOptions: DebugOptions,
) : AutoCloseable {
    data class DebugOptions(
        val taintRulesStatsSamplingPeriod: Int?,
        val enableIfdsCoverage: Boolean,
        val factReachabilitySarif: Boolean,
        val enableVulnSummary: Boolean,
    )

    private val ifdsAnalysisGraph by lazy {
        val usages = runBlocking { cp.usagesExt() }
        CApplicationGraphImpl(cp, usages)
    }

    val ifdsEngine by lazy { createIfdsEngine() }

    fun analyzeWithIfds(entryPoints: List<CIRFunction>): List<TaintVulnerability> {
        return analyzeTaintWithIfdsEngine(entryPoints)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createIfdsEngine() = TaintAnalysisUnitRunnerManager(
        CIRAnalysisManager(cp),
        ifdsAnalysisGraph as ApplicationGraph<CommonMethod, CommonInst>,
        analysisUnit as UnitResolver<CommonMethod>,
        taintConfig,
        summarySerializationContext,
        ifdsApMode,
        debugOptions.taintRulesStatsSamplingPeriod,
    )

    private fun analyzeTaintWithIfdsEngine(
        entryPoints: List<CIRFunction>,
    ): List<TaintVulnerability> {
        val analysisStart = TimeSource.Monotonic.markNow()

        val analysisTimeout = ifdsTimeout * 0.95
        runCatching { ifdsEngine.runAnalysis(entryPoints, timeout = analysisTimeout, cancellationTimeout = 30.seconds) }
            .onFailure { logger.error(it) { "Ifds engine failed" } }
            .getOrThrow()

        if (debugOptions.enableIfdsCoverage) {
            logger.debug {
                ifdsEngine.reportCoverage()
            }
        }

        if (storeSummaries) {
            logger.info { "Storing summaries" }
            ifdsEngine.storeSummaries()
        }

        var vulnerabilities = ifdsEngine.getVulnerabilities()
        logger.info { "Total vulnerabilities: ${vulnerabilities.size}" }

        if (debugOptions.enableVulnSummary) {
            logger.info { printVulnSummary(vulnerabilities) }
        }

        if (analysisCwe != null) {
            vulnerabilities = vulnerabilities.filter {
                val cwe = (it.rule.meta as TaintSinkMeta).cwe
                cwe?.intersect(analysisCwe)?.isNotEmpty() ?: true
            }

            logger.info { "Vulnerabilities with cwe $analysisCwe: ${vulnerabilities.size}" }
        }

        return vulnerabilities

//        logger.info { "Start trace generation" }
//        val traceResolutionTimeout = ifdsTimeout - analysisStart.elapsedNow()
//        if (!traceResolutionTimeout.isPositive()) {
//            logger.warn { "No time remaining for trace resolution" }
//            return emptyList()
//        }
//
//        return ifdsEngine.generateTraces(entryPoints, vulnerabilities, traceResolutionTimeout).also {
//            logger.info { "Finish trace generation" }
//        }
    }

    private fun TaintAnalysisUnitRunnerManager.generateTraces(
        entryPoints: List<CIRFunction>,
        vulnerabilities: List<TaintSinkTracker.TaintVulnerability>,
        timeout: Duration,
    ): List<VulnerabilityWithTrace> {
        val entryPointsSet = entryPoints.toHashSet()
        return resolveVulnerabilityTraces(
            entryPointsSet, vulnerabilities,
            resolverParams = TraceResolver.Params(
                resolveEntryPointToStartTrace = symbolicExecutionEnabled,
                startToSourceTraceResolutionLimit = 100,
                startToSinkTraceResolutionLimit = 100,
            ),
            timeout = timeout,
            cancellationTimeout = 30.seconds,
        )
    }

    private val builtinTaintConfiguration by lazy {
        TaintConfiguration(cp).also {
            it.loadConfig(builtinUseAfterFreeConfig())
        }
    }

    private val taintConfig: CIRTaintRulesProvider by lazy {
        CIRTaintRulesProviderAdapter(builtinTaintConfiguration)
    }

    override fun close() {
        runCatching { ifdsEngine.close() }
        cp.close()
    }

    companion object Loader {
        private const val USE_AFTER_FREE_MARK = "use-after-free"

        data class LoadedAnalyzer(
            val analyzer: CIRTaintAnalyzer,
            val workingDir: Path,
        ) : AutoCloseable {
            override fun close() {
                analyzer.close()
                workingDir.toFile().deleteRecursively()
            }
        }

        fun loadSingleCirFile(cirFile: Path): LoadedAnalyzer = loadCirFiles(listOf(cirFile))

        /**
         * Load one or more `.cir` files into a single classpath / analyzer instance.
         *
         * Used for fixtures whose `_bad` entrypoint lives in one file but the
         * actual source/sink lives in a companion file (Juliet's `_62a.cir` +
         * `_62b.cir` interfile pattern, and likewise for variants 63/64).
         * Without the companion file the helper called from the entrypoint is
         * just a forward declaration → no `malloc`/`free` is visible to IFDS
         * → the vulnerability is missed.
         */
        fun loadCirFiles(cirFiles: List<Path>): LoadedAnalyzer {
            require(cirFiles.isNotEmpty()) { "loadCirFiles requires at least one .cir file" }
            val workingDir = createTempDirectory("seqra-cir-sast-dataflow-")
            val protocirFiles = cirFiles.map { cirFile ->
                val copiedCir = workingDir.resolve(cirFile.name)
                copiedCir.writeBytes(cirFile.toFile().readBytes())

                val copiedCirBaseName = copiedCir.name.substringBeforeLast('.')
                val protocir = workingDir.resolve("$copiedCirBaseName.protocir")
                generateProtocir(copiedCir, protocir)
                protocir.toFile()
            }

            val db = cirDatabase(
                CIRSettings().apply {
                    persistenceImpl(CIRXodusKvErsSettings)
                    installFeatures(CIRUsages)
                },
            )
            db.loadFiles(protocirFiles)
            val cp = db.classpath(protocirFiles, listOf(CIRLoadStoreFeature))

            val analyzer = CIRTaintAnalyzer(
                cp = cp,
                projectLocations = cp.registeredLocations.toSet(),
                ifdsTimeout = 1.minutes,
                ifdsApMode = ApMode.Tree,
                symbolicExecutionEnabled = false,
                analysisCwe = null,
                summarySerializationContext = InMemorySummarySerializationContext(),
                storeSummaries = false,
                debugOptions = DebugOptions(
                    taintRulesStatsSamplingPeriod = null,
                    enableIfdsCoverage = false,
                    factReachabilitySarif = false,
                    enableVulnSummary = false,
                ),
            )
            return LoadedAnalyzer(analyzer, workingDir)
        }

        private fun builtinUseAfterFreeConfig(): SerializedTaintConfig {
            val anyFunction = SerializedFunctionNameMatcher.Complex(
                `package` = anyNameMatcher(),
                `class` = anyNameMatcher(),
                name = anyNameMatcher(),
            )

            val simple = { name: String ->
                SerializedFunctionNameMatcher.Simple(
                    `package` = SerializedNameMatcher.Simple(""),
                    `class` = SerializedNameMatcher.Simple(""),
                    name = SerializedNameMatcher.Simple(name),
                )
            }

            // Itanium C++ ABI mangled names for the standard de/allocation operators.
            // We taint Argument(0) of every "release" operator so that any subsequent
            // use of that pointer is flagged. The matching "acquire" operators are
            // listed as cleaners of [USE_AFTER_FREE_MARK] on the call result so that
            // a fresh allocation that happens to reuse a previously-freed SSA name
            // does not propagate stale taint.
            val freeLikeNames = listOf(
                "free",
                // operator delete(void*)        — single-object delete
                "_ZdlPv",
                // operator delete(void*, ulong) — sized single-object delete
                "_ZdlPvm",
                // operator delete[](void*)      — array delete
                "_ZdaPv",
                // operator delete[](void*, ul.) — sized array delete
                "_ZdaPvm",
            )
            val mallocLikeNames = listOf(
                "malloc",
                "calloc",
                "realloc",
                // operator new(unsigned long)        — single-object new
                "_Znwm",
                // operator new[](unsigned long)      — array new
                "_Znam",
                // nothrow / aligned variants
                "_ZnwmRKSt9nothrow_t",
                "_ZnamRKSt9nothrow_t",
            )

            val sourceRules = freeLikeNames.map { name ->
                SerializedRule.Source(
                    function = simple(name),
                    overrides = false,
                    taint = listOf(
                        SerializedTaintAssignAction(
                            kind = USE_AFTER_FREE_MARK,
                            pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0)),
                        ),
                    ),
                )
            }

            val cleanerRules = mallocLikeNames.map { name ->
                SerializedRule.Cleaner(
                    function = simple(name),
                    overrides = false,
                    cleans = listOf(
                        SerializedTaintCleanAction(
                            taintKind = USE_AFTER_FREE_MARK,
                            pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Result),
                        ),
                    ),
                )
            }

            return SerializedTaintConfig(
                source = sourceRules,
                cleaner = cleanerRules,
                sink = listOf(
                    SerializedRule.Sink(
                        function = anyFunction,
                        overrides = false,
                        condition = SerializedCondition.ContainsMark(
                            tainted = USE_AFTER_FREE_MARK,
                            pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0)),
                        ),
                        id = USE_AFTER_FREE_MARK,
                        meta = SinkMetaData(
                            cwe = listOf(416),
                            note = "Freed value is used after free",
                            severity = CommonTaintConfigurationSinkMeta.Severity.Error,
                        ),
                    ),
                ),
            )
        }

        private fun generateProtocir(cirFile: Path, protocir: Path) {
            val compiler = System.getenv("CIRTAC_COMPILER")
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: error("CIRTAC_COMPILER is required to generate ${protocir.fileName}")

            check(compiler.exists() && compiler.canExecute()) {
                "Configured CIRTAC_COMPILER is not executable: ${compiler.absolutePath}"
            }

            val process = ProcessBuilder(listOf(compiler.absolutePath, cirFile.absolutePathString()))
                .redirectOutput(protocir.toFile())
                .start()
            val completed = process.waitFor()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            check(completed == 0) {
                buildString {
                    append("Failed to generate protocir for ${cirFile.absolutePathString()}")
                    if (stderr.isNotBlank()) append(": $stderr")
                }
            }
        }

        private val logger = object : KLogging() {}.logger
    }

    private class InMemorySummarySerializationContext : SummarySerializationContext {
        private var nextMethodId = 1L
        private var nextAccessorId = 1L

        private val methodIds = LinkedHashMap<CommonMethod, Long>()
        private val methodsById = LinkedHashMap<Long, CommonMethod>()
        private val accessorIds = LinkedHashMap<Accessor, Long>()
        private val accessorsById = LinkedHashMap<Long, Accessor>()
        private val summaries = LinkedHashMap<Long, ByteArray>()

        override fun getIdByMethod(method: CommonMethod): Long = methodIds.getOrPut(method) {
            nextMethodId++.also { methodsById[it] = method }
        }

        override fun getIdByAccessor(accessor: Accessor): Long = accessorIds.getOrPut(accessor) {
            nextAccessorId++.also { accessorsById[it] = accessor }
        }

        override fun getMethodById(id: Long): CommonMethod = methodsById[id]
            ?: error("Unknown method id: $id")

        override fun getAccessorById(id: Long): Accessor = accessorsById[id]
            ?: error("Unknown accessor id: $id")

        override fun loadSummaries(method: CommonMethod): ByteArray? = summaries[getIdByMethod(method)]

        override fun storeSummaries(method: CommonMethod, summaries: ByteArray) {
            this.summaries[getIdByMethod(method)] = summaries
        }

        override fun flush() = Unit
    }

    private fun TaintAnalysisUnitRunnerManager.reportCoverage(): String =
        "CIR IFDS coverage reporting ommited cause not indexing hierarchy"

    private fun printVulnSummary(
        vulnerabilities: List<TaintSinkTracker.TaintVulnerability>
    ): String = buildString {
        data class VulnInfo(val location: String, val ruleId: String, val kind: String)

        val info = mutableListOf<VulnInfo>()

        for (v in vulnerabilities) {
            when (v) {
                is TaintSinkTracker.TaintVulnerabilityUnconditional -> {
                    info += VulnInfo("${v.statement.location}|${v.statement}", v.rule.id, "unconditional")
                }

                is TaintSinkTracker.TaintVulnerabilityWithFact -> {
                    info += VulnInfo("${v.statement.location}|${v.statement}", v.rule.id, "fact")
                }
            }
        }

        info.sortWith(compareBy<VulnInfo> { it.kind }.thenBy { it.ruleId }.thenBy { it.location })

        appendLine("VULNERABILITIES:")
        appendLine("#".repeat(50))
        for ((kind, sameKindVuln) in info.groupBy { it.kind }) {
            appendLine(kind)
            appendLine("-".repeat(50))
            for ((rule, sameRuleVuln) in sameKindVuln.groupBy { it.ruleId }) {
                appendLine(rule)
                for (vuln in sameRuleVuln) {
                    appendLine("\t\t${vuln.location}")
                }
            }
        }
        appendLine("#".repeat(50))
    }
}
