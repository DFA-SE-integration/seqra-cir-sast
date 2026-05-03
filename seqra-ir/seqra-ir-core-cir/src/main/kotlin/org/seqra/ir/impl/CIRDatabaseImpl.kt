package org.seqra.ir.impl

import org.seqra.ir.api.cir.*
import org.seqra.ir.impl.features.CIRClasspathCache
import org.seqra.ir.impl.features.CIRFunctionPointerCallTransformer
import org.seqra.ir.impl.features.CIRLoadStoreFeature
import org.seqra.ir.impl.features.FunctionInstructionsFeature
import org.seqra.ir.impl.sources.*
import org.seqra.ir.impl.storage.PersistentProjectRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CIRDatabaseImpl(private val settings: CIRSettings, private val sourceLoader: CIRSourceLoader) : CIRDatabase {
    override val persistence: CIRDatabasePersistence
    override val features: List<CIRFeature<*, *>> get() = featuresRegistry.features

    internal val featuresRegistry = CIRFeaturesRegistry(settings.features).also { it.bind(this) }

    private val isClosed = AtomicBoolean()
    private val locationsRegistry: LocationsRegistry
    private val projectRegistry: PersistentProjectRegistry

    private fun assertNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("Database is already closed")
        }
    }

    private fun List<CIRClasspathFeature>?.appendBuiltInFeatures(): List<CIRClasspathFeature> {
        return mutableListOf<CIRClasspathFeature>().also { result ->
            result += orEmpty()
            if (!result.any { it is CIRClasspathCache }) {
                result += CIRClasspathCache(settings.cacheSettings)
            }
            result += FunctionInstructionsFeature()
            // if (!result.any { it is CIRLoadStoreFeature }) {
                // result += CIRLoadStoreFeature
            // }
            if (!result.any { it is CIRFunctionPointerCallTransformer }) {
                result += CIRFunctionPointerCallTransformer
            }
        }
    }

    override fun refresh() {
        locationsRegistry.refresh().new.process(true)
        // TODO: process cleaned locations
        locationsRegistry.cleanup()
    }

    override fun classpath(
        dirOrCirs: List<File>, features: List<CIRClasspathFeature>?
    ): CIRClasspath {
        assertNotClosed()
        val existingLocations = dirOrCirs.filterExisting().flatMap { it.asByteCodeLocation() }.distinct()
        val processed = locationsRegistry.registerIfNeeded(existingLocations).also { it.new.process(true) }.registered
        return CIRClasspathImpl(
            locationsRegistry.newSnapshot(processed),
            this,
            features.appendBuiltInFeatures(),
        )
    }

    override fun classpath(
        target: TargetID, features: List<CIRClasspathFeature>?
    ): CIRClasspath {
        assertNotClosed()
        projectRegistry.registerIfNeeded(target.project)

        val cirFiles = projectRegistry.resolve(target).internalDependencies.map { File(it) }
        assert(cirFiles.isNotEmpty())
        return classpath(cirFiles, features)
    }

    init {
        val persistenceId = settings.persistenceId!!
        val persistenceSPI = CIRDatabasePersistenceSPI.getProvider(persistenceId)
        persistence = persistenceSPI.newPersistence(settings, sourceLoader)
        locationsRegistry = persistenceSPI.newLocationsRegistry(this)
        projectRegistry = PersistentProjectRegistry(this)
    }

    override fun loadFiles(file: File) {
        assertNotClosed()
        loadFiles(listOf(file))
    }

    override fun loadFiles(files: List<File>) {
        assertNotClosed()
        loadLocations(files.filterExisting().flatMap { it.asByteCodeLocation() })
    }

    override fun loadProjects(project: CIRProject) = loadProjects(listOf(project))

    override fun loadProjects(projects: List<CIRProject>) {
        assertNotClosed()
        val projectKnownFiles = projects.flatMap { project ->
            projectRegistry.registerIfNeeded(project).flatMap { compilationUnit ->
                compilationUnit.knownDependencies.map { File(it) }
            }
        }
        loadFiles(projectKnownFiles)
    }

    override fun loadLocations(locations: List<CIRBitCodeLocation>) {
        assertNotClosed()
        locationsRegistry.registerIfNeeded(locations).new.process(true)
    }

    private fun List<RegisteredLocation>.process(createIndexes: Boolean): List<RegisteredLocation> {
        featuresRegistry.broadcast(
            CIRInternalSignal.BeforeIndexing(settings.persistenceSettings.persistenceClearOnStart ?: false)
        )
        forEach { location ->
            val sources = arrayListOf<CIRModuleSource>()
            location.cirLocation?.modules?.forEach { (modulePath, content) ->
                val aliasBytes = location.cirLocation?.moduleAliasData?.get(modulePath)
                sources.add(CIRModuleSourceImpl(sourceLoader.loadModuleFromBytes(content), location, aliasBytes))
            }
            persistence.persist(location, sources)
            if (sources.isNotEmpty()) {
                featuresRegistry.index(location, sources)
            }
        }
        if (createIndexes) {
            persistence.createIndexes()
        }
        locationsRegistry.afterProcessing(this@process)
        return this
    }

    internal fun broadcastAfterIndexing() {
        featuresRegistry.broadcast(CIRInternalSignal.AfterIndexing)
    }

    internal fun broadcastLocationRemoved(location: RegisteredLocation) {
        featuresRegistry.broadcast(CIRInternalSignal.LocationRemoved(location))
    }

    internal fun broadcastClosed() {
        featuresRegistry.broadcast(CIRInternalSignal.Closed)
    }

    override fun close() {
        isClosed.set(true)
        broadcastClosed()
        persistence.close()
    }
}