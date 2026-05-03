package org.seqra.dataflow.cir.ap.ifds

import org.seqra.cir.graph.CApplicationGraph
import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRClasspathFeature
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.CIRFunctionAliasData
import org.seqra.ir.api.cir.CIRAliasGroup
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.cir.cfg.CIRBlockList
import org.seqra.ir.api.cir.cfg.CIRFuncOp
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRFunctionParameter
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.CIRGraph
import org.seqra.ir.api.cir.cfg.CIRInst
import org.seqra.ir.api.cir.cfg.CIRReturnOpInst
import org.seqra.ir.api.cir.cfg.CIRInstLocation
import org.seqra.ir.api.cir.cfg.MLIROpID
import org.seqra.ir.api.cir.cfg.MLIROpValue
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.cir.cfg.MLIRUnknownLoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CIRLocalAliasAnalysisTest {
    private val moduleId = MLIRModuleID("alias-test-mod")
    private val ptrTy = MLIRTypeID(moduleId, "ptr.i32")

    private class StubClasspath(
        private val moduleKey: String,
        private val aliasByFunction: Map<CIRFunctionID, CIRFunctionAliasData>,
    ) : CIRClasspath {
        override val db: CIRDatabase get() = error("unused")
        override val registeredLocations: List<RegisteredLocation> = emptyList()
        override val registeredLocationIds: Set<Long> = emptySet()
        override val features: List<CIRClasspathFeature> = emptyList()
        override val moduleNames: List<String> = listOf(moduleKey)
        override fun findFunctionOrNull(functionID: CIRFunctionID): CIRFunction? = null
        override fun findFunctionBySymbolName(symbolName: String): CIRFunction? = null
        override fun findTypeOrNull(typeID: MLIRTypeID): org.seqra.ir.api.cir.cfg.MLIRType? = null
        override fun findGlobalOrNull(globalID: CIRGlobalID) = null
        override fun getGlobalConstructors(): List<CIRFunctionID> = emptyList()
        override fun getGlobalDestructors(): List<CIRFunctionID> = emptyList()
        override fun close() = Unit
        override fun findFunctionAliasData(functionID: CIRFunctionID): CIRFunctionAliasData? = aliasByFunction[functionID]
    }

    private class StubFunction(
        override val id: CIRFunctionID,
        override val classpath: CIRClasspath,
    ) : CIRFunction {
        private val ret =
            CIRReturnOpInst(CIRInstLocation(this, 0, MLIRUnknownLoc), MLIROpID(0), emptyList())
        override val parameters: List<CIRFunctionParameter> get() = emptyList()
        override val returnType: MLIRTypeID get() = MLIRTypeID(id.moduleID, "void")
        override val blocks: CIRBlockList get() = CIRBlockList(emptyList())
        override val info: CIRFuncOp get() = error("unused")
        override val assignInstByLhv: Map<org.seqra.ir.api.cir.cfg.MLIRValue, org.seqra.ir.api.cir.cfg.CIRAssignInst>
            get() = emptyMap()
        override val allInstructions: List<CIRInst> get() = listOf(ret)
        override fun <T> withIRNode(body: (ByteArray?) -> T): T = body(null)
        override fun flowGraph(): CIRGraph =
            object : CIRGraph {
                override val function get() = this@StubFunction
                override val entry get() = ret
                override val instructions get() = allInstructions
                override val entries get() = listOf(ret)
                override val exits get() = listOf(ret)
                override fun successors(node: CIRInst) = emptySet<CIRInst>()
                override fun predecessors(node: CIRInst) = emptySet<CIRInst>()
                override fun throwers(node: CIRInst) = emptySet<CIRInst>()
                override fun catchers(node: CIRInst) = emptySet<CIRInst>()
            }
    }

    private class StubGraph(override val cp: CIRClasspath, private val fn: CIRFunction) : CApplicationGraph {
        override fun predecessors(node: CIRInst) = emptySequence<CIRInst>()
        override fun successors(node: CIRInst) = emptySequence<CIRInst>()
        override fun callees(node: CIRInst) = emptySequence<CIRFunction>()
        override fun callers(method: CIRFunction) = emptySequence<CIRInst>()
        override fun entryPoints(method: CIRFunction) = method.flowGraph().entries.asSequence()
        override fun exitPoints(method: CIRFunction) = method.flowGraph().exits.asSequence()
        override fun methodOf(node: CIRInst): CIRFunction = fn
        override fun statementsOf(method: CIRFunction) = method.allInstructions.asSequence()
    }

    @Test
    fun `findAliases exposes bases from classpath alias group`() {
        val fid = CIRFunctionID(moduleId, "f")
        val v0 = MLIROpValue(ptrTy, MLIROpID(10), 0)
        val v1 = MLIROpValue(ptrTy, MLIROpID(20), 0)
        val data =
            CIRFunctionAliasData(
                listOf(CIRAliasGroup(setOf(v0, v1))),
            )
        val cp = StubClasspath(moduleId.id, mapOf(fid to data))
        val fn = StubFunction(fid, cp)
        val graph = StubGraph(cp, fn)
        val entry = fn.flowGraph().entry
        val analysis = CIRLocalAliasAnalysis(entry, graph, CIRLanguageManager(cp))
        val b0 = AccessPathBase.LocalVar(10)
        val b1 = AccessPathBase.LocalVar(20)
        assertEquals(setOf(b0, b1), analysis.findAliases(b0))
        assertEquals(setOf(b0, b1), analysis.findAliases(b1))
        assertNull(analysis.findAliases(AccessPathBase.LocalVar(99)))
    }
}
