package org.seqra.semgrep.pattern.conversion

import org.seqra.semgrep.pattern.SemgrepErrorEntry
import org.seqra.semgrep.pattern.SemgrepJavaPattern
import org.seqra.semgrep.pattern.SemgrepJavaPatternParser
import org.seqra.semgrep.pattern.SemgrepJavaPatternParsingResult
import org.seqra.semgrep.pattern.SemgrepRuleLoadStepTrace
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

interface SemgrepPatternParser {
    fun parseOrNull(
        pattern: String,
        semgrepTrace: SemgrepRuleLoadStepTrace,
    ): SemgrepJavaPattern?

    fun cached() = CachedSemgrepPatternParser(this)

    companion object {
        fun create(): SemgrepPatternParser = DefaultSemgrepPatternParser()
    }
}

class DefaultSemgrepPatternParser(
    private val parser: SemgrepJavaPatternParser = SemgrepJavaPatternParser()
) : SemgrepPatternParser {
    override fun parseOrNull(
        pattern: String,
        semgrepTrace: SemgrepRuleLoadStepTrace,
    ): SemgrepJavaPattern? {
        return when (val result = parser.parseSemgrepJavaPattern(pattern)) {
            is SemgrepJavaPatternParsingResult.FailedASTParsing -> {
                semgrepTrace.error(
                    "Pattern parsing AST failed with errors:\n${result.errorMessages.joinToString("\n")}",
                    SemgrepErrorEntry.Reason.ERROR,
                )
                null
            }

            is SemgrepJavaPatternParsingResult.Ok -> {
                result.pattern
            }

            is SemgrepJavaPatternParsingResult.ParserFailure -> {
                semgrepTrace.error(
                    "Pattern parsing failed: ${result.exception.message}, ${result.exception.element.text}",
                    SemgrepErrorEntry.Reason.ERROR,
                )
                null
            }

            is SemgrepJavaPatternParsingResult.OtherFailure -> {
                semgrepTrace.error(
                    "Pattern parsing failed: ${result.exception.message}",
                    SemgrepErrorEntry.Reason.ERROR,
                )
                null
            }
        }
    }
}

class CachedSemgrepPatternParser(
    private val parser: SemgrepPatternParser,
) : SemgrepPatternParser {
    private val cache = ConcurrentHashMap<String, Optional<SemgrepJavaPattern>>()

    override fun parseOrNull(
        pattern: String,
        semgrepTrace: SemgrepRuleLoadStepTrace,
    ): SemgrepJavaPattern? =
        cache.computeIfAbsent(pattern) {
            Optional.ofNullable(parser.parseOrNull(pattern, semgrepTrace))
        }.getOrNull()
}
