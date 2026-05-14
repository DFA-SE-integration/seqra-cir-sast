package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.ExclusionSet
import org.seqra.dataflow.ap.ifds.access.ApManager
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnFFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnNonDistributiveFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnZFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnZeroFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartFFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartZFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartZeroFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.SideEffectRequirement
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.Unchanged
import org.seqra.dataflow.configuration.core.TaintMethodSink
import org.seqra.dataflow.configuration.core.TaintMethodSource
import org.seqra.dataflow.cir.ap.ifds.CallPositionToCIRValueResolver
import org.seqra.dataflow.cir.ap.ifds.CIRFactAwareConditionEvaluator
import org.seqra.dataflow.cir.ap.ifds.CIRMarkAwareConditionRewriter
import org.seqra.dataflow.cir.ap.ifds.CIRMethodPositionBaseTypeResolver
import org.seqra.dataflow.cir.ap.ifds.CIRMethodCallFactMapper
import org.seqra.dataflow.cir.ap.ifds.CIRSimpleFactAwareConditionEvaluator
import org.seqra.dataflow.cir.ap.ifds.TaintConfigUtils.applyCleaner
import org.seqra.dataflow.cir.ap.ifds.TaintConfigUtils.applyPassThrough
import org.seqra.dataflow.cir.ap.ifds.TaintConfigUtils.sinkRules
import org.seqra.dataflow.cir.ap.ifds.taint.CIRTaintRulesProvider
import org.seqra.dataflow.cir.ap.ifds.taint.FactReader
import org.seqra.dataflow.cir.ap.ifds.taint.FinalFactReader
import org.seqra.dataflow.cir.ap.ifds.taint.FinalFactReaderWithPrefix
import org.seqra.dataflow.cir.ap.ifds.taint.PositionAccess
import org.seqra.dataflow.cir.ap.ifds.taint.TaintCleanActionEvaluator
import org.seqra.dataflow.cir.ap.ifds.taint.TaintPassActionEvaluator
import org.seqra.dataflow.cir.ap.ifds.taint.TaintSourceActionEvaluator
import org.seqra.ir.api.cir.cfg.CIRDirectCall
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.common.cfg.CommonCallExpr
import org.seqra.util.onSome

private val SEQRA_TRACE_DEBUG: Boolean = System.getenv("SEQRA_TRACE_DEBUG") != null

