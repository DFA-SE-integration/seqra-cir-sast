package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.FactTypeChecker
import org.seqra.dataflow.ap.ifds.ExclusionSet
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnFFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnNonDistributiveFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnZFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnZeroFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartFFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartNDFFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartZeroFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartZFact
import org.seqra.dataflow.ap.ifds.analysis.MethodCallFlowFunction.Unchanged
import org.seqra.dataflow.ap.ifds.access.ApManager
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.dataflow.cir.ap.ifds.CIRFactAwareConditionEvaluator
import org.seqra.dataflow.cir.ap.ifds.CIRMarkAwareConditionRewriter
import org.seqra.dataflow.cir.ap.ifds.CIRMethodCallFactMapper
import org.seqra.dataflow.cir.ap.ifds.CIRMethodPositionBaseTypeResolver
import org.seqra.dataflow.cir.ap.ifds.CIRSimpleFactAwareConditionEvaluator
import org.seqra.dataflow.cir.ap.ifds.CallPositionToCIRValueResolver
import org.seqra.dataflow.cir.ap.ifds.TaintConfigUtils.applyRuleWithAssumptions
import org.seqra.dataflow.cir.ap.ifds.TaintConfigUtils.sinkRules
import org.seqra.dataflow.cir.ap.ifds.taint.CIRTaintRulesProvider
import org.seqra.dataflow.cir.ap.ifds.taint.FinalFactReader
import org.seqra.dataflow.cir.ap.ifds.taint.TaintSourceActionEvaluator
import org.seqra.dataflow.configuration.core.TaintMethodSource
import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.ir.api.cir.cfg.CIRAssignInst
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRCallOpInst
import org.seqra.ir.api.cir.cfg.CIRCastKind
import org.seqra.ir.api.cir.cfg.CIRCastOpExpr
import org.seqra.ir.api.cir.cfg.CIRDirectCall
import org.seqra.ir.api.cir.cfg.CIRFuncOp
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRFunctionParameter
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRTryCallOpInst
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIRStringAttr
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRValue
import org.seqra.ir.api.cir.cfg.MLIRValueRef
import org.seqra.ir.api.common.cfg.CommonCallExpr
import org.seqra.ir.api.common.cfg.BytecodeGraph
import org.seqra.dataflow.util.cartesianProductMapTo
import org.seqra.util.onSome

