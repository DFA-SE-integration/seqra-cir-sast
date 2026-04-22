package org.seqra.dataflow.cir.ap.ifds.taint

import org.seqra.dataflow.configuration.CommonTaintRulesProvider
import org.seqra.dataflow.configuration.core.TaintCleaner
import org.seqra.dataflow.configuration.core.TaintEntryPointSource
import org.seqra.dataflow.configuration.core.TaintMethodEntrySink
import org.seqra.dataflow.configuration.core.TaintMethodSource
import org.seqra.dataflow.configuration.core.TaintMethodSink
import org.seqra.dataflow.configuration.core.TaintPassThrough
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.cfg.CommonInst

interface CIRTaintRulesProvider : CommonTaintRulesProvider {
    fun entryPointRulesForMethod(method: CommonMethod): Iterable<TaintEntryPointSource>
    fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodSource>
    fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodSink>
    fun sinkRulesForMethodEntry(method: CommonMethod): Iterable<TaintMethodEntrySink>
    fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintPassThrough>
    fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst): Iterable<TaintCleaner>
}
