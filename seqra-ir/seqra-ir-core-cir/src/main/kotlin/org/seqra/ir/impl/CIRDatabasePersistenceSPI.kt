package org.seqra.ir.impl

import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.CIRDatabasePersistence
import org.seqra.ir.api.spi.CommonSPI
import org.seqra.ir.api.spi.SPILoader
import org.seqra.ir.impl.sources.CIRSourceLoader

class CIRDatabaseException(message: String) : RuntimeException(message)

/**
 * Service Provider Interface to load pluggable implementation of [CIRDatabasePersistence].
 */
interface CIRDatabasePersistenceSPI : CommonSPI {

    /**
     * ID of [CIRDatabasePersistence] which is used to select particular persistence implementation.
     * It can be an arbitrary unique string, but use of fully qualified name of the class
     * implementing [CIRDatabasePersistenceSPI] is preferable.
     */
    override val id: String

    /**
     * Creates new instance of [CIRDatabasePersistence] specified [CIRSettings].
     * @param settings - settings to use for creation of [CIRDatabasePersistence] instance
     * @return new [CIRDatabasePersistence] instance
     */
    fun newPersistence(settings: CIRSettings, sourceLoader: CIRSourceLoader): CIRDatabasePersistence

    /**
     * Creates new instance of [LocationsRegistry] and bind it to specified [CIRDatabase].
     * Implementation of [LocationsRegistry] is specific to persistence provided by this SPI.
     * [LocationsRegistry] is always being created _after_ corresponding [CIRDatabasePersistence]
     * is created.
     * @param jcdb - [CIRDatabase] which [LocationsRegistry] is bound to
     */
    fun newLocationsRegistry(jcdb: CIRDatabase): LocationsRegistry

    companion object : SPILoader() {

        @JvmStatic
        fun getProvider(id: String): CIRDatabasePersistenceSPI {
            return loadSPI(id) ?: throw CIRDatabaseException("No CIRDatabasePersistenceSPI found by id = $id")
        }
    }
}