package org.seqra.ir.impl.storage.ers

import mu.KLogging
import org.seqra.ir.api.cir.*
import org.seqra.ir.api.cir.cfg.*
import org.seqra.ir.api.storage.ers.*
import org.seqra.ir.impl.sources.*
import org.seqra.ir.impl.storage.BytecodeLocationEntity
import org.seqra.ir.impl.storage.PersistentByteCodeLocation
import org.seqra.ir.impl.storage.PersistentByteCodeLocationData
import org.seqra.ir.impl.types.FunctionKind
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val logger = object : KLogging() {}.logger

class CIRErsPersistenceImpl(private var ers: EntityRelationshipStorage, private val sourceLoader: CIRSourceLoader) :
    CIRDatabasePersistence {
    private val lock = ReentrantLock()

    private fun CIRFunctionID.asConsolidatedID() = moduleID.id + "#" + id
    private fun MLIRTypeID.asConsolidatedID() = moduleID.id + "#" + id
    private fun CIRGlobalID.asConsolidatedID() = moduleID.id + "#" + id

    override fun <T> read(action: (Transaction) -> T): T {
        return if (ers.isInRam) { // RAM storage doesn't support explicit readonly transactions
            ers.transactionalOptimistic(attempts = 10) { txn ->
                action(txn)
            }
        } else {
            ers.transactional(readonly = true) { txn ->
                action(txn)
            }
        }
    }

    override fun <T> write(action: (Transaction) -> T): T = lock.withLock {
        ers.transactional { txn ->
            action(txn)
        }
    }

    override fun persist(location: RegisteredLocation, modules: List<CIRModuleSource>) {
        if (modules.isEmpty()) {
            return
        }
        val locationId = location.id
        val moduleEntities = hashMapOf<MLIRModuleID, Entity>()

        write { txn ->
            modules.forEach { source ->
                val moduleInfo = (source.node as ModuleIRNode).asModuleInfo()
                txn.newEntity(PersistenceEntity.ENTITY_MODULE).also { module ->
                    moduleEntities[moduleInfo.id] = module
                    module[PersistenceEntity.Module.ID] = moduleInfo.id.id

                    source.aliasData?.takeIf { it.isNotEmpty() }?.let { blob ->
                        module.setRawBlob(PersistenceEntity.Module.ALIAS_DATA, blob)
                    }

                    // Set up information about global constructors
                    // TODO: handle all attributes
                    val globalCtors = links(module, "globalCtors")
                    val globalCtorsArray =
                        moduleInfo.attributes.singleOrNull { it.name.value == "cir.global_ctors" }?.value as MLIRArrayAttr?

                    globalCtorsArray?.let { array ->
                        array.value.filterIsInstance<CIRGlobalCtorAttr>().forEach { globalCtor ->
                            val globalCtorEntity = txn.newEntity(PersistenceEntity.Module.ENTITY_GLOBAL_CTOR)
                            globalCtorEntity["nameId"] = globalCtor.name.value
                            globalCtors += globalCtorEntity
                        }
                    }

                    val globalDtors = links(module, "globalDtors")
                    val globalDtorsArray =
                        moduleInfo.attributes.singleOrNull { it.name.value == "cir.global_dtors" }?.value as MLIRArrayAttr?

                    globalDtorsArray?.let { array ->
                        array.value.filterIsInstance<CIRGlobalDtorAttr>().forEach { globalDtor ->
                            val globalDtorEntity = txn.newEntity(PersistenceEntity.Module.ENTITY_GLOBAL_DTOR)
                            globalDtorEntity["nameId"] = globalDtor.name
                            globalDtors += globalDtorEntity
                        }
                    }

                    val types = links(module, "types")
                    moduleInfo.types.forEach { typeInfo ->
                        txn.newEntity(PersistenceEntity.ENTITY_TYPE).also { type ->
                            types += type

                            // Identification information to determine if the module is in the classpath
                            type["ownerId"] = location.id

                            // Identification
                            type[PersistenceEntity.Type.NAME] = typeInfo.id.id
                            type[PersistenceEntity.Function.MODULE] = typeInfo.id.moduleID.id
                            type[PersistenceEntity.Function.CONSOLIDATED_NAME] = typeInfo.id.asConsolidatedID()

                            // Type content
                            type.setRawBlob(PersistenceEntity.Type.BYTECODE, typeInfo.bytecodeNode.byteBuffer)
                        }
                    }

                    val functions = links(module, "functions")
                    moduleInfo.functions.forEach { functionInfo ->
                        txn.newEntity(PersistenceEntity.ENTITY_FUNCTION).also { function ->
                            functions += function

                            // Identification information to determine if the module is in the classpath
                            function["ownerId"] = location.id

                            // Identification information
                            function[PersistenceEntity.Function.MODULE] = functionInfo.id.moduleID.id
                            function[PersistenceEntity.Function.NAME] = functionInfo.id.id
                            function[PersistenceEntity.Function.CONSOLIDATED_NAME] = functionInfo.id.asConsolidatedID()

                            // Function attributes
                            function[PersistenceEntity.Function.TYPE] = functionInfo.info.functionType.value.id
                            function[PersistenceEntity.Function.DEF_OR_DECL] = functionInfo.definitionOrDeclaration
                            function[PersistenceEntity.Function.LINKAGE] = functionInfo.info.linkage

                            // Loads bytecode of the entire function
                            function.setRawBlob(PersistenceEntity.Function.INFO, functionInfo.infoNode.byteBuffer)
                            function.setRawBlob(PersistenceEntity.Function.BYTECODE, functionInfo.blocksNode)
                        }
                    }

                    val globals = links(module, "globals")
                    moduleInfo.globals.forEach { globalInfo ->
                        txn.newEntity(PersistenceEntity.ENTITY_GLOBAL).also { global ->
                            globals += global

                            global["ownerId"] = location.id

                            // Identification
                            global[PersistenceEntity.Global.NAME] = globalInfo.id.id
                            global[PersistenceEntity.Global.MODULE] = globalInfo.id.moduleID.id

                            global[PersistenceEntity.Global.CONSOLIDATED_NAME] = globalInfo.id.asConsolidatedID()

                            // Attributes
                            global[PersistenceEntity.Global.LINKAGE] = globalInfo.info.linkage

                            // Bytecode
                            global.setRawBlob(PersistenceEntity.Global.BYTECODE, globalInfo.bytecodeNode.byteBuffer)
                        }
                    }
                }
            }
            modules.forEach { source ->
                val moduleInfo = (source.node as ModuleIRNode).asModuleInfo()
                moduleEntities[moduleInfo.id]?.let { module ->
                    module[PersistenceEntity.Module.LOCATION_ID] = locationId
                }
            }
//            symbolInterner.flush(context)
        }
    }

    override fun findFunctionSources(classpath: CIRClasspath, functionID: CIRFunctionID): List<CIRFunctionSource> {
        return read { txn ->
            val functions = txn.find(
                PersistenceEntity.ENTITY_FUNCTION,
                PersistenceEntity.Function.CONSOLIDATED_NAME,
                functionID.asConsolidatedID()
            ).filter {
                (it["ownerId"] in classpath.registeredLocationIds)
            }.map {
                it.toFunctionSource(classpath, functionID, sourceLoader)
            }.toList()

            functions
        }
    }

    override fun findTypeSources(classpath: CIRClasspath, typeID: MLIRTypeID): List<CIRTypeSource> {
        return read { txn ->
            txn.find(
                PersistenceEntity.ENTITY_TYPE, PersistenceEntity.Function.CONSOLIDATED_NAME, typeID.asConsolidatedID()
            ).filter { (it["ownerId"] in classpath.registeredLocationIds) }
                .map { it.toTypeSource(classpath.db, sourceLoader) }.toList()
        }
    }

    override fun findGlobalCtors(classpath: CIRClasspath): List<CIRFunctionID> {
        return read { txn ->
            classpath.moduleNames.flatMap { moduleName ->
                val moduleEntity = txn.find(PersistenceEntity.ENTITY_MODULE, PersistenceEntity.Module.ID, moduleName)
                moduleEntity.firstOrNull()?.getLinks("globalCtors")
                    ?.map { CIRFunctionID(MLIRModuleID(moduleName), it.get<String>("nameId")!!) }.orEmpty()
            }
        }
    }

    override fun findGlobalDtors(classpath: CIRClasspath): List<CIRFunctionID> {
        return read { txn ->
            classpath.moduleNames.flatMap { moduleName ->
                val moduleEntity = txn.find(PersistenceEntity.ENTITY_MODULE, PersistenceEntity.Module.ID, moduleName)
                moduleEntity.firstOrNull()?.getLinks("globalDtors")
                    ?.map { CIRFunctionID(MLIRModuleID(moduleName), it.get<String>("nameId")!!) }.orEmpty()
            }
        }
    }

    override fun findGlobalSources(classpath: CIRClasspath, globalID: CIRGlobalID): List<CIRGlobalSource> {
        return read { txn ->
            txn.find(
                PersistenceEntity.ENTITY_GLOBAL, PersistenceEntity.Global.CONSOLIDATED_NAME, globalID.asConsolidatedID()
            ).filter { it["ownerId"] in classpath.registeredLocationIds }
                .map { it.toGlobalSource(globalID, sourceLoader) }.toList()
        }
    }

    override fun findModuleAliasData(classpath: CIRClasspath, moduleId: MLIRModuleID): ByteArray? {
        return read { txn ->
            // A module name may legitimately appear in several registered
            // locations (e.g. linked sub-projects re-exporting the same .cir).
            // Pick the first such location belonging to the current classpath -
            // alias data is content-derived from the file, so duplicates are
            // expected to agree on it.
            txn.find(PersistenceEntity.ENTITY_MODULE, PersistenceEntity.Module.ID, moduleId.id)
                .firstOrNull { it[PersistenceEntity.Module.LOCATION_ID] in classpath.registeredLocationIds }
                ?.getRawBlob(PersistenceEntity.Module.ALIAS_DATA)
        }
    }

    override fun findLocation(locationId: Long): RegisteredLocation {
        val locationData = read { txn ->
            txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, locationId)
                ?.let { PersistentByteCodeLocationData.fromErsEntity(it) }
        }
        return PersistentByteCodeLocation(
            persistence = this, id = locationId, cachedData = locationData, cachedLocation = null
        )
    }

    override fun findModules(classpath: CIRClasspath): List<String> {
        return read { txn ->
            classpath.registeredLocationIds.mapNotNull { id ->
                val module = txn.find(PersistenceEntity.ENTITY_MODULE, PersistenceEntity.Module.LOCATION_ID, id)
                    .singleOrNull()
                module?.get<String>(PersistenceEntity.Module.ID)
            }
        }
    }

    override fun findFunctionBytecode(classpath: CIRClasspath, functionID: CIRFunctionID): ByteArray? {
        return read { txn ->
            val function = txn.find(PersistenceEntity.ENTITY_FUNCTION, PersistenceEntity.Function.NAME, functionID.id)
                .filter { it["ownerId"] in classpath.registeredLocationIds && it.get<FunctionKind>(PersistenceEntity.Function.DEF_OR_DECL) == FunctionKind.DEFINITION }
                .firstOrNull()
            function?.getRawBlob(PersistenceEntity.Function.BYTECODE)
        }
    }

    override fun findFunctionInfo(classpath: CIRClasspath, functionID: CIRFunctionID): ByteArray {
        return read { txn ->
            // Assume that each module should contain single definition/declaration of the function
            val function = txn.find(
                PersistenceEntity.ENTITY_FUNCTION,
                PersistenceEntity.Function.CONSOLIDATED_NAME,
                functionID.asConsolidatedID()
            ).also {
                if (it.size >= 2) {
                    throw RuntimeException("Database contains 2 entities of ${functionID.id} in the single module ${functionID.moduleID}")
                }
            }.firstOrNull()
            function?.getRawBlob(PersistenceEntity.Function.INFO)
                ?: throw IllegalArgumentException("Could not find ${functionID.id}")
        }
    }

    override fun findFunctionsByType(classpath: CIRClasspath, typeID: MLIRTypeID): List<CIRFunctionID> {
        return read { txn ->
            txn.find(
                PersistenceEntity.ENTITY_FUNCTION, PersistenceEntity.Function.TYPE, typeID.id
            ).filter { it["ownerId"] in classpath.registeredLocationIds }.map {
                val functionName = it.get<String>(PersistenceEntity.Type.NAME)!!
                val moduleName = it.get<String>(PersistenceEntity.Function.MODULE)!!
                CIRFunctionID(MLIRModuleID(moduleName), functionName)
            }.toList()
        }
    }

    override fun findFunctionSourcesBySymbolName(classpath: CIRClasspath, symbolName: String): List<CIRFunctionSource> {
        return read { txn ->
            txn.find(PersistenceEntity.ENTITY_FUNCTION, PersistenceEntity.Function.NAME, symbolName)
                .filter {
                    it["ownerId"] in classpath.registeredLocationIds &&
                        it.get<FunctionKind>(PersistenceEntity.Function.DEF_OR_DECL) == FunctionKind.DEFINITION
                }
                .map { entity ->
                    val moduleName = entity.get<String>(PersistenceEntity.Function.MODULE)!!
                    val functionID = CIRFunctionID(MLIRModuleID(moduleName), symbolName)
                    entity.toFunctionSource(classpath, functionID, sourceLoader)
                }
                .toList()
        }
    }


    override fun close() {
        try {
            ers.close()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to close ERS persistence" }
        }
    }
}

