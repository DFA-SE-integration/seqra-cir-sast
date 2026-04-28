package org.seqra.cir.sast.dataflow.rules

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.seqra.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.seqra.dataflow.configuration.core.Action
import org.seqra.dataflow.configuration.core.Argument
import org.seqra.dataflow.configuration.core.AssignMark
import org.seqra.dataflow.configuration.core.ClassStatic
import org.seqra.dataflow.configuration.core.Condition
import org.seqra.dataflow.configuration.core.ConstantBooleanValue
import org.seqra.dataflow.configuration.core.ConstantEq
import org.seqra.dataflow.configuration.core.ConstantGt
import org.seqra.dataflow.configuration.core.ConstantIntValue
import org.seqra.dataflow.configuration.core.ConstantLt
import org.seqra.dataflow.configuration.core.ConstantMatches
import org.seqra.dataflow.configuration.core.ConstantStringValue
import org.seqra.dataflow.configuration.core.ConstantTrue
import org.seqra.dataflow.configuration.core.ContainsMark
import org.seqra.dataflow.configuration.core.CopyAllMarks
import org.seqra.dataflow.configuration.core.CopyMark
import org.seqra.dataflow.configuration.core.IsConstant
import org.seqra.dataflow.configuration.core.Not
import org.seqra.dataflow.configuration.core.Position
import org.seqra.dataflow.configuration.core.PositionAccessor
import org.seqra.dataflow.configuration.core.PositionWithAccess
import org.seqra.dataflow.configuration.core.RemoveAllMarks
import org.seqra.dataflow.configuration.core.RemoveMark
import org.seqra.dataflow.configuration.core.Result
import org.seqra.dataflow.configuration.core.TaintCleaner
import org.seqra.dataflow.configuration.core.TaintConfigurationItem
import org.seqra.dataflow.configuration.core.TaintEntryPointSource
import org.seqra.dataflow.configuration.core.TaintMark
import org.seqra.dataflow.configuration.core.TaintMethodEntrySink
import org.seqra.dataflow.configuration.core.TaintMethodExitSink
import org.seqra.dataflow.configuration.core.TaintMethodSink
import org.seqra.dataflow.configuration.core.TaintMethodSource
import org.seqra.dataflow.configuration.core.TaintPassThrough
import org.seqra.dataflow.configuration.core.TaintSinkMeta
import org.seqra.dataflow.configuration.core.TaintStaticFieldSource
import org.seqra.dataflow.configuration.core.This
import org.seqra.dataflow.configuration.core.TypeMatchesPattern
import org.seqra.dataflow.configuration.core.isFalse
import org.seqra.dataflow.configuration.core.mkAnd
import org.seqra.dataflow.configuration.core.mkFalse
import org.seqra.dataflow.configuration.core.mkOr
import org.seqra.dataflow.configuration.core.mkTrue
import org.seqra.dataflow.configuration.core.serialized.*
import org.seqra.dataflow.configuration.core.serialized.SerializedCondition.AnnotationConstraint
import org.seqra.dataflow.configuration.core.serialized.SerializedCondition.AnnotationParamMatcher
import org.seqra.dataflow.configuration.core.serialized.SerializedNameMatcher.ClassPattern
import org.seqra.dataflow.configuration.core.serialized.SerializedNameMatcher.Pattern
import org.seqra.dataflow.configuration.core.serialized.SerializedNameMatcher.Simple
import org.seqra.dataflow.configuration.core.simplify
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRVoidType
import java.util.Collections
import kotlin.collections.forEach
import java.util.concurrent.atomic.AtomicInteger

class TaintConfiguration(cp: CIRClasspath) {
    private val patternManager = PatternManager()

