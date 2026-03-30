/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-25
 */

package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class ServerConfigurationRepository(
    private val client: OkHttpClient = OkHttpClient(),
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build(),
) {

    private val responseAdapter = moshi.adapter(ConfigurationResponse::class.java)

    suspend fun fetchConfiguration(baseUrl: String?): Result<ConfigurationDocument?> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalized = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
                    ?: throw IOException("Missing base url")
                val url = normalized.toHttpUrlOrNull()?.newBuilder()
                    ?.addPathSegments("db/configurations/_all_docs")
                    ?.addQueryParameter("include_docs", "true")
                    ?.build()
                    ?.toString()
                    ?: throw IOException("Invalid base url")
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    responseAdapter.fromJson(body)?.rows?.mapNotNull { it.doc }?.firstOrNull()
                }
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class ConfigurationResponse(
        @param:Json(name = "rows") val rows: List<Row> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class Row(
        @param:Json(name = "doc") val doc: ConfigurationDocument?,
    )

    @JsonClass(generateAdapter = true)
    data class ConfigurationDocument(
        @param:Json(name = "keys") val keys: AiKeys? = null,
        @param:Json(name = "models") val models: AiModels? = null,
        @param:Json(name = "preferredLang") val preferredLang: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class AiKeys(
        @param:Json(name = "openai") val openAi: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class AiModels(
        @param:Json(name = "openai") val openAi: String? = null,
    )
}