private fun Entity.toFunctionSource(
    cp: CIRClasspath, functionID: CIRFunctionID, sourceLoader: CIRSourceLoader
): PersistenceCIRFunctionSource {
    var bytecode: ByteArray? = null
    if (get<FunctionKind>(PersistenceEntity.Function.DEF_OR_DECL) == FunctionKind.DEFINITION) {
        bytecode = getRawBlob(PersistenceEntity.Function.BYTECODE)
    }
    return PersistenceCIRFunctionSource(
        cp = cp,
        enclosingModuleId = get<String>(PersistenceEntity.Function.MODULE)!!,
        functionID = functionID,
        cachedInfo = getRawBlob(PersistenceEntity.Function.INFO),
        cachedBytecode = bytecode,
    )
}

private fun Entity.toTypeSource(db: CIRDatabase, sourceLoader: CIRSourceLoader) = PersistenceCIRTypeSource(
    db = db,
    enclosingModuleId = get<String>(PersistenceEntity.Type.MODULE)!!,
    typeInstanceId = id.instanceId,
    cachedByteCode = getRawBlob(PersistenceEntity.Type.BYTECODE)?.let { sourceLoader.loadTypeFromBytes(it) },
)

private fun Entity.toGlobalSource(globalID: CIRGlobalID, sourceLoader: CIRSourceLoader) =
    PersistenceCIRGlobalSource(node = getRawBlob(PersistenceEntity.Global.BYTECODE)?.let {
        sourceLoader.loadGlobalFromBytes(it)
    }
        ?: throw IllegalStateException("Can not find bytecode for the global ${globalID.id} in the module ${globalID.moduleID.id}"))

