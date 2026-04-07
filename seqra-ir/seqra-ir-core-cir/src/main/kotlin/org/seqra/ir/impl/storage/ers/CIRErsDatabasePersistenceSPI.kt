package org.seqra.ir.impl.storage.ers

import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.CIRDatabasePersistence
import org.seqra.ir.api.storage.ers.EntityRelationshipStorageSPI
import org.seqra.ir.impl.CIRDatabaseImpl
import org.seqra.ir.impl.CIRDatabasePersistenceSPI
import org.seqra.ir.impl.CIRErsSettings
import org.seqra.ir.impl.CIRSettings
import org.seqra.ir.impl.LocationsRegistry
import org.seqra.ir.impl.sources.CIRSourceLoader
import org.seqra.ir.impl.storage.PersistentLocationsRegistry

const val ERS_DATABASE_PERSISTENCE_SPI = "org.seqra.ir.impl.storage.ers.CIRErsDatabasePersistenceSPI"

class CIRErsDatabasePersistenceSPI : CIRDatabasePersistenceSPI {

    override val id = ERS_DATABASE_PERSISTENCE_SPI

    override fun newPersistence(settings: CIRSettings, sourceLoader: CIRSourceLoader): CIRDatabasePersistence {
        val persistenceSettings = settings.persistenceSettings
        val cirErsSettings = persistenceSettings.implSettings as CIRErsSettings
        return CIRErsPersistenceImpl(
            ers = EntityRelationshipStorageSPI.getProvider(cirErsSettings.ersId).newStorage(
                persistenceLocation = settings.persistenceSettings.persistenceLocation,
                settings = cirErsSettings.ersSettings
            ),
            sourceLoader
        )
    }

    override fun newLocationsRegistry(jcdb: CIRDatabase): LocationsRegistry {
        return PersistentLocationsRegistry(jcdb as CIRDatabaseImpl)
    }
}