class CIRMethodCallFlowFunction(
    @Suppress("unused") private val apManager: ApManager,
    @Suppress("unused") private val analysisContext: CIRMethodAnalysisContext,
    private val returnValue: MLIRValue?,
    private val callExpr: CommonCallExpr,
    private val statement: CIRInst,
) : MethodCallFlowFunction {
    private val checker = PermissiveFactTypeChecker
    private val config get() = analysisContext.taint.taintConfig as CIRTaintRulesProvider
    private val sinkTracker get() = analysisContext.taint.taintSinkTracker

    private fun debug(message: () -> String) {
        val enabled = System.getenv("CIR_TAINT_DEBUG")?.lowercase() in setOf("1", "true", "yes", "on")
        if (enabled) {
            System.err.println("[CIR-CALL-FF] ${message()}")
        }
    }

    override fun propagateZeroToZero(): Set<MethodCallFlowFunction.ZeroCallFact> = buildSet {
        val conditionRewriter = CIRMarkAwareConditionRewriter(
            CallPositionToCIRValueResolver(callExpr, returnValue),
            statement.method,
        )

        applySinkRules(conditionRewriter, factReader = null)

        applySourceRules(
            initialFacts = emptySet(),
            conditionRewriter = conditionRewriter,
            factReader = null,
            exclusion = ExclusionSet.Universe,
            createFinalFact = { this += CallToReturnZFact(it) },
            createEdge = { initial, final -> this += CallToReturnFFact(initial, final) },
            createNDEdge = { initials, final -> this += CallToReturnNonDistributiveFact(initials, final) },
        )

        this += CallToReturnZeroFact
        this += CallToStartZeroFact
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<MethodCallFlowFunction.ZeroCallFact> = buildSet {
        val conditionRewriter = CIRMarkAwareConditionRewriter(
            CallPositionToCIRValueResolver(callExpr, returnValue),
            statement.method,
        )
        val factReader = FinalFactReader(currentFactAp, apManager)

        applySourceRules(
            initialFacts = emptySet(),
            conditionRewriter = conditionRewriter,
            factReader = factReader,
            exclusion = ExclusionSet.Universe,
            createFinalFact = { this += CallToReturnZFact(it) },
            createEdge = { initial, final -> this += CallToReturnFFact(initial, final) },
            createNDEdge = { initials, final -> this += CallToReturnNonDistributiveFact(initials, final) },
        )

        debug { "propagateZeroToFact statement=$statement fact=$currentFactAp returnValue=$returnValue" }
        if (!CIRMethodCallFactMapper.factIsRelevantToMethodCall(returnValue, callExpr, currentFactAp)) {
            debug { "fact is irrelevant to method call" }
            add(Unchanged)
            return@buildSet
        }

        applySinkRules(conditionRewriter, factReader)

        val callee = callExpr.calleeOrNull() ?: run {
            debug { "calleeOrNull returned null" }
            add(Unchanged)
            return@buildSet
        }

        CIRMethodCallFactMapper.mapMethodCallToStartFlowFact(callee, callExpr, currentFactAp, checker) { callerFact, startFactBase ->
            debug { "mapped zero fact callerFact=$callerFact startFactBase=$startFactBase callee=${callee.name}" }
            add(CallToStartZFact(callerFact, startFactBase))
        }
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<MethodCallFlowFunction.FactCallFact> = buildSet {
        val conditionRewriter = CIRMarkAwareConditionRewriter(
            CallPositionToCIRValueResolver(callExpr, returnValue),
            statement.method,
        )
        val factReader = FinalFactReader(currentFactAp, apManager)

        applySourceRules(
            initialFacts = setOf(initialFactAp),
            conditionRewriter = conditionRewriter,
            factReader = factReader,
            exclusion = initialFactAp.exclusions,
            createFinalFact = { this += CallToReturnFFact(initialFactAp, it) },
            createEdge = { initial, final -> this += CallToReturnFFact(initial, final) },
            createNDEdge = { initials, final -> this += CallToReturnNonDistributiveFact(initials, final) },
        )

        debug { "propagateFactToFact statement=$statement initial=$initialFactAp current=$currentFactAp" }
        if (!CIRMethodCallFactMapper.factIsRelevantToMethodCall(returnValue, callExpr, currentFactAp)) {
            debug { "fact is irrelevant to method call" }
            add(Unchanged)
            return@buildSet
        }

        applySinkRules(conditionRewriter, factReader)

        val callee = callExpr.calleeOrNull() ?: run {
            debug { "calleeOrNull returned null" }
            add(Unchanged)
            return@buildSet
        }

        CIRMethodCallFactMapper.mapMethodCallToStartFlowFact(callee, callExpr, currentFactAp, checker) { callerFact, startFactBase ->
            debug { "mapped fact callerFact=$callerFact startFactBase=$startFactBase callee=${callee.name}" }
            add(CallToStartFFact(initialFactAp, callerFact, startFactBase))
        }
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<MethodCallFlowFunction.NDFactCallFact> = buildSet {
        val conditionRewriter = CIRMarkAwareConditionRewriter(
            CallPositionToCIRValueResolver(callExpr, returnValue),
            statement.method,
        )
        val factReader = FinalFactReader(currentFactAp, apManager)

        applySourceRules(
            initialFacts = initialFacts,
            conditionRewriter = conditionRewriter,
            factReader = factReader,
            exclusion = ExclusionSet.Universe,
            createFinalFact = { this += CallToReturnNonDistributiveFact(initialFacts, it) },
            createEdge = { _, _ -> error("Unexpected") },
            createNDEdge = { initials, final -> this += CallToReturnNonDistributiveFact(initials, final) },
        )

        if (!CIRMethodCallFactMapper.factIsRelevantToMethodCall(returnValue, callExpr, currentFactAp)) {
            add(Unchanged)
            return@buildSet
        }

        applySinkRules(conditionRewriter, factReader)

        val callee = callExpr.calleeOrNull() ?: run {
            add(Unchanged)
            return@buildSet
        }

        CIRMethodCallFactMapper.mapMethodCallToStartFlowFact(callee, callExpr, currentFactAp, checker) { callerFact, startFactBase ->
            add(CallToStartNDFFact(initialFacts, callerFact, startFactBase))
        }
    }

    private fun CommonCallExpr.calleeOrNull() = when (this) {
        is CIRCallOpInst -> calleeRef?.function
        is CIRTryCallOpInst -> calleeRef?.function
        else -> null
    }

    private fun CommonCallExpr.calleeForRuleLookupOrNull(): CIRFunction? = when (this) {
        is CIRDirectCall -> calleeRef?.function ?: calleeRef?.symbolName?.let(::syntheticCalleeForRuleLookup)
        else -> null
    }

    private fun syntheticCalleeForRuleLookup(symbolName: String): CIRFunction = SyntheticCIRFunction(
        symbolName = symbolName,
        moduleID = statement.method.id.moduleID,
        parameterTypes = (callExpr as? CIRDirectCall)?.arg_ops?.map { it.type }.orEmpty(),
        returnType = (callExpr as? CIRDirectCall)?.result ?: MLIRTypeID(statement.method.id.moduleID, "void"),
        classpath = statement.method.classpath,
        info = statement.method.info.copy(symName = MLIRStringAttr(symbolName, null)),
    )

    private fun applySourceRules(
        initialFacts: Set<InitialFactAp>,
        conditionRewriter: CIRMarkAwareConditionRewriter,
        factReader: FinalFactReader?,
        exclusion: ExclusionSet,
        createFinalFact: (FinalFactAp) -> Unit,
        createEdge: (InitialFactAp, FinalFactAp) -> Unit,
        createNDEdge: (Set<InitialFactAp>, FinalFactAp) -> Unit,
    ) {
        val method = callExpr.calleeForRuleLookupOrNull() ?: return
        val sourceRules = config.sourceRulesForMethod(method, statement).toList()
        debug {
            "source rule lookup statement=$statement ruleMethod=${method.name} resolved=${callExpr.calleeOrNull()?.name} sourceRules=${sourceRules.size}"
        }
        if (sourceRules.isEmpty()) return

        val conditionFactReaders = factReader?.toConditionFactReaders().orEmpty()
        val conditionEvaluator = conditionFactReaders.takeIf { it.isNotEmpty() }?.let(::CIRFactAwareConditionEvaluator)
        val simpleConditionEvaluator = CIRSimpleFactAwareConditionEvaluator(conditionRewriter, conditionEvaluator)
        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager,
            exclusion,
            analysisContext.factTypeChecker,
            returnValueType = method.classpath.findTypeOrNull(method.returnType),
        )

        sourceRules.applyRuleWithAssumptions(
            apManager,
            conditionRewriter,
            initialFacts,
            conditionFactReaders,
            condition = { condition },
            storeAssumptions = { rule, facts -> sinkTracker.addSourceRuleAssumptions(rule, statement, facts) },
            currentAssumptions = { rule -> sinkTracker.currentSourceRuleAssumptions(rule, statement) },
            currentAssumptionPreconditions = { rule, facts ->
                sinkTracker.currentSourceRuleAssumptionsPreconditions(rule, statement, facts)
            },
            applyRule = { rule, evaluatedFacts ->
                debug { "applying source rule=${rule} evaluatedFacts=${evaluatedFacts.size}" }
                if (evaluatedFacts.isEmpty() && factReader != null) return@applyRuleWithAssumptions
                applySourceAction(rule, sourceEvaluator, createFinalFact)
            },
            applyRuleWithAssumptions = { rule, factsWithPreconditions ->
                val factPreconditions = factsWithPreconditions.map { it.preconditions }
                factPreconditions.cartesianProductMapTo { preconditions ->
                    val nonZeroPreconditions = hashSetOf<InitialFactAp>()
                    for (precondition in preconditions) {
                        if (precondition.isEmpty()) continue
                        nonZeroPreconditions.addAll(precondition)
                    }

                    if (nonZeroPreconditions.isEmpty()) {
                        check(initialFacts.isEmpty()) { "Unexpected zero precondition" }
                        applySourceAction(rule, sourceEvaluator, createFinalFact)
                        return@cartesianProductMapTo
                    }

                    if (nonZeroPreconditions.size == 1) {
                        val precondition = nonZeroPreconditions.first()
                        if (initialFacts.isEmpty()) {
                            val newInitial = precondition.replaceExclusions(ExclusionSet.Empty)
                            applySourceAction(rule, sourceEvaluator) { fact ->
                                createEdge(newInitial, fact.replaceExclusions(ExclusionSet.Empty))
                            }
                            return@cartesianProductMapTo
                        }

                        if (initialFacts.size == 1) {
                            val initialFact = initialFacts.first()
                            check(precondition == initialFact.replaceExclusions(ExclusionSet.Universe)) {
                                "Unexpected fact precondition"
                            }
                            applySourceAction(rule, sourceEvaluator, createFinalFact)
                            return@cartesianProductMapTo
                        }
                    }

                    applySourceAction(rule, sourceEvaluator) { fact ->
                        createNDEdge(nonZeroPreconditions, fact.replaceExclusions(ExclusionSet.Universe))
                    }
                }
            },
        )

        conditionFactReaders.forEach { reader -> factReader?.updateRefinement(reader) }
    }

    private fun applySourceAction(
        rule: TaintMethodSource,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp) -> Unit,
    ) {
        for (action in rule.actionsAfter) {
            sourceEvaluator.evaluate(rule, action).onSome { facts ->
                facts.forEach { fact ->
                    fact.mapExitToReturnFact()?.emitWithCallerAliasOrigins(createFinalFact)
                }
            }
        }
    }

    private fun FinalFactAp.emitWithCallerAliasOrigins(createFinalFact: (FinalFactAp) -> Unit) {
        debug { "emit source fact base=$base fact=$this" }
        createFinalFact(this)

        val visitedBases = hashSetOf(base)
        emitWithCallerAliasOrigins(this, base, visitedBases, createFinalFact)
    }

    private fun emitWithCallerAliasOrigins(
        fact: FinalFactAp,
        currentBase: AccessPathBase,
        visitedBases: MutableSet<AccessPathBase>,
        createFinalFact: (FinalFactAp) -> Unit,
    ) {
        val assignInst = callerAssignInstByBase(currentBase) ?: return

        for (sourceValue in aliasPreservingSourceValues(assignInst.rhv)) {
            val sourceBase = accessPathBase(sourceValue) ?: continue
            if (!visitedBases.add(sourceBase)) continue

            debug { "lift source fact from=$currentBase to=$sourceBase via=$sourceValue" }
            createFinalFact(fact.rebase(sourceBase))
            emitWithCallerAliasOrigins(fact, sourceBase, visitedBases, createFinalFact)
        }
    }

    private fun callerAssignInstByBase(base: AccessPathBase): CIRAssignInst? =
        statement.method.allInstructions.asSequence()
            .filterIsInstance<CIRAssignInst>()
            .firstOrNull { accessPathBase(it.lhv) == base }

    private fun aliasPreservingSourceValues(expr: org.seqra.ir.api.cir.cfg.CIRExpr): List<MLIRValue> = when (expr) {
        is MLIRValueRef -> listOf(expr.value)
        is CIRCastOpExpr -> when (expr.kind) {
            CIRCastKind.Bitcast,
            CIRCastKind.AddressSpace -> listOf(expr.src)

            else -> emptyList()
        }

        is MLIRValue -> listOf(expr)
        else -> emptyList()
    }

    private fun accessPathBase(value: MLIRValue): AccessPathBase? = when (value) {
        is MLIRValueRef -> accessPathBase(value.value)
        is org.seqra.ir.api.cir.cfg.MLIRBlockValue -> AccessPathBase.Argument(value.argIndex.toInt())
        is org.seqra.ir.api.cir.cfg.MLIROpValue -> AccessPathBase.LocalVar(value.opIndex.id.toInt())
        else -> null
    }

    private fun applySinkRules(
        conditionRewriter: CIRMarkAwareConditionRewriter,
        factReader: FinalFactReader?,
    ) {
        val method = callExpr.calleeForRuleLookupOrNull() ?: return
        val rules = sinkRules(config, method, statement).toList()
        debug {
            "sink rule lookup statement=$statement ruleMethod=${method.name} resolved=${callExpr.calleeOrNull()?.name} sinkRules=${rules.size}"
        }
        if (rules.isEmpty()) return

        val conditionFactReaders = factReader?.toConditionFactReaders().orEmpty()

        rules.applyRuleWithAssumptions(
            apManager,
            conditionRewriter,
            conditionFactReaders,
            condition = { condition },
            storeAssumptions = { rule, facts -> sinkTracker.addSinkRuleAssumptions(rule, statement, facts) },
            currentAssumptions = { rule -> sinkTracker.currentSinkRuleAssumptions(rule, statement) },
            applyRule = { rule, evaluatedFacts ->
                debug { "applying sink rule=$rule evaluatedFacts=${evaluatedFacts.size}" }
                if (evaluatedFacts.isEmpty()) {
                    if (factReader != null) return@applyRuleWithAssumptions

                    sinkTracker.addUnconditionalVulnerability(
                        analysisContext.methodEntryPoint,
                        statement,
                        rule,
                    )
                    return@applyRuleWithAssumptions
                }

                val mappedFacts = evaluatedFacts.mapTo(hashSetOf()) {
                    it.mapExitToReturnFact() ?: error("Fact mapping failure")
                }
                debug { "sink mappedFacts=$mappedFacts" }

                sinkTracker.addVulnerability(
                    analysisContext.methodEntryPoint,
                    mappedFacts,
                    statement,
                    rule,
                )
            },
        )

        conditionFactReaders.forEach { reader -> factReader?.updateRefinement(reader) }
    }

    private fun FinalFactAp.mapExitToReturnFact(): FinalFactAp? =
        CIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, this, analysisContext.factTypeChecker)
            .singleOrNull()

    private fun InitialFactAp.mapExitToReturnFact(): InitialFactAp? =
        CIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, this)
            .singleOrNull()

    private fun FinalFactReader.toConditionFactReaders(): List<FinalFactReader> {
        val callee = callExpr.calleeOrNull()
        if (callee == null && callExpr is CIRDirectCall && callExpr.calleeRef != null) {
            val directArgs = when (callExpr) {
                is CIRCallOpInst -> callExpr.arg_ops
                is CIRTryCallOpInst -> callExpr.arg_ops
                else -> emptyList()
            }
            return directArgs.mapIndexedNotNull { index, arg ->
                val argBase = accessPathBase(arg) ?: return@mapIndexedNotNull null
                if (argBase != factAp.base) return@mapIndexedNotNull null
                FinalFactReader(factAp.rebase(AccessPathBase.Argument(index)), apManager)
            }
        }
        callee ?: return emptyList()
        val conditionFactReaders = mutableListOf<FinalFactReader>()
        CIRMethodCallFactMapper.mapMethodCallToStartFlowFact(callee, callExpr, factAp, checker) { callerFact, startFactBase ->
            conditionFactReaders += FinalFactReader(callerFact.rebase(startFactBase), apManager)
        }
        return conditionFactReaders
    }

    private object PermissiveFactTypeChecker : FactTypeChecker {
        override fun filterFactByLocalType(actualType: org.seqra.ir.api.common.CommonType?, factAp: FinalFactAp): FinalFactAp = factAp

        override fun accessPathFilter(accessPath: List<org.seqra.dataflow.ap.ifds.Accessor>): FactTypeChecker.FactApFilter =
            FactTypeChecker.AlwaysAcceptFilter
    }

    private class SyntheticCIRFunction(
        symbolName: String,
        moduleID: MLIRModuleID,
        parameterTypes: List<MLIRTypeID>,
        override val returnType: MLIRTypeID,
        override val classpath: org.seqra.ir.api.cir.CIRClasspath,
        override val info: CIRFuncOp,
    ) : CIRFunction {
        override val id: CIRFunctionID = CIRFunctionID(moduleID, symbolName)

        override val parameters: List<CIRFunctionParameter> by lazy {
            parameterTypes.mapIndexed { index, type -> SyntheticCIRFunctionParameter(index, type, this) }
        }

        override val blocks: CIRBlockList = CIRBlockList(emptyList())
        override val allInstructions: List<CIRInst> = emptyList()
        override val assignInstByLhv: Map<MLIRValue, CIRAssignInst> = emptyMap()

        override fun <T> withIRNode(body: (ByteArray?) -> T): T = body(null)

        override fun flowGraph(): BytecodeGraph<CIRInst> = EmptyBytecodeGraph

        override fun equals(other: Any?): Boolean = other is SyntheticCIRFunction && other.id == id

        override fun hashCode(): Int = id.hashCode()
    }

    private class SyntheticCIRFunctionParameter(
        override val index: Int,
        override val type: MLIRTypeID,
        override val method: CIRFunction,
    ) : CIRFunctionParameter

    private object EmptyBytecodeGraph : BytecodeGraph<CIRInst> {
        override val instructions: List<CIRInst> = emptyList()
        override val entries: List<CIRInst> = emptyList()
        override val exits: List<CIRInst> = emptyList()

        override fun successors(node: CIRInst): Set<CIRInst> = emptySet()
        override fun predecessors(node: CIRInst): Set<CIRInst> = emptySet()
        override fun throwers(node: CIRInst): Set<CIRInst> = emptySet()
        override fun catchers(node: CIRInst): Set<CIRInst> = emptySet()
    }
}
