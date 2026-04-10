package org.seqra.ir.api.cir

import org.seqra.ir.api.cir.cfg.CIRFunctionID
import org.seqra.ir.api.cir.cfg.CIRGlobalID
import org.seqra.ir.api.cir.cfg.MLIRTypeID
import org.seqra.ir.api.storage.ers.Transaction
import java.io.Closeable
import java.io.File

interface RegisteredLocation {
    val cirLocation: CIRBitCodeLocation?
    val id: Long
    val path: String
}

interface CIRDatabasePersistence : Closeable {
    fun <T> write(action: (Transaction) -> T): T
    fun <T> read(action: (Transaction) -> T): T

    fun persist(location: RegisteredLocation, modules: List<CIRModuleSource>)
    fun findLocation(locationId: Long): RegisteredLocation

    // Global project structure
    fun findModules(classpath: CIRClasspath): List<String>

    // Functions
    fun findFunctionSources(classpath: CIRClasspath, functionID: CIRFunctionID): List<CIRFunctionSource>
    fun findFunctionInfo(classpath: CIRClasspath, functionID: CIRFunctionID): ByteArray
    fun findFunctionBytecode(classpath: CIRClasspath, functionID: CIRFunctionID): ByteArray?

    fun findFunctionsByType(classpath: CIRClasspath, typeID: MLIRTypeID): List<CIRFunctionID>
    fun findFunctionSourcesBySymbolName(classpath: CIRClasspath, symbolName: String): List<CIRFunctionSource>

    // Globals
    fun findGlobalSources(classpath: CIRClasspath, globalID: CIRGlobalID): List<CIRGlobalSource>

    // Types
    fun findTypeSources(classpath: CIRClasspath, typeID: MLIRTypeID): List<CIRTypeSource>

    // Projects
    fun findGlobalCtors(classpath: CIRClasspath): List<CIRFunctionID>
    fun findGlobalDtors(classpath: CIRClasspath): List<CIRFunctionID>

    // DB
    fun createIndexes() {}
}

interface CIRDatabase : Closeable {
    val persistence: CIRDatabasePersistence

    fun refresh()

    // Load the project consisting of the given files
    fun classpath(dirOrCirs: List<File>, features: List<CIRClasspathFeature>?): CIRClasspath
    fun classpath(dirOrCirs: List<File>): CIRClasspath = classpath(dirOrCirs, null)

    // Load the project with the files specified in the given link commands
    fun classpath(target: TargetID, features: List<CIRClasspathFeature>?): CIRClasspath
    fun classpath(target: TargetID): CIRClasspath = classpath(target, null)

    fun loadFiles(file: File)
    fun loadFiles(files: List<File>)

    fun loadProjects(project: CIRProject)
    fun loadProjects(projects: List<CIRProject>)

    fun loadLocations(locations: List<CIRBitCodeLocation>)
}
