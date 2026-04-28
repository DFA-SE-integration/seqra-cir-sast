package org.seqra.cir.sast.dataflow

import org.seqra.cir.sast.dataflow.rules.TaintConfiguration
import org.seqra.dataflow.cir.ap.ifds.taint.CIRTaintRulesProvider
import org.seqra.dataflow.configuration.core.TaintConfigurationItem
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.cfg.CommonInst

class CIRTaintRulesProviderAdapter (
    private val taintConfiguration: TaintConfiguration
) : CIRTaintRulesProvider {
    override fun entryPointRulesForMethod(method: CommonMethod) = getRules(method) {
        taintConfiguration.entryPointForMethod(it)
    }

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst) = getRules(method) {
        taintConfiguration.sourceForMethod(it)
    }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst) = getRules(method) {
        taintConfiguration.sinkForMethod(it)
    }

    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst) = getRules(method) {
        taintConfiguration.passThroughForMethod(it)
    }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst) = getRules(method) {
        taintConfiguration.cleanerForMethod(it)
    }

    override fun sinkRulesForMethodEntry(method: CommonMethod) = getRules(method) {
        taintConfiguration.methodEntrySinkForMethod(it)
    }

    private inline fun <T : TaintConfigurationItem> getRules(
        method: CommonMethod,
        body: (CIRFunction) -> Iterable<T>
    ): Iterable<T> {
        check(method is CIRFunction) { "Expected method to be CIRFunction" }
        return body(method)
    }
}