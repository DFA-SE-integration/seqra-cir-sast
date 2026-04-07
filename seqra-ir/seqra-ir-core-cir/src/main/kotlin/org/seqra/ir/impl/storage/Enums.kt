package org.seqra.ir.impl.storage

enum class LocationState {
    INITIAL,
    AWAITING_INDEXING,
    PROCESSED,
    OUTDATED
}