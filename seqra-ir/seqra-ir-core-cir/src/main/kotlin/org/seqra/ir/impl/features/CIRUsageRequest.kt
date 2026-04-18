package org.seqra.ir.impl.features

import org.seqra.ir.api.cir.CIRFunctionSource

data class CIRUsageFeatureRequest(
    val calleeSymbols: Set<String>,
)

class CIRUsageFeatureResponse(
    val source: CIRFunctionSource,
    val offsets: ShortArray,
)
