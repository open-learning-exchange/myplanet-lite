package org.ole.planet.myplanet.lite.util

import com.squareup.moshi.Moshi
import org.ole.planet.myplanet.lite.model.ServerMetadataResponse

object ServerMetadataExtractor {
    fun extract(payload: String, moshi: Moshi): Pair<String?, String?>? {
        return runCatching {
            val serverMetadataAdapter = moshi.adapter(ServerMetadataResponse::class.java)
            val response = serverMetadataAdapter.fromJson(payload)
            val rows = response?.rows ?: return@runCatching null
            for (row in rows) {
                val doc = row.doc ?: continue
                val parentCode = doc.parentCode?.takeIf { it.isNotBlank() }
                val code = doc.code?.takeIf { it.isNotBlank() }
                if (parentCode != null || code != null) {
                    return@runCatching Pair(parentCode, code)
                }
            }
            null
        }.getOrNull()
    }
}
