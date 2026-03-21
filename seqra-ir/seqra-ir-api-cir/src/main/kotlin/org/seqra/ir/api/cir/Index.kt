package org.seqra.ir.api.cir

interface ByteCodeIndexer {
    fun index(moduleNode: IRNode)

    fun flush()
}

interface CIRFeature<REQ, RES> {
    suspend fun query(classpath: CIRClasspath, req: REQ): Sequence<RES>

    fun newIndexer(jcdb: CIRDatabase, location: RegisteredLocation): ByteCodeIndexer

    fun onSignal(signal: CIRSignal)
}

sealed class CIRSignal(val jcdb: CIRDatabase) {

    /** can be used for creating persistence scheme */
    class BeforeIndexing(jcdb: CIRDatabase, val clearOnStart: Boolean) : CIRSignal(jcdb)

    /** can be used to create persistence indexes after data batch upload */
    class AfterIndexing(jcdb: CIRDatabase) : CIRSignal(jcdb)

    /** can be used for cleanup index data when location is removed */
    class LocationRemoved(jcdb: CIRDatabase, val location: RegisteredLocation) : CIRSignal(jcdb)

    /**
     * rebuild all
     */
    class Drop(jcdb: CIRDatabase) : CIRSignal(jcdb)

    /**
     * database is closed
     */
    class Closed(jcdb: CIRDatabase) : CIRSignal(jcdb)

}