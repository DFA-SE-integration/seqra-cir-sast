package org.seqra.ir.api.cir

import org.seqra.ir.api.cir.cfg.CIRFunction

interface CIRLookup<Function : CIRFunction> {
    /**
     * Lookup for method based on name and description:
     * - in current class search for private methods too
     * - in parent classes and interfaces search only for visible methods
     *
     * @param name method name
     * @param description jvm description of method
     */
    fun method(name: String, description: String): Function?
}