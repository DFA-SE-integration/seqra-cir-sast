package org.seqra.dataflow.cir.ap.ifds

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.Accessor
import org.seqra.dataflow.ap.ifds.AnyAccessor
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.ExclusionSet
import org.seqra.dataflow.ap.ifds.FactTypeChecker
import org.seqra.dataflow.ap.ifds.FieldAccessor
import org.seqra.dataflow.ap.ifds.FinalAccessor
import org.seqra.dataflow.ap.ifds.TaintMarkAccessor
import org.seqra.dataflow.ap.ifds.access.FinalFactAp
import org.seqra.dataflow.ap.ifds.access.InitialFactAp
import org.seqra.ir.api.cir.CIRClasspath
import org.seqra.ir.api.cir.CIRClasspathFeature
import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.RegisteredLocation
import org.seqra.ir.api.cir.cfg.CIRArrayType
import org.seqra.ir.api.cir.cfg.CIRBoolType
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.CIRIntType
import org.seqra.ir.api.cir.cfg.CIRPointerType
import org.seqra.ir.api.cir.cfg.CIRRecordKind
import org.seqra.ir.api.cir.cfg.CIRStructType
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIRStringAttr
import org.seqra.ir.api.cir.cfg.MLIRType
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class CIRFactTypeCheckerTest {
    private val moduleId = MLIRModuleID("test-mod")
    private val boolTy = MLIRTypeID(moduleId, "bool")
    private val intTy = MLIRTypeID(moduleId, "i32")
    private val structTy = MLIRTypeID(moduleId, "struct.S")
    private val ptrStructTy = MLIRTypeID(moduleId, "ptr.struct.S")
    private val arrayTy = MLIRTypeID(moduleId, "array.i32")
    private val ptrArrayTy = MLIRTypeID(moduleId, "ptr.array.i32")
    private val structArrayTy = MLIRTypeID(moduleId, "array.struct.S")
    private val ptrStructArrayTy = MLIRTypeID(moduleId, "ptr.array.struct.S")

    private inner class StubClasspath(
        private val types: MutableMap<MLIRTypeID, MLIRType> = linkedMapOf(),
        private val availableModuleNames: List<String> = listOf(moduleId.id),
    ) : CIRClasspath {
        override val db: CIRDatabase get() = error("unused")
        override val registeredLocations: List<RegisteredLocation> = emptyList()
        override val registeredLocationIds: Set<Long> = emptySet()
        override val features: List<CIRClasspathFeature> = emptyList()
        override val moduleNames: List<String> = availableModuleNames

        override fun findFunctionOrNull(functionID: CIRFunctionID): CIRFunction? = null
        override fun findFunctionBySymbolName(symbolName: String): CIRFunction? = null
        override fun findTypeOrNull(typeID: MLIRTypeID): MLIRType? = types[typeID]
        override fun findGlobalOrNull(globalID: CIRGlobalID) = null
        override fun getGlobalConstructors(): List<CIRFunctionID> = emptyList()
        override fun getGlobalDestructors(): List<CIRFunctionID> = emptyList()
        override fun close() = Unit

        fun register(type: MLIRType) {
            types[type.id] = type
        }
    }

    private data class StubFinalFactAp(
        override val base: AccessPathBase = AccessPathBase.Argument(0),
        val accessors: List<Accessor>,
        override val exclusions: ExclusionSet = ExclusionSet.Empty,
    ) : FinalFactAp {
        override val size: Int = accessors.size

        override fun rebase(newBase: AccessPathBase): FinalFactAp = copy(base = newBase)
        override fun exclude(accessor: Accessor): FinalFactAp = this
        override fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp = copy(exclusions = exclusions)
        override fun isAbstract(): Boolean = false
        override fun startsWithAccessor(accessor: Accessor): Boolean = accessors.firstOrNull() == accessor
        override fun readAccessor(accessor: Accessor): FinalFactAp? = null
        override fun prependAccessor(accessor: Accessor): FinalFactAp = copy(accessors = listOf(accessor) + accessors)
        override fun clearAccessor(accessor: Accessor): FinalFactAp? = this
        override fun removeAbstraction(): FinalFactAp? = this
        override fun delta(other: InitialFactAp): List<FinalFactAp.Delta> = emptyList()
        override fun concat(typeChecker: FactTypeChecker, delta: FinalFactAp.Delta): FinalFactAp? = this

        override fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp? {
            var currentFilter = filter
            for (accessor in accessors) {
                when (val result = currentFilter.check(accessor)) {
                    FactTypeChecker.FilterResult.Accept -> return this
                    FactTypeChecker.FilterResult.Reject -> return null
                    is FactTypeChecker.FilterResult.FilterNext -> currentFilter = result.filter
                }
            }
            return this
        }

        override fun contains(factAp: InitialFactAp): Boolean = false
    }

    private data class StrictStubFinalFactAp(
        override val base: AccessPathBase = AccessPathBase.Argument(0),
        val accessors: List<Accessor>,
        override val exclusions: ExclusionSet = ExclusionSet.Empty,
    ) : FinalFactAp {
        override val size: Int = accessors.size

        override fun rebase(newBase: AccessPathBase): FinalFactAp = copy(base = newBase)
        override fun exclude(accessor: Accessor): FinalFactAp = this
        override fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp = copy(exclusions = exclusions)
        override fun isAbstract(): Boolean = false
        override fun startsWithAccessor(accessor: Accessor): Boolean = accessors.firstOrNull() == accessor
        override fun readAccessor(accessor: Accessor): FinalFactAp? = null
        override fun prependAccessor(accessor: Accessor): FinalFactAp = copy(accessors = listOf(accessor) + accessors)
        override fun clearAccessor(accessor: Accessor): FinalFactAp? = this
        override fun removeAbstraction(): FinalFactAp? = this
        override fun delta(other: InitialFactAp): List<FinalFactAp.Delta> = emptyList()
        override fun concat(typeChecker: FactTypeChecker, delta: FinalFactAp.Delta): FinalFactAp? = this

        override fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp? {
            var currentFilter = filter
            for ((index, accessor) in accessors.withIndex()) {
                when (val result = currentFilter.check(accessor)) {
                    FactTypeChecker.FilterResult.Reject -> return null
                    FactTypeChecker.FilterResult.Accept -> {
                        return if (index == accessors.lastIndex) this else null
                    }

                    is FactTypeChecker.FilterResult.FilterNext -> currentFilter = result.filter
                }
            }
            return this
        }

        override fun contains(factAp: InitialFactAp): Boolean = false
    }

    private fun checker(): CIRFactTypeChecker {
        val cp = StubClasspath().apply {
            register(CIRBoolType(boolTy))
            register(CIRIntType(intTy, width = 32, isSigned = true))
            register(
                CIRStructType(
                    id = structTy,
                    members = listOf(boolTy, ptrArrayTy),
                    name = MLIRStringAttr("S", null),
                    incomplete = false,
                    packed = false,
                    kind = CIRRecordKind.Struct,
                )
            )
            register(CIRPointerType(ptrStructTy, structTy, null))
            register(CIRArrayType(arrayTy, intTy, 4))
            register(CIRPointerType(ptrArrayTy, arrayTy, null))
            register(CIRArrayType(structArrayTy, structTy, 2))
            register(CIRPointerType(ptrStructArrayTy, structArrayTy, null))
        }
        return CIRFactTypeChecker(cp)
    }

    @Test
    fun `filterFactByLocalType keeps original fact when type is null`() {
        val checker = checker()
        val fact = StubFinalFactAp(accessors = listOf(FieldAccessor("S", "value", boolTy.id)))

        val result = checker.filterFactByLocalType(null, fact)

        assertSame(fact, result)
    }

    @Test
    fun `filterFactByLocalType rejects field accessor for scalar type`() {
        val checker = checker()
        val fact = StubFinalFactAp(accessors = listOf(FieldAccessor("S", "value", boolTy.id)))

        val result = checker.filterFactByLocalType(CIRBoolType(boolTy), fact)

        assertNull(result)
    }

    @Test
    fun `filterFactByLocalType accepts field accessor for pointer to struct`() {
        val checker = checker()
        val fact = StubFinalFactAp(
            accessors = listOf(FieldAccessor("S", "value", CIRFieldTypeEncoding.encode(boolTy)))
        )

        val result = checker.filterFactByLocalType(CIRPointerType(ptrStructTy, structTy, null), fact)

        assertNotNull(result)
    }

    @Test
    fun `filterFactByLocalType validates nested element access through array field`() {
        val checker = checker()
        val fact = StubFinalFactAp(
            accessors = listOf(
                FieldAccessor("S", "items", CIRFieldTypeEncoding.encode(ptrArrayTy)),
                ElementAccessor,
            )
        )
        val actualStructType = CIRStructType(
            structTy,
            members = listOf(boolTy, ptrArrayTy),
            name = MLIRStringAttr("S", null),
            incomplete = false,
            packed = false,
            kind = CIRRecordKind.Struct,
        )

        val result = checker.filterFactByLocalType(actualStructType, fact)

        assertNotNull(result)
    }

    @Test
    fun `filterFactByLocalType rejects nested field access after scalar array element`() {
        val checker = checker()
        val fact = StubFinalFactAp(
            accessors = listOf(
                ElementAccessor,
                FieldAccessor("S", "value", boolTy.id),
            )
        )

        val result = checker.filterFactByLocalType(CIRPointerType(ptrArrayTy, arrayTy, null), fact)

        assertNull(result)
    }

    @Test
    fun `accessPathFilter requires resolved field type for nested continuation chain`() {
        val checker = checker()
        val fact = StrictStubFinalFactAp(
            accessors = listOf(
                ElementAccessor,
                FieldAccessor("Nested", "value", CIRFieldTypeEncoding.encode(boolTy)),
            )
        )
        val encodedFieldType = CIRFieldTypeEncoding.encode(ptrStructArrayTy)

        val result = fact.filterFact(
            checker.accessPathFilter(
                listOf(FieldAccessor("S", "items", encodedFieldType))
            )
        )

        assertNotNull(result)
    }

    @Test
    fun `accessPathFilter rejects nested continuation when field type cannot be resolved`() {
        val checker = checker()
        val fact = StrictStubFinalFactAp(
            accessors = listOf(
                ElementAccessor,
                FieldAccessor("Nested", "value", CIRFieldTypeEncoding.encode(boolTy)),
            )
        )

        val result = fact.filterFact(
            checker.accessPathFilter(
                listOf(FieldAccessor("S", "items", "unknown.type"))
            )
        )

        assertNull(result)
    }

    @Test
    fun `field type encoding roundtrips MLIRTypeID`() {
        val typeId = MLIRTypeID(MLIRModuleID("module-a"), "!cir.ptr<!record>")

        val encoded = CIRFieldTypeEncoding.encode(typeId)
        val decoded = CIRFieldTypeEncoding.decodeOrNull(encoded)

        assertEquals(typeId, decoded)
    }

    @Test
    fun `decodeOrNull returns null for malformed encoded field type`() {
        assertNull(CIRFieldTypeEncoding.decodeOrNull("@cir-type:v1:bad"))
        assertNull(CIRFieldTypeEncoding.decodeOrNull("plain-type"))
    }

    @Test
    fun `filterFactByLocalType prefers encoded exact MLIRTypeID over ambiguous type string`() {
        val moduleA = MLIRModuleID("module-a")
        val moduleB = MLIRModuleID("module-b")
        val ambiguousTypeName = "dup.type"
        val ownerStructId = MLIRTypeID(moduleA, "owner.struct")
        val scalarTypeId = MLIRTypeID(moduleA, ambiguousTypeName)
        val nestedStructId = MLIRTypeID(moduleB, ambiguousTypeName)

        val cp = StubClasspath(availableModuleNames = listOf(moduleA.id, moduleB.id)).apply {
            register(
                CIRStructType(
                    id = ownerStructId,
                    members = listOf(nestedStructId),
                    name = MLIRStringAttr("Owner", null),
                    incomplete = false,
                    packed = false,
                    kind = CIRRecordKind.Struct,
                )
            )
            register(CIRBoolType(scalarTypeId))
            register(
                CIRStructType(
                    id = nestedStructId,
                    members = listOf(boolTy),
                    name = MLIRStringAttr("Nested", null),
                    incomplete = false,
                    packed = false,
                    kind = CIRRecordKind.Struct,
                )
            )
            register(CIRBoolType(boolTy))
        }
        val checker = CIRFactTypeChecker(cp)
        val encodedFieldType = CIRFieldTypeEncoding.encode(nestedStructId)
        val fact = StubFinalFactAp(
            accessors = listOf(
                FieldAccessor("Owner", "nested", encodedFieldType),
                FieldAccessor("Nested", "value", CIRFieldTypeEncoding.encode(boolTy)),
            )
        )
        val actualOwnerType = CIRStructType(
            ownerStructId,
            members = listOf(nestedStructId),
            name = MLIRStringAttr("Owner", null),
            incomplete = false,
            packed = false,
            kind = CIRRecordKind.Struct,
        )

        val result = checker.filterFactByLocalType(
            actualOwnerType,
            fact,
        )

        assertNotNull(result)
    }

    @Test
    fun `filterFactByLocalType rejects malformed encoded field type for terminal field access`() {
        val checker = checker()
        val fact = StubFinalFactAp(accessors = listOf(FieldAccessor("S", "value", "@cir-type:v1:bad")))

        val result = checker.filterFactByLocalType(
            CIRStructType(structTy, emptyList(), MLIRStringAttr("S", null), false, false, CIRRecordKind.Struct),
            fact,
        )

        assertNull(result)
    }

    @Test
    fun `filterFactByLocalType rejects encoded field type that is absent from struct members`() {
        val checker = checker()
        val fact = StubFinalFactAp(
            accessors = listOf(FieldAccessor("S", "value", CIRFieldTypeEncoding.encode(ptrStructArrayTy)))
        )

        val result = checker.filterFactByLocalType(
            CIRStructType(structTy, listOf(boolTy, ptrArrayTy), MLIRStringAttr("S", null), false, false, CIRRecordKind.Struct),
            fact,
        )

        assertNull(result)
    }

    @Test
    fun `accessPathFilter always accepts taint final and any accessors`() {
        val checker = checker()
        val fact = StubFinalFactAp(
            accessors = listOf(
                TaintMarkAccessor("source"),
                FinalAccessor,
                AnyAccessor,
            )
        )

        val result = checker.filterFactByLocalType(CIRStructType(structTy, emptyList(), MLIRStringAttr("S", null), false, false, CIRRecordKind.Struct), fact)

        assertNotNull(result)
    }
}
