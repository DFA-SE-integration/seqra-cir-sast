package org.seqra.cir.sast.dataflow.rules

import org.seqra.dataflow.configuration.core.serialized.SerializedRule
import org.seqra.dataflow.configuration.core.serialized.SerializedNameMatcher.ClassPattern
import org.seqra.dataflow.configuration.core.serialized.SerializedNameMatcher.Pattern
import org.seqra.dataflow.configuration.core.serialized.SerializedNameMatcher.Simple
import org.seqra.ir.api.cir.cfg.CIRFunction

class MethodTaintRulesStorage<S : SerializedRule> private constructor(
    private val concreteMethodNameRules: MutableMap<String, MethodClassTaintRulesStorage<S>>,
    private val patternMethodRules: Map<Regex, Array<SerializedRule>>,
    private val anyMethodRules: MethodClassTaintRulesStorage<S>?,
) {
    private val methodNameWithoutConcreteRules = hashSetOf<String>()

    fun findRules(rules: MutableList<S>, method: CIRFunction) {
        anyMethodRules?.findRules(rules, method)

        val concreteRules = concreteMethodNameRules[method.name]
        if (concreteRules != null) {
            concreteRules.findRules(rules, method)
            return
        }

        if (method.name in methodNameWithoutConcreteRules) {
            return
        }

        val builder = MethodClassTaintRulesStorage.Builder<S>()
        resolvePatterns(patternMethodRules, method.name, builder)
        val storage = builder.build()

        if (storage == null) {
            methodNameWithoutConcreteRules.add(method.name)
            return
        }

        concreteMethodNameRules[method.name] = storage
        storage.findRules(rules, method)
    }

    class Builder<S : SerializedRule>(
        private val patternManager: PatternManager
    ) {
        private val rules = mutableListOf<S>()

        fun addRules(rules: List<S>) {
            this.rules.addAll(rules)
        }

        fun build(): MethodTaintRulesStorage<S> {
            val concreteMethodNameRules = hashMapOf<String, MethodClassTaintRulesStorage.Builder<S>>()
            val anyMethodRules = MethodClassTaintRulesStorage.Builder<S>()
            val patternMethodRules = hashMapOf<String, MutableSet<S>>()

            for (rule in rules) {
                when (val fName = rule.function.name.normalizeAnyName()) {
                    is ClassPattern -> error("impossible")
                    is Simple -> {
                        concreteMethodNameRules.getOrPut(fName.value) {
                            MethodClassTaintRulesStorage.Builder()
                        }.addRule(rule)
                    }

                    is Pattern -> {
                        if (fName.isAny()) {
                            anyMethodRules.addRule(rule)
                        } else {
                            patternMethodRules.getOrPut(fName.pattern, ::hashSetOf).add(rule)
                        }
                    }
                }
            }

            val compiledPatternMethodRules = patternMethodRules
                .mapKeys { patternManager.compilePattern(it.key) }
                .mapValuesTo(hashMapOf()) { it.value.toTypedArray<SerializedRule>() }


            val concreteRules = hashMapOf<String, MethodClassTaintRulesStorage<S>>()
            for ((methodName, builder) in concreteMethodNameRules) {
                resolvePatterns(compiledPatternMethodRules, methodName, builder)
                concreteRules[methodName] = builder.build() ?: continue
            }

            return MethodTaintRulesStorage(
                concreteRules,
                compiledPatternMethodRules,
                anyMethodRules.build()
            )
        }
    }

    companion object {
        private fun <S : SerializedRule> resolvePatterns(
            patterns: Map<Regex, Array<SerializedRule>>,
            methodName: String,
            builder: MethodClassTaintRulesStorage.Builder<S>,
        ) {
            for ((pattern, rules) in patterns) {
                if (pattern.containsMatchIn(methodName)) {
                    for (rule in rules) {
                        @Suppress("UNCHECKED_CAST")
                        builder.addRule(rule as S)
                    }
                }
            }
        }
    }
}

/**
 * Просто контейнер для правил метода
 */
private class MethodClassTaintRulesStorage<S : SerializedRule> private constructor(
    private val anyRules: Array<S>,
) {

    fun findRules(dst: MutableList<S>, method: CIRFunction) {
//        TODO: Omit cause we work with C, without supertypes of enclosing class,
//          No need to propagate delayed rules
//        pushDelayedRules()

        dst.addAll(anyRules)

//        findRules(dst, method.enclosingClass.name)
//        method.enclosingClass.allSuperHierarchy.forEach { cls ->
//            val overrideRules = mutableListOf<S>()
//            findRules(overrideRules, cls.name)
//            overrideRules.removeAll { !it.overrides }
//            dst.addAll(overrideRules)
//        }

//        hierarchyInfo.forEachSubClassName(method.enclosingClass.name) { className ->
//            findRules(dst, className)
//        }
    }

    class Builder<S : SerializedRule> {
        private val rules = mutableListOf<S>()
        fun addRule(rule: S) {
            rules.add(rule)
        }

        fun build(): MethodClassTaintRulesStorage<S>? {
            if (rules.isEmpty()) return null

            return MethodClassTaintRulesStorage(
                rules.toRuleArray()
            )
        }
    }

    companion object {
        private fun <S : SerializedRule> Collection<S>.toRuleArray(): Array<S> {
            @Suppress("UNCHECKED_CAST")
            return toTypedArray<SerializedRule>() as Array<S>
        }
    }
}