object PersistenceEntity {
    const val ENTITY_MODULE = "Module"
    const val ENTITY_FUNCTION = "Function"
    const val ENTITY_TYPE = "Type"
    const val ENTITY_GLOBAL = "Global"

    object Module {
        const val ID = "nameID"
        // Foreign key into the locations registry. Note: other entity tables
        // (Function/Type/Global) historically use "ownerId" for the same role -
        // for Module the schema settled on "locationId", so all module-level
        // queries must go through this constant to stay consistent.
        const val LOCATION_ID = "locationId"
        const val ENTITY_GLOBAL_CTOR = "globalCtor"
        const val ENTITY_GLOBAL_DTOR = "globalDtor"
        const val ALIAS_DATA = "aliasData"
    }

    object Function {
        const val INFO = "info"
        const val BYTECODE = "bytecode"

        const val NAME = "nameId"
        const val MODULE = "moduleId"

        // For faster indexing
        const val CONSOLIDATED_NAME = "consolidatedId"

        const val TYPE = "typeId"

        const val LINKAGE = "linkage"
        const val DEF_OR_DECL = "defOrDeclKind"
    }

    object Type {
        const val NAME = "nameId"
        const val MODULE = "moduleId"

        const val BYTECODE = "bytecode"
    }

    object Global {
        const val NAME = "nameId"
        const val MODULE = "moduleId"

        // For faster indexing
        const val CONSOLIDATED_NAME = "consolidatedId"

        const val LINKAGE = "linkage"

        const val BYTECODE = "bytecode"
    }
}