    private val entryPointConfig = TaintRulesStorage<SerializedRule.EntryPoint, TaintEntryPointSource>()
    private val sourceConfig = TaintRulesStorage<SerializedRule.Source, TaintMethodSource>()
    private val sinkConfig = TaintRulesStorage<SerializedRule.Sink, TaintMethodSink>()
    private val passThroughConfig = TaintRulesStorage<SerializedRule.PassThrough, TaintPassThrough>()
    private val cleanerConfig = TaintRulesStorage<SerializedRule.Cleaner, TaintCleaner>()
    private val methodExitSinkConfig = TaintRulesStorage<SerializedRule.MethodExitSink, TaintMethodExitSink>()
    private val analysisEndSinkConfig = TaintRulesStorage<SerializedRule.MethodExitSink, TaintMethodExitSink>()
    private val methodEntrySinkConfig = TaintRulesStorage<SerializedRule.MethodEntrySink, TaintMethodEntrySink>()

    private val taintMarks = hashMapOf<String, TaintMark>()

    fun loadConfig(config: SerializedTaintConfig) {
        config.entryPoint?.let { entryPointConfig.addRules(it) }
        config.source?.let { sourceConfig.addRules(it) }
        config.sink?.let { sinkConfig.addRules(it) }
        config.passThrough?.let { passThroughConfig.addRules(it) }
        config.cleaner?.let { cleanerConfig.addRules(it) }
        config.methodExitSink?.let { methodExitSinkConfig.addRules(it) }
        config.methodEntrySink?.let { methodEntrySinkConfig.addRules(it) }
        config.analysisEndSink?.let { r -> analysisEndSinkConfig.addRules(r.map { it.asMethodExitSink() }) }
    }

    private val anyFunction by lazy {
        SerializedFunctionNameMatcher.Complex(
            anyNameMatcher(), anyNameMatcher(), anyNameMatcher()
        )
    }

    private fun AnalysisEndSink.asMethodExitSink(): SerializedRule.MethodExitSink {
        return SerializedRule.MethodExitSink(anyFunction, overrides = false, condition = condition, id = id, meta = meta)
    }

    fun entryPointForMethod(method: CIRFunction): List<TaintEntryPointSource> = entryPointConfig.getConfigForMethod(method)
    fun sourceForMethod(method: CIRFunction): List<TaintMethodSource> = sourceConfig.getConfigForMethod(method)
    fun sinkForMethod(method: CIRFunction): List<TaintMethodSink> = sinkConfig.getConfigForMethod(method)
    fun passThroughForMethod(method: CIRFunction): List<TaintPassThrough> = passThroughConfig.getConfigForMethod(method)
    fun cleanerForMethod(method: CIRFunction): List<TaintCleaner> = cleanerConfig.getConfigForMethod(method)
    fun methodExitSinkForMethod(method: CIRFunction): List<TaintMethodExitSink> = methodExitSinkConfig.getConfigForMethod(method)
    fun analysisEndSinkForMethod(method: CIRFunction): List<TaintMethodExitSink> = analysisEndSinkConfig.getConfigForMethod(method)
    fun methodEntrySinkForMethod(method: CIRFunction): List<TaintMethodEntrySink> = methodEntrySinkConfig.getConfigForMethod(method)

