package org.seqra.dataflow.configuration.jvm

import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.jvm.JIRField
import org.seqra.dataflow.configuration.CommonTaintConfigurationItem
import org.seqra.dataflow.configuration.CommonTaintConfigurationSink
import org.seqra.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.seqra.dataflow.configuration.CommonTaintConfigurationSource

sealed interface TaintConfigurationItem: CommonTaintConfigurationItem

sealed interface TaintConfigurationSource : TaintConfigurationItem, CommonTaintConfigurationSource {
    val condition: Condition
    val actionsAfter: List<AssignMark>
}

data class TaintEntryPointSource(
    val method: CommonMethod,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
) : TaintConfigurationSource

data class TaintMethodSource(
    val method: CommonMethod,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
) : TaintConfigurationSource

data class TaintStaticFieldSource(
    val field: JIRField,
    override val condition: Condition,
    override val actionsAfter: List<AssignMark>,
) : TaintConfigurationSource

sealed interface TaintConfigurationSink : TaintConfigurationItem, CommonTaintConfigurationSink {
    val condition: Condition
}

data class TaintSinkMeta(
    override val message: String,
    override val severity: CommonTaintConfigurationSinkMeta.Severity,
    val cwe: List<Int>?
): CommonTaintConfigurationSinkMeta

data class TaintMethodSink(
    val method: CommonMethod,
    override val condition: Condition,
    override val id: String,
    override val meta: TaintSinkMeta,
) : TaintConfigurationSink

data class TaintMethodExitSink(
    val method: CommonMethod,
    override val condition: Condition,
    override val id: String,
    override val meta: CommonTaintConfigurationSinkMeta,
) : TaintConfigurationSink

data class TaintMethodEntrySink(
    val method: CommonMethod,
    override val condition: Condition,
    override val id: String,
    override val meta: CommonTaintConfigurationSinkMeta,
) : TaintConfigurationSink

data class TaintPassThrough(
    val method: CommonMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem

data class TaintCleaner(
    val method: CommonMethod,
    val condition: Condition,
    val actionsAfter: List<Action>,
) : TaintConfigurationItem
