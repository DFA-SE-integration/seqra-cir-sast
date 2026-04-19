package org.seqra.dataflow.jvm.ap.ifds.taint

import org.seqra.dataflow.configuration.core.TaintConfigurationItem


fun interface TaintRuleFilter {
    fun ruleEnabled(rule: TaintConfigurationItem): Boolean
}
