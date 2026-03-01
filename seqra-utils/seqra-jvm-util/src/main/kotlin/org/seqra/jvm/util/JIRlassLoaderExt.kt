package org.seqra.jvm.util

import org.seqra.ir.api.jvm.JIRClassOrInterface

interface JIRlassLoaderExt {
    fun loadClass(jIRClass: JIRClassOrInterface, initialize: Boolean = true): Class<*>
}
