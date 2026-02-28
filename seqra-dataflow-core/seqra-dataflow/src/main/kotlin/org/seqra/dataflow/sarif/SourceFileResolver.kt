package org.seqra.dataflow.sarif

import org.seqra.ir.api.common.cfg.CommonInst

interface SourceFileResolver<in Statement : CommonInst> {
    fun resolveByName(inst: Statement, pkg: String, name: String): String?

    fun resolveByInst(inst: Statement): String?
}