    private inner class TaintRulesStorage<S : SerializedRule, T : TaintConfigurationItem> {
        private var builder: MethodTaintRulesStorage.Builder<S>? = MethodTaintRulesStorage.Builder(patternManager)
        private var storage: MethodTaintRulesStorage<S>? = null

        private fun storage(): MethodTaintRulesStorage<S> {
            storage?.let { return it }

            storage = builder?.build()
            builder = null

            return storage ?: error("Storage initialization failed")
        }

        fun addRules(rules: List<S>) {
            val builder = this.builder ?: error("Storage rule set closed")
            builder.addRules(rules)
        }

        private val methodItems = hashMapOf<CIRFunction, List<T>>()

        @Synchronized
        fun getConfigForMethod(method: CIRFunction): List<T> = methodItems.getOrPut(method) {
            resolveMethodItems(method).adjustEmptyList()
        }

        private fun resolveMethodItems(method: CIRFunction): List<T> {
            val rules = mutableListOf<S>()
            storage().findRules(rules, method)

            rules.removeAll { it.signature?.matchFunctionSignature(method) == false }

            return rules.flatMap { resolveMethodRule(it, method) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun resolveMethodRule(rule: S, method: CIRFunction): List<T> =
            rule.resolveMethodRule(method) as List<T>
    }

    private fun SerializedNameMatcher.match(name: String): Boolean = when (this) {
        is Simple -> if (value == "*") true else value == name
        is Pattern -> {
            isAny() || patternManager.matchPattern(pattern, name)
        }
        is ClassPattern -> {
            val (pkgName, clsName) = splitClassName(name)
            `package`.match(pkgName) && `class`.match(clsName)
        }
    }

    private fun SerializedSignatureMatcher.matchFunctionSignature(method: CIRFunction): Boolean {
        when (this) {
            is SerializedSignatureMatcher.Simple -> {
                if (method.parameters.size != args.size) return false

                if (!`return`.match(method.returnType.typeName)) return false

                return args.zip(method.parameters).all { (matcher, param) ->
                    matcher.match(param.type.typeName)
                }
            }

            is SerializedSignatureMatcher.Partial -> {
                val ret = `return`
                if (ret != null && !ret.match(method.returnType.typeName)) return false

                val params = params
                if (params != null) {
                    for (param in params) {
                        val methodParam = method.parameters.getOrNull(param.index) ?: return false
                        if (!param.type.match(methodParam.type.typeName)) return false
                    }
                }

                return true
            }
        }
    }

    private fun SerializedRule.resolveMethodRule(method: CIRFunction): List<TaintConfigurationItem> {
        val serializedCondition = when (this) {
            is SinkRule -> condition
            is SourceRule -> condition
            is SerializedRule.Cleaner -> condition
            is SerializedRule.PassThrough -> condition
        }

        val actions = when (this) {
            is SerializedRule.Source -> taint
            is SerializedRule.EntryPoint -> taint
            is SerializedRule.Cleaner -> cleans
            is SerializedRule.PassThrough -> copy
            is SerializedRule.MethodEntrySink,
            is SerializedRule.MethodExitSink,
            is SerializedRule.Sink -> emptyList()
        }

        val contexts = anyArgSpecializationContexts(method, serializedCondition, actions)
        return contexts.mapNotNull { resolveMethodRule(method, serializedCondition, it) }
    }

    private fun SerializedRule.resolveMethodRule(
        method: CIRFunction,
        serializedCondition: SerializedCondition?,
        ctx: AnyArgSpecializationCtx,
    ): TaintConfigurationItem? {
        val condition = serializedCondition.resolve(method, ctx).simplify()
        if (condition.isFalse()) return null

        return when (this) {
            is SerializedRule.EntryPoint -> {
                TaintEntryPointSource(method, condition, taint.flatMap { it.resolve(method, ctx) })
            }

            is SerializedRule.Source -> {
                TaintMethodSource(method, condition, taint.flatMap { it.resolve(method, ctx) })
            }

            is SerializedRule.Sink -> {
                TaintMethodSink(method, condition, ruleId(), meta())
            }

            is SerializedRule.MethodExitSink -> {
                TaintMethodExitSink(method, condition, ruleId(), meta())
            }

            is SerializedRule.MethodEntrySink -> {
                TaintMethodEntrySink(method, condition, ruleId(), meta())
            }

            is SerializedRule.PassThrough -> {
                TaintPassThrough(method, condition, copy.flatMap { it.resolve(method, ctx) })
            }

            is SerializedRule.Cleaner -> {
                TaintCleaner(method, condition, cleans.flatMap { it.resolve(method, ctx) })
            }
        }
    }

    private val ruleIdGen = AtomicInteger()

    private fun SinkRule.ruleId(): String {
        id?.let { return it }
        meta?.cwe?.firstOrNull()?.let { return "CWE-$it" }
        return "generated-id-${ruleIdGen.incrementAndGet()}"
    }

    private fun SinkRule.meta(): TaintSinkMeta = TaintSinkMeta(
        message = meta?.message() ?: "",
        severity = meta?.severity ?: CommonTaintConfigurationSinkMeta.Severity.Warning,
        cwe = meta?.cwe
    )

    private fun SinkMetaData.message(): String? = note

    private fun taintMark(name: String): TaintMark = taintMarks.getOrPut(name) { TaintMark(name) }

    data class AnyArgSpecializationCtx(val positions: Map<String, Argument>) {
        fun resolve(anyArg: PositionBase.AnyArgument): Argument =
            positions[anyArg.classifier]
                ?: error("Unresolved anyarg classifier")
    }

    private fun anyArgSpecializationContexts(
        method: CIRFunction, condition: SerializedCondition?, actions: List<SerializedAction>
    ): List<AnyArgSpecializationCtx> {
        val classifiers = hashSetOf<String>()
        condition.collectAnyArgumentClassifiers(classifiers)
        actions.forEach {
            when (it) {
                is SerializedTaintAssignAction -> it.pos.collectAnyArgumentClassifiers(classifiers)
                is SerializedTaintCleanAction -> it.pos.collectAnyArgumentClassifiers(classifiers)
                is SerializedTaintPassAction -> {
                    it.from.collectAnyArgumentClassifiers(classifiers)
                    it.to.collectAnyArgumentClassifiers(classifiers)
                }
            }
        }

        if (classifiers.isEmpty()) {
            return listOf(AnyArgSpecializationCtx(emptyMap()))
        }

        val contexts = mutableListOf<AnyArgSpecializationCtx>()
        val allArgs = method.parameters.indices.map { Argument(it) }
        buildAnyArgSpecializationCtx(classifiers.toList(), idx = 0, persistentHashMapOf(), allArgs, contexts)
        return contexts
    }

    private fun buildAnyArgSpecializationCtx(
        classifiers: List<String>,
        idx: Int,
        current: PersistentMap<String, Argument>,
        allArgs: List<Argument>,
        result: MutableList<AnyArgSpecializationCtx>
    ) {
        if (idx == classifiers.size) {
            result.add(AnyArgSpecializationCtx(current))
            return
        }

        val classifier = classifiers[idx]
        for (arg in allArgs) {
            val next = current.put(classifier, arg)
            buildAnyArgSpecializationCtx(classifiers, idx + 1, next, allArgs, result)
        }
    }

    private fun SerializedCondition?.collectAnyArgumentClassifiers(
        classifiers: MutableSet<String>
    ): Unit = when (this) {
        is SerializedCondition.And -> allOf.forEach { it.collectAnyArgumentClassifiers(classifiers) }
        is SerializedCondition.Or -> anyOf.forEach { it.collectAnyArgumentClassifiers(classifiers) }
        is SerializedCondition.Not -> not.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.AnnotationType -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ConstantCmp -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ConstantEq -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ConstantGt -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ConstantLt -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ConstantMatches -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ContainsMark -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.IsConstant -> isConstant.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.IsType -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ParamAnnotated -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ClassAnnotated,
        is SerializedCondition.MethodAnnotated,
        is SerializedCondition.MethodNameMatches,
        is SerializedCondition.ClassNameMatches,
        is SerializedCondition.NumberOfArgs,
        SerializedCondition.True,
        null -> {
            // no positions
        }
    }

    private fun PositionBaseWithModifiers.collectAnyArgumentClassifiers(classifiers: MutableSet<String>) {
        base.collectAnyArgumentClassifiers(classifiers)
    }

    private fun PositionBase.collectAnyArgumentClassifiers(classifiers: MutableSet<String>) {
        if (this !is PositionBase.AnyArgument) return
        classifiers.add(classifier)
    }

    private fun SerializedCondition?.resolve(
        method: CIRFunction,
        ctx: AnyArgSpecializationCtx,
    ): Condition = when (this) {
        null -> ConstantTrue
        is SerializedCondition.Not -> Not(not.resolve(method, ctx))
        is SerializedCondition.And -> mkAnd(allOf.map { it.resolve(method, ctx) })
        is SerializedCondition.Or -> mkOr(anyOf.map { it.resolve(method, ctx) })
        SerializedCondition.True -> ConstantTrue
        is SerializedCondition.AnnotationType -> {
            val containsAnnotation = pos.resolveWithAnnotationConstraint(
                method, ctx,
                annotatedWith.asAnnotationConstraint()
            ).any()
            containsAnnotation.asCondition()
        }

        is SerializedCondition.ConstantCmp -> {
            val value = when (value.type) {
                SerializedCondition.ConstantType.Str -> ConstantStringValue(value.value)
                SerializedCondition.ConstantType.Bool -> ConstantBooleanValue(value.value.toBoolean())
                SerializedCondition.ConstantType.Int -> ConstantIntValue(value.value.toInt())
            }

            pos.resolve(method, ctx).map {
                when (cmp) {
                    SerializedCondition.ConstantCmpType.Eq -> ConstantEq(it, value)
                    SerializedCondition.ConstantCmpType.Lt -> ConstantLt(it, value)
                    SerializedCondition.ConstantCmpType.Gt -> ConstantGt(it, value)
                }
            }.let { mkOr(it) }
        }

        is SerializedCondition.ConstantEq -> mkOr(
            pos.resolve(method, ctx).map { ConstantEq(it, ConstantStringValue(constantEq)) })

        is SerializedCondition.ConstantGt -> mkOr(
            pos.resolve(method, ctx).map { ConstantGt(it, ConstantStringValue(constantGt)) })

        is SerializedCondition.ConstantLt -> mkOr(
            pos.resolve(method, ctx).map { ConstantLt(it, ConstantStringValue(constantLt)) })

        is SerializedCondition.ConstantMatches -> mkOr(
            pos.resolve(method, ctx).map { ConstantMatches(it, patternManager.compilePattern(constantMatches)) })

        is SerializedCondition.IsConstant -> mkOr(isConstant.resolve(method, ctx).map { IsConstant(it) })

        is SerializedCondition.ContainsMark -> mkOr(
            pos.resolvePosition(method, ctx).map { ContainsMark(it, taintMark(tainted)) })

        is SerializedCondition.IsType -> resolveIsType(method, ctx)

        is SerializedCondition.NumberOfArgs -> {
            (method.parameters.size == numberOfArgs).asCondition()
        }

        is SerializedCondition.ClassAnnotated -> {
//            TODO
            mkFalse()
        }

        is SerializedCondition.MethodAnnotated -> {
            //            TODO
            mkFalse()
        }

        is SerializedCondition.ParamAnnotated -> {
            val containsAnnotation = pos.resolveWithAnnotationConstraint(method, ctx, annotation).any()
            containsAnnotation.asCondition()
        }

        is SerializedCondition.MethodNameMatches -> {
            patternManager.matchPattern(nameMatches, method.name).asCondition()
        }

        is SerializedCondition.ClassNameMatches -> {
            //            TODO
            mkFalse()
        }
    }

    private fun Boolean.asCondition(): Condition = if (this) mkTrue() else mkFalse()

    private fun SerializedCondition.IsType.resolveIsType(method: CIRFunction, ctx: AnyArgSpecializationCtx): Condition {
        val position = pos.resolve(method, ctx)
        if (position.isEmpty()) return mkFalse()

        val normalizedTypeIs = typeIs.normalizeAnyName()
        for (pos in position) {
            val posTypeName = when (pos) {
                is Argument -> method.parameters[pos.index].type.typeName
                Result -> method.returnType.typeName
                This -> ""
                is PositionWithAccess,
                is ClassStatic -> continue
            }

            if (normalizedTypeIs.match(posTypeName)) return mkTrue()
        }

        val matcher = normalizedTypeIs.toConditionNameMatcher(patternManager)
            ?: return mkTrue()

        return mkOr(position.map { TypeMatchesPattern(it, matcher) })
    }

    private fun SerializedTaintAssignAction.resolve(method: CIRFunction, ctx: AnyArgSpecializationCtx): List<AssignMark> =
        pos.resolvePositionWithAnnotationConstraint(method, ctx, annotatedWith?.asAnnotationConstraint())
            .map { AssignMark(taintMark(kind), it) }

    private fun SerializedTaintPassAction.resolve(method: CIRFunction, ctx: AnyArgSpecializationCtx): List<Action> =
        from.resolvePosition(method, ctx).flatMap { fromPos ->
            to.resolvePosition(method, ctx).map { toPos ->
                val taintKind = taintKind
                if (taintKind == null) {
                    CopyAllMarks(fromPos, toPos)
                } else {
                    CopyMark(taintMark(taintKind), fromPos, toPos)
                }
            }
        }

    private fun SerializedTaintCleanAction.resolve(method: CIRFunction, ctx: AnyArgSpecializationCtx): List<Action> =
        pos.resolvePosition(method, ctx).map { pos ->
            val taintKind = taintKind
            if (taintKind == null) {
                RemoveAllMarks(pos)
            } else {
                RemoveMark(taintMark(taintKind), pos)
            }
        }

    private fun PositionBaseWithModifiers.resolvePosition(
        method: CIRFunction,
        ctx: AnyArgSpecializationCtx,
    ): List<Position> = resolvePositionWithModifiers { it.resolve(method, ctx) }

    private fun PositionBaseWithModifiers.resolvePositionWithAnnotationConstraint(
        method: CIRFunction,
        ctx: AnyArgSpecializationCtx,
        annotation: AnnotationConstraint?
    ): List<Position> {
        if (annotation == null) return resolvePosition(method, ctx)
        return resolvePositionWithModifiers {
            it.resolveWithAnnotationConstraint(method, ctx, annotation)
        }
    }

    private inline fun PositionBaseWithModifiers.resolvePositionWithModifiers(
        resolveBase: (PositionBase) -> List<Position>
    ): List<Position> {
        val resolvedBase = resolveBase(base)
        return when (this) {
            is PositionBaseWithModifiers.BaseOnly -> resolvedBase
            is PositionBaseWithModifiers.WithModifiers -> {
                resolvedBase.map { b ->
                    modifiers.fold(b) { basePos, modifier ->
                        val accessor = when (modifier) {
                            PositionModifier.AnyField -> PositionAccessor.AnyFieldAccessor
                            PositionModifier.ArrayElement -> PositionAccessor.ElementAccessor
                            is PositionModifier.Field -> {
                                PositionAccessor.FieldAccessor(
                                    modifier.className,
                                    modifier.fieldName,
                                    modifier.fieldType
                                )
                            }
                        }
                        PositionWithAccess(basePos, accessor)
                    }
                }
            }
        }
    }

    private fun PositionBase.resolve(method: CIRFunction, ctx: AnyArgSpecializationCtx): List<Position> {
        when (this) {
            is PositionBase.AnyArgument -> return listOf(ctx.resolve(this))

            is PositionBase.Argument -> {
                val idx = idx
                if (idx != null) {
                    if (idx !in method.parameters.indices) return emptyList()
                    return listOf(Argument(idx))
                } else {
                    return method.parameters.map { Argument(it.index) }
                }
            }

            PositionBase.Result -> {
                if (method.classpath.findTypeOrNull(method.returnType) is CIRVoidType)
                    return emptyList()
                return listOf(Result)
            }

            PositionBase.This -> return emptyList()

            is PositionBase.ClassStatic -> return listOf(ClassStatic(className))
        }
    }

    private fun PositionBase.resolveWithAnnotationConstraint(
        method: CIRFunction,
        ctx: AnyArgSpecializationCtx,
        annotation: AnnotationConstraint
    ): List<Position> {
        val arguments = when (this) {
            is PositionBase.AnyArgument -> listOf(ctx.resolve(this))

            is PositionBase.Argument -> {
                val idx = idx
                if (idx != null) {
                    listOf(Argument(idx))
                } else {
                    method.parameters.map { Argument(it.index) }
                }
            }

            PositionBase.Result,
            PositionBase.This,
            is PositionBase.ClassStatic -> TODO("Annotation constraint on non-argument position")
        }

        return arguments.mapNotNull { arg ->
            val param = method.parameters.getOrNull(arg.index) ?: return@mapNotNull null
//            TODO
//            if (!param.annotations.matched(annotation)) return@mapNotNull null

            arg
        }
    }

    private fun SerializedNameMatcher.asAnnotationConstraint(): AnnotationConstraint =
        AnnotationConstraint(this, params = null)
}

fun <T> List<T>.adjustEmptyList(): List<T> = ifEmpty { Collections.emptyList() }
