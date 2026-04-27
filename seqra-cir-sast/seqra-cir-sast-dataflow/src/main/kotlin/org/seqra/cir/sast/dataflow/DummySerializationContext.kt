package org.seqra.cir.sast.dataflow

import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.seqra.ir.api.common.CommonMethod

/**
 * Mock для SummarySerializationContext
 */
class DummySerializationContext : SummarySerializationContext {
    override fun getIdByMethod(method: CommonMethod) = error("Should not be called")

    override fun getIdByAccessor(accessor: Accessor) = error("Should not be called")

    override fun getMethodById(id: Long) = error("Should not be called")

    override fun getAccessorById(id: Long) = error("Should not be called")

    override fun loadSummaries(method: CommonMethod): ByteArray? = null

    override fun storeSummaries(method: CommonMethod, summaries: ByteArray) = error("Should not be called")

    override fun flush() = error("Should not be called")
}