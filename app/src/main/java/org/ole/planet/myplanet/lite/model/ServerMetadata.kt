package org.ole.planet.myplanet.lite.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServerMetadataResponse(
    @param:Json(name = "rows") val rows: List<ServerMetadataRow>? = null
)

@JsonClass(generateAdapter = true)
data class ServerMetadataRow(
    @param:Json(name = "doc") val doc: ServerMetadataDoc? = null
)

@JsonClass(generateAdapter = true)
data class ServerMetadataDoc(
    @param:Json(name = "parentCode") val parentCode: String? = null,
    @param:Json(name = "code") val code: String? = null
)
