package org.seqra.cir.sast.dataflow.rules

/**
 * String to regex, and vice versa
 */
class PatternManager {
    private val compiledMatchers = hashMapOf<String, Regex>()

    fun compilePattern(pattern: String): Regex =
        compiledMatchers.getOrPut(pattern) { pattern.toRegex() }

    fun matchPattern(pattern: String, str: String): Boolean =
        compilePattern(pattern).containsMatchIn(str)
}