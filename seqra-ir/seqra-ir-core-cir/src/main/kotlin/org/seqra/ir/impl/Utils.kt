package org.seqra.ir.impl

import com.google.common.hash.Hashing
import java.nio.charset.StandardCharsets

val String.shaHash: String
    get() {
        return Hashing.sha256()
            .hashString(this, StandardCharsets.UTF_8)
            .toString()
    }