class CIRMethodCallFlowFunction(
    private val apManager: ApManager,
    private val analysisContext: CIRMethodAnalysisContext,
    private val returnValue: MLIRValue?,
    private val callExpr: CommonCallExpr,
    private val statement: CIRInst,
) : MethodCallFlowFunction {

    init {
        @Suppress("USELESS_CAST")
        check(callExpr is CIRDirectCall) { "Expected CIRDirectCall as callExpr for CIRMethodCallFlowFunction" }
    }

    private val directCall: CIRDirectCall get() = callExpr as CIRDirectCall

    private val config get() = analysisContext.taint.taintConfig as CIRTaintRulesProvider
    private val sinkTracker get() = analysisContext.taint.taintSinkTracker
    private val factTypeChecker get() = analysisContext.factTypeChecker

    override fun propagateZeroToZero(): Set<MethodCallFlowFunction.ZeroCallFact> = buildSet {
        val conditionRewriter = CIRMarkAwareConditionRewriter(
            CallPositionToCIRValueResolver(callExpr, returnValue),
            statement.method,
        )

        applySinkRules(conditionRewriter, factReader = null)

        applySourceRules(
            conditionRewriter,
            factReader = null,
            exclusion = ExclusionSet.Universe,
            createFinalFact = { fact ->
                fact.forEachFactWithAliases { this += CallToReturnZFact(factAp = it) }
            },
        )

        this += CallToReturnZeroFact
        this += CallToStartZeroFact
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<MethodCallFlowFunction.ZeroCallFact> = buildSet {
        propagateFact(
            exclusion = ExclusionSet.Universe,
            factAp = currentFactAp,
            skipCall = { this += Unchanged },
            addSideEffectRequirement = { factReader ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
            },
            addCallToReturn = { factReader, factAp ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
                this += CallToReturnZFact(factAp)
            },
            addCallToStart = { factReader, callerFactAp, startFactBase ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
                this += CallToStartZFact(callerFactAp, startFactBase)
            },
        )
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<MethodCallFlowFunction.FactCallFact> = buildSet {
        propagateFact(
            exclusion = initialFactAp.exclusions,
            factAp = currentFactAp,
            skipCall = { this += Unchanged },
            addSideEffectRequirement = { factReader ->
                this += SideEffectRequirement(factReader.refineFact(initialFactAp.replaceExclusions(ExclusionSet.Empty)))
            },
            addCallToReturn = { factReader, factAp ->
                this += CallToReturnFFact(factReader.refineFact(initialFactAp), factReader.refineFact(factAp))
            },
            addCallToStart = { factReader, callerFactAp, startFactBase ->
                this += CallToStartFFact(
                    factReader.refineFact(initialFactAp),
                    factReader.refineFact(callerFactAp),
                    startFactBase,
                )
            },
        )
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<MethodCallFlowFunction.NDFactCallFact> = buildSet {
        propagateFact(
            exclusion = ExclusionSet.Universe,
            factAp = currentFactAp,
            skipCall = { this += Unchanged },
            addSideEffectRequirement = { factReader ->
                check(!factReader.hasRefinement) { "Can't refine NDF2F edge" }
            },
            addCallToReturn = { factReader, factAp ->
                check(!factReader.hasRefinement) { "Can't refine NDF2F edge" }
                this += CallToReturnNonDistributiveFact(initialFacts, factAp)
            },
            addCallToStart = { factReader, callerFactAp, startFactBase ->
                check(!factReader.hasRefinement) { "Can't refine NDF2F edge" }
                this += MethodCallFlowFunction.CallToStartNDFFact(initialFacts, callerFactAp, startFactBase)
            },
        )
    }

    private fun propagateFact(
        exclusion: ExclusionSet,
        factAp: FinalFactAp,
        skipCall: () -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
        addCallToReturn: (FinalFactReader, FinalFactAp) -> Unit,
        addCallToStart: (
            factReader: FinalFactReader,
            callerFact: FinalFactAp,
            startFactBase: AccessPathBase,
        ) -> Unit,
    ) {
        val aa = analysisContext.aliasAnalysis
        val relevant =
            CIRMethodCallFactMapper.factIsRelevantToMethodCall(returnValue, callExpr, factAp, aa)
        if (!relevant) {
            skipCall()
            return
        }

        val callee = resolvedCallee()

        val conditionRewriter = CIRMarkAwareConditionRewriter(
            CallPositionToCIRValueResolver(callExpr, returnValue),
            statement.method,
        )

        val factReader = FinalFactReader(factAp, apManager)

        applySinkRules(conditionRewriter, factReader)

        applySourceRules(
            conditionRewriter,
            factReader,
            exclusion,
            createFinalFact = { fact ->
                fact.forEachFactWithAliases { addCallToReturn(factReader, it) }
            },
        )

        if (callee == null) {
            if (factReader.hasRefinement) {
                addSideEffectRequirement(factReader)
            }
            return
        }

        CIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
            callee,
            directCall,
            factAp,
            factTypeChecker,
            analysisContext.aliasAnalysis,
        ) { callerFact, startFactBase ->
            applyPassRulesOrCallToStart(
                conditionRewriter,
                factReader,
                callerFact,
                startFactBase,
                callee,
                addCallToReturn,
                addCallToStart,
            )
        }

        if (factReader.hasRefinement) {
            addSideEffectRequirement(factReader)
        }
    }

    private fun resolvedCallee() = directCall.calleeRef?.function

    private fun applyPassRulesOrCallToStart(
        conditionRewriter: CIRMarkAwareConditionRewriter,
        originalFactReader: FinalFactReader,
        unmappedCallerFactAp: FinalFactAp,
        startFactBase: AccessPathBase,
        method: CIRFunction,
        addCallToReturn: (FinalFactReader, FinalFactAp) -> Unit,
        addCallToStart: (
            factReader: FinalFactReader,
            callerFactAp: FinalFactAp,
            startFactBase: AccessPathBase,
        ) -> Unit,
    ) {
        val callerFact = unmappedCallerFactAp.rebase(startFactBase)
        val conditionFactReader = FinalFactReader(callerFact, apManager)

        val conditionEvaluator = CIRFactAwareConditionEvaluator(listOf(conditionFactReader))
        val simpleConditionEvaluator =
            CIRSimpleFactAwareConditionEvaluator(conditionRewriter, conditionEvaluator)

        val cleaner = TaintCleanActionEvaluator()

        val factReaderBeforeCleaner = FinalFactReader(callerFact, apManager)
        val factReaderAfterCleaner =
            applyCleaner(
                config,
                method,
                statement,
                factReaderBeforeCleaner,
                simpleConditionEvaluator,
                cleaner,
            )
                ?: return

        val passEvaluator = TaintPassActionEvaluator(
            apManager,
            factTypeChecker,
            factReaderAfterCleaner,
            CIRMethodPositionBaseTypeResolver(method),
        )

        val passThroughFacts =
            applyPassThrough(
                config,
                method,
                statement,
                simpleConditionEvaluator,
                passEvaluator,
            )

        originalFactReader.updateRefinement(listOf(conditionFactReader))
        originalFactReader.updateRefinement(listOf(factReaderAfterCleaner))

        passThroughFacts.onSome { facts ->
            facts.forEach { fact ->
                val mappedFact = fact.mapExitToReturnFact() ?: return@forEach

                addCallToReturn(factReaderAfterCleaner, mappedFact)

                analysisContext.aliasAnalysis?.forEachAliasAtStatement(statement, mappedFact) { aliased ->
                    addCallToReturn(factReaderAfterCleaner, aliased)
                }
            }
            return
        }

        val cleanedFact = factReaderAfterCleaner.factAp
        check(cleanedFact.base == startFactBase)

        val unmappedFact = cleanedFact.rebase(originalFactReader.factAp.base)

        addCallToStart(originalFactReader, unmappedFact, startFactBase)
    }

    private fun applySinkRules(
        conditionRewriter: CIRMarkAwareConditionRewriter,
        factReader: FinalFactReader?,
    ) {
        val method = resolvedCallee() ?: return
        val sinkRuleList = sinkRules(config, method, statement).toList()
        if (sinkRuleList.isEmpty()) return

        val normalConditionFactReaders = factReader?.toConditionFactReaders().orEmpty()

        val arrayElementFactReaders = normalConditionFactReaders.arrayElementConditionReaders(directCall)

        val conditionFactReaders = normalConditionFactReaders + arrayElementFactReaders

        for (rule in sinkRuleList) {
            val simplified = conditionRewriter.rewrite(rule.condition)
            when {
                simplified.isFalse -> continue
                simplified.isTrue -> emitSinkEvaluation(rule, emptyList(), factReader)
                else -> {
                    val evaluator = CIRFactAwareConditionEvaluator(conditionFactReaders)
                    val ok = evaluator.evalWithAssumptionsCheck(simplified.expr)
                    if (!ok) continue
                    emitSinkEvaluation(rule, evaluator.facts(), factReader)
                }
            }
        }

        normalConditionFactReaders.forEach { factReader?.updateRefinement(it) }
    }

    private fun emitSinkEvaluation(
        rule: TaintMethodSink,
        evaluatedFacts: List<InitialFactAp>,
        factReader: FinalFactReader?,
    ) {
        if (evaluatedFacts.isEmpty()) {
            if (factReader != null) {
                // #region agent log
                if (org.seqra.dataflow.cir.ap.ifds.DebugLog.isEnabled()) {
                    org.seqra.dataflow.cir.ap.ifds.DebugLog.log(
                        hypothesisId = "H5",
                        message = "sink-eval-empty-skip",
                        data = mapOf(
                            "rule" to rule.id,
                            "method" to statement.location.method.toString(),
                            "statement" to statement.toString(),
                            "factReader.factAp" to factReader.factAp.toString(),
                            "factReader.factAp.base" to factReader.factAp.base.toString(),
                        ),
                    )
                }
                // #endregion
                return
            }

            sinkTracker.addUnconditionalVulnerability(
                analysisContext.methodEntryPoint,
                statement,
                rule,
            )
            // #region agent log
            if (org.seqra.dataflow.cir.ap.ifds.DebugLog.isEnabled()) {
                org.seqra.dataflow.cir.ap.ifds.DebugLog.log(
                    hypothesisId = "H5",
                    message = "sink-eval-unconditional",
                    data = mapOf(
                        "rule" to rule.id,
                        "method" to statement.location.method.toString(),
                        "statement" to statement.toString(),
                    ),
                )
            }
            // #endregion
            return
        }

        val mappedFacts =
            evaluatedFacts.mapTo(HashSet()) {
                it.mapExitToReturnFact() ?: error("Fact mapping failure")
            }

        sinkTracker.addVulnerability(
            analysisContext.methodEntryPoint,
            mappedFacts,
            statement,
            rule,
        )
        // #region agent log
        if (org.seqra.dataflow.cir.ap.ifds.DebugLog.isEnabled()) {
            org.seqra.dataflow.cir.ap.ifds.DebugLog.log(
                hypothesisId = "H5",
                message = "sink-eval-vuln",
                data = mapOf(
                    "rule" to rule.id,
                    "method" to statement.location.method.toString(),
                    "statement" to statement.toString(),
                    "evaluatedFactsCount" to evaluatedFacts.size,
                    "evaluatedFacts" to evaluatedFacts.joinToString(" | ") { "${it} base=${it.base}" },
                    "mappedFacts" to mappedFacts.joinToString(" | ") { "${it} base=${it.base}" },
                ),
            )
        }
        // #endregion
    }

    private fun applySourceRules(
        conditionRewriter: CIRMarkAwareConditionRewriter,
        factReader: FinalFactReader?,
        exclusion: ExclusionSet,
        createFinalFact: (FinalFactAp) -> Unit,
    ) {
        val method = resolvedCallee() ?: return

        val sourceRules = config.sourceRulesForMethod(method, statement).toList()
        if (sourceRules.isEmpty()) return

        val conditionFactReaders = factReader?.toConditionFactReaders().orEmpty()

        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager,
            exclusion,
            factTypeChecker,
            returnValueType = method.classpath.findTypeOrNull(method.returnType),
        )

        for (rule in sourceRules) {
            val simplified = conditionRewriter.rewrite(rule.condition)
            when {
                simplified.isFalse -> continue
                simplified.isTrue -> applySourceRuleIfApplicable(rule, emptyList(), factReader, sourceEvaluator, createFinalFact)
                else -> {
                    val evaluator = CIRFactAwareConditionEvaluator(conditionFactReaders)
                    if (!evaluator.evalWithAssumptionsCheck(simplified.expr)) continue
                    applySourceRuleIfApplicable(rule, evaluator.facts(), factReader, sourceEvaluator, createFinalFact)
                }
            }
        }

        conditionFactReaders.forEach { reader -> factReader?.updateRefinement(reader) }
    }

    /** Same guard as JIR `applyRule` without assumption branches. */
    private fun applySourceRuleIfApplicable(
        rule: TaintMethodSource,
        evaluatedFacts: List<InitialFactAp>,
        factReader: FinalFactReader?,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp) -> Unit,
    ) {
        if (evaluatedFacts.isEmpty() && factReader != null) return
        applySourceAction(rule, sourceEvaluator, createFinalFact)
    }

    private fun applySourceAction(
        rule: TaintMethodSource,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp) -> Unit,
    ) {
        for (action in rule.actionsAfter) {
            sourceEvaluator.evaluate(rule, action).onSome { facts ->
                facts.forEach { factAfterSource ->
                    val mappedFact = factAfterSource.mapExitToReturnFact()
                    // #region agent log
                    if (org.seqra.dataflow.cir.ap.ifds.DebugLog.isEnabled()) {
                        val aliases = buildList {
                            if (mappedFact != null) {
                                analysisContext.aliasAnalysis?.forEachAliasAtStatement(statement, mappedFact) { aliased ->
                                    add(aliased)
                                }
                            }
                        }
                        org.seqra.dataflow.cir.ap.ifds.DebugLog.log(
                            hypothesisId = "H1",
                            message = "source-fact",
                            data = mapOf(
                                "rule" to rule.toString(),
                                "method" to statement.location.method.toString(),
                                "statement" to statement.toString(),
                                "factAfterSource" to factAfterSource.toString(),
                                "factAfterSource.base" to factAfterSource.base.toString(),
                                "mappedFact" to mappedFact?.toString(),
                                "mappedFact.base" to mappedFact?.base?.toString(),
                                "aliasesCount" to aliases.size,
                                "aliases" to aliases.joinToString(" | "),
                            ),
                        )
                    }
                    // #endregion
                    if (mappedFact == null) return@forEach
                    createFinalFact(mappedFact)
                    analysisContext.aliasAnalysis?.forEachAliasAtStatement(statement, mappedFact) { aliased ->
                        createFinalFact(aliased)
                    }
                }
            }
        }
    }

    private fun FinalFactAp.mapExitToReturnFact(): FinalFactAp? =
        CIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, this, analysisContext.factTypeChecker)
            .singleOrNull()

    private fun InitialFactAp.mapExitToReturnFact(): InitialFactAp? =
        CIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, this)
            .singleOrNull()

    private fun FinalFactReader.toConditionFactReaders(): List<FinalFactReader> {
        val calleeFn = resolvedCallee() ?: return emptyList()
        val mappedArgIndices = mutableSetOf<Int>()
        val conditionFactReaders = mutableListOf<FinalFactReader>()

        fun mapAndCollect(fact: FinalFactAp) {
            CIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
                calleeFn,
                directCall,
                fact,
                factTypeChecker,
                analysisContext.aliasAnalysis,
            ) { callerFact, startFactBase ->
                val argIdx = (startFactBase as? AccessPathBase.Argument)?.idx
                if (argIdx != null && !mappedArgIndices.add(argIdx)) {
                    return@mapMethodCallToStartFlowFact
                }
                conditionFactReaders += FinalFactReader(callerFact.rebase(startFactBase), apManager)
            }
        }

        mapAndCollect(factAp)
        analysisContext.aliasAnalysis?.forEachAlias(factAp) { aliasedFact ->
            mapAndCollect(aliasedFact)
        }
        return conditionFactReaders
    }

    private fun FinalFactReader.updateRefinement(conditionFactReaders: List<FinalFactReader>) {
        conditionFactReaders.forEach { updateRefinement(it) }
    }

    private fun List<FinalFactReader>.arrayElementConditionReaders(callExpr: CIRDirectCall): List<FactReader> =
        mapNotNull {
            val base = it.factAp.base as? AccessPathBase.Argument ?: return@mapNotNull null

            if (!factTypeChecker.callArgumentMayBeArray(callExpr, base)) {
                return@mapNotNull null
            }

            val arrayElementPosition =
                PositionAccess.Complex(PositionAccess.Simple(base), ElementAccessor)
            if (!it.containsPosition(arrayElementPosition)) return@mapNotNull null

            FinalFactReaderWithPrefix(it, ElementAccessor)
        }

    private inline fun FinalFactAp.forEachFactWithAliases(crossinline body: (FinalFactAp) -> Unit) {
        body(this)
        analysisContext.aliasAnalysis?.forEachAliasAtStatement(statement, this) { aliased ->
            body(aliased)
        }
    }
}
