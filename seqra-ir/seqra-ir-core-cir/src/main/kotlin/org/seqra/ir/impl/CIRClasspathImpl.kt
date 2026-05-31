package org.seqra.ir.impl

import mu.KLogging
import org.seqra.ir.api.cir.*
import org.seqra.ir.api.cir.CIRClasspathExtFeature.*
import org.seqra.ir.api.cir.cfg.CIRFunction
import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRGlobal
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.MLIRModuleID
import org.seqra.ir.api.cir.cfg.MLIRType
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.impl.cfg.builder.buildCIRGlobalID
import org.seqra.ir.impl.cfg.builder.buildCIRGlobalOp
import org.seqra.ir.impl.cfg.builder.buildMLIRType
import org.seqra.ir.impl.features.AbstractCIRResolvedResult.*
import org.seqra.ir.impl.features.CIRFeatureEventImpl
import org.seqra.ir.impl.features.CIRFeaturesChain
import org.seqra.ir.impl.grpc.Model
import org.seqra.ir.impl.grpc.Type
import org.seqra.ir.impl.alias.CIRAliasDataCodec
import org.seqra.ir.impl.ir.CIRFunctionImpl
import java.util.concurrent.ConcurrentHashMap

val logger = object : KLogging() {}.logger

class CIRClasspathImpl(
    private val locationsRegistrySnapshot: LocationsRegistrySnapshot,
    override val db: CIRDatabase,
    override val features: List<CIRClasspathFeature>
) : CIRClasspath {
    override val registeredLocations: List<RegisteredLocation> = locationsRegistrySnapshot.locations
    override val registeredLocationIds: Set<Long> = registeredLocations.map { it.id }.toSet()

    private val featuresChain = CIRFeaturesChain(features + CIRClasspathFeatureImpl())
    private val functionsBySymbolNameCache = mutableMapOf<String, CIRFunction?>()

    // Per-module decoded alias map; lazy and shared across threads. We cache
    // at module granularity because `CIRModuleAliasData` is a single blob per
    // module - decoding once amortizes the cost across all queries against
    // that module's functions. ConcurrentHashMap is required because the
    // taint analyzer queries this from many worker threads simultaneously.
    private val functionAliasByModuleId =
        ConcurrentHashMap<MLIRModuleID, Map<CIRFunctionID, CIRFunctionAliasData>>()
    private val aliasDecodeFailedModules = ConcurrentHashMap.newKeySet<MLIRModuleID>()

    init {
        assert(registeredLocationIds.isNotEmpty())
    }

    override val moduleNames: List<String>
        get() = db.persistence.findModules(this)

    // Searchers
    override fun findFunctionOrNull(functionID: CIRFunctionID) =
        featuresChain.run<CIRClasspathExtFeature, CIRResolvedFunctionResult> {
            it.tryFindFunction(functionID)
        }?.function

    override fun findFunctionBySymbolName(symbolName: String): CIRFunction? {
        if (functionsBySymbolNameCache.containsKey(symbolName)) {
            return functionsBySymbolNameCache[symbolName]
        }

        logger.warn {
            "Find function by symbol name '$symbolName'"
        }

        val sources = db.persistence.findFunctionSourcesBySymbolName(this, symbolName)
        val resolvedSource = selectDefinitionSource(symbolName, sources) ?: return null
        val resolvedFunction = newFunction(resolvedSource)
        functionsBySymbolNameCache[symbolName] = resolvedFunction
        return resolvedFunction
    }

    override fun findTypeOrNull(typeID: MLIRTypeID) = featuresChain.run<CIRClasspathExtFeature, CIRResolvedTypeResult> {
        it.tryFindType(typeID)
    }?.type

    override fun findGlobalOrNull(globalID: CIRGlobalID) =
        featuresChain.run<CIRClasspathExtFeature, CIRResolvedGlobalResult> {
            it.tryFindGlobal(globalID)
        }?.global

    // TODO: for now it is a stub
    //  Surely, it must use features chain etc.
    //  But now it is just a hack
    fun findAllFunctionsWithSignature(typeID: MLIRTypeID) = db.persistence.findFunctionsByType(this, typeID)

    override fun getGlobalConstructors(): List<CIRFunctionID> = db.persistence.findGlobalCtors(this)
    override fun getGlobalDestructors(): List<CIRFunctionID> = db.persistence.findGlobalDtors(this)

    override fun findFunctionAliasData(functionID: CIRFunctionID): CIRFunctionAliasData? {
        val moduleId = functionID.moduleID
        if (moduleId in aliasDecodeFailedModules) return null
        val byFn = functionAliasByModuleId.computeIfAbsent(moduleId) { id ->
            val raw = db.persistence.findModuleAliasData(this, id) ?: return@computeIfAbsent emptyMap()
            try {
                CIRAliasDataCodec.decodeModule(raw, id)
            } catch (e: Exception) {
                // Mark the module as unrecoverable for alias info so we do not
                // re-attempt the (failing) decode on every query, and so the
                // cache itself stays consistent (no half-decoded blobs).
                aliasDecodeFailedModules.add(id)
                logger.error(e) {
                    "Failed to parse alias blob for module ${id.id}; alias analysis disabled for this module"
                }
                emptyMap()
            }
        }
        return byFn[functionID]
    }

    // Constructors
    private fun newFunction(source: CIRFunctionSource): CIRFunction {
        return CIRFunctionImpl(source, featuresChain, this)
    }

    private fun newType(source: CIRTypeSource): MLIRType {
        return buildMLIRType(Type.MLIRType.parseFrom(source.node.byteBuffer))
    }

    private fun newGlobal(source: CIRGlobalSource): CIRGlobal {
        val global = Model.CIRGlobal.parseFrom(source.node.byteBuffer)
        return CIRGlobal(buildCIRGlobalID(global.id), buildCIRGlobalOp(global.info))
    }

    // Classpath controllers
    override fun close() {
        locationsRegistrySnapshot.close()
    }

    private inner class CIRClasspathFeatureImpl : CIRClasspathExtFeature {
        override fun tryFindFunction(functionID: CIRFunctionID): CIRResolvedFunctionResult? {
            val functions = db.persistence.findFunctionSources(this@CIRClasspathImpl, functionID)
            assert(functions.size <= 1)

            val singleFunction = functions.firstOrNull()
            return singleFunction?.let { CIRResolvedFunctionResultImpl(functionID, newFunction(singleFunction)) }
        }

        override fun tryFindType(typeID: MLIRTypeID): CIRResolvedTypeResult? {
            val types = db.persistence.findTypeSources(this@CIRClasspathImpl, typeID)
            logger.debug("Found ${types.size} types with name \"${typeID.id}\"")
            return types.firstOrNull()?.let { CIRResolvedTypeResultImpl(typeID, newType(it)) }
        }

        override fun tryFindGlobal(globalID: CIRGlobalID): CIRResolvedGlobalResult? {
            val globals = db.persistence.findGlobalSources(this@CIRClasspathImpl, globalID)
            logger.debug("Found ${globals.size} globals with name \"${globalID.id}\"")
            return globals.firstOrNull()?.let { CIRResolvedGlobalResultImpl(globalID, newGlobal(it)) }
        }

        override fun event(result: Any): CIRFeatureEvent {
            return CIRFeatureEventImpl(this, result)
        }
    }

    private fun selectDefinitionSource(
        symbolName: String,
        sources: List<CIRFunctionSource>,
    ): CIRFunctionSource? {
        if (sources.isEmpty()) {
            return null
        }

        val preferredSources = selectPreferredSources(sources).toList()
        val definitionSources = preferredSources.filter { it.bytecodeNode != null }

        return when {
            definitionSources.size == 1 -> definitionSources.single()
            definitionSources.size > 1 -> {
                val disambiguated = disambiguateMultipleDefinitions(symbolName, definitionSources)
                if (disambiguated != null) {
                    logger.warn {
                        "Resolved duplicate definitions for symbol '$symbolName' to ${disambiguated.functionID}"
                    }
                    disambiguated
                } else {
                    logAmbiguousFunctionResolution(symbolName, definitionSources)
                    null
                }
            }

            preferredSources.size == 1 -> preferredSources.single().also {
                logger.warn {
                    "No definition for symbol '$symbolName', using declaration ${it.functionID}"
                }
            }

            preferredSources.size > 1 -> {
                logAmbiguousFunctionResolution(symbolName, preferredSources)
                preferredSources.first()
            }

            else -> null
        }
    }

    private fun selectPreferredSources(sources: List<CIRFunctionSource>): Collection<CIRFunctionSource> {
        return sources.groupBy { it.functionID }
            .mapValues { (_, variants) -> variants.firstOrNull { it.bytecodeNode != null } ?: variants.first() }
            .values
    }

    private fun logAmbiguousFunctionResolution(symbolName: String, sources: Collection<CIRFunctionSource>) {
        logger.warn {
            "Ambiguous function resolution for symbol '$symbolName': ${sources.map { it.functionID }}"
        }
    }

    /**
     * Juliet CWE416 interfile fixtures ship the same symbol in `_*_<n>a.*` (entry TU)
     * and `_*_<n>b.*` (helper TU). ClangIR can attach a body in both TUs, so we see
     * multiple [CIRFunctionSource] rows with bytecode for one symbol name. Picking
     * deterministically restores [findFunctionBySymbolName] for callees such as
     * `*_63b_badSink` (must resolve to the `b` translation unit).
     */
    private fun disambiguateMultipleDefinitions(
        symbolName: String,
        definitionSources: List<CIRFunctionSource>,
    ): CIRFunctionSource? =
        preferDefinitionWhoseRegisteredLocationStemAppearsInSymbol(symbolName, definitionSources)
            ?: preferDefinitionWhoseModuleStemAppearsInSymbol(symbolName, definitionSources)
            ?: preferJulietInterfileBOverADefinition(definitionSources) { it.registeredLocationPath().orEmpty() }
            ?: preferJulietInterfileBOverADefinition(definitionSources) { it.functionID.moduleID.id }

    private fun preferDefinitionWhoseRegisteredLocationStemAppearsInSymbol(
        symbolName: String,
        definitionSources: List<CIRFunctionSource>,
    ): CIRFunctionSource? {
        val scored = definitionSources.mapNotNull { src ->
            val stem = src.registeredLocationPath()
                ?.let(::moduleFileStemWithoutExtension)
                ?.removeSuffix(".proto")
                .orEmpty()
            if (stem.isNotEmpty() && symbolName.contains(stem)) {
                src to stem.length
            } else {
                null
            }
        }
        if (scored.isEmpty()) return null
        val bestLen = scored.maxOf { it.second }
        return scored.filter { it.second == bestLen }
            .map { it.first }
            .distinct()
            .minByOrNull { it.registeredLocationPath().orEmpty() }
    }

    private fun preferDefinitionWhoseModuleStemAppearsInSymbol(
        symbolName: String,
        definitionSources: List<CIRFunctionSource>,
    ): CIRFunctionSource? {
        val scored = definitionSources.mapNotNull { src ->
            val stem = moduleFileStemWithoutExtension(src.functionID.moduleID.id)
            if (stem.isNotEmpty() && symbolName.contains(stem)) {
                src to stem.length
            } else {
                null
            }
        }
        if (scored.isEmpty()) return null
        val bestLen = scored.maxOf { it.second }
        return scored.filter { it.second == bestLen }
            .map { it.first }
            .distinct()
            .minByOrNull { it.functionID.moduleID.id }
    }

    private fun preferJulietInterfileBOverADefinition(
        definitionSources: List<CIRFunctionSource>,
        pathSelector: (CIRFunctionSource) -> String,
    ): CIRFunctionSource? {
        if (definitionSources.size != 2) return null
        val tagged = definitionSources.mapNotNull { src ->
            val key = parseJulietSplitModuleKey(pathSelector(src)) ?: return@mapNotNull null
            src to key
        }
        if (tagged.size != 2) return null
        if (tagged[0].second.stem != tagged[1].second.stem) return null
        val letters = tagged.map { it.second.letter }.toSet()
        if (letters != setOf('a', 'b')) return null
        return tagged.first { it.second.letter == 'b' }.first
    }

    private data class JulietSplitModuleKey(val stem: String, val letter: Char)

    private fun parseJulietSplitModuleKey(modulePath: String): JulietSplitModuleKey? {
        val base = modulePath.replace('\\', '/').substringAfterLast('/')
        val m = Regex("""^(.+)_(\d+)([ab])[.](c|cpp|cxx|cir|protocir)$""", RegexOption.IGNORE_CASE)
            .matchEntire(base)
            ?: return null
        return JulietSplitModuleKey(
            stem = "${m.groupValues[1]}_${m.groupValues[2]}",
            letter = m.groupValues[3].lowercase().first(),
        )
    }

    private fun moduleFileStemWithoutExtension(modulePath: String): String =
        modulePath.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')

    private fun CIRFunctionSource.registeredLocationPath(): String? =
        locationId?.let { id -> registeredLocations.firstOrNull { it.id == id }?.path }
}
