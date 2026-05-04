package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.ole.planet.myplanet.lite.model.ServerConnectivityResult
import org.ole.planet.myplanet.lite.util.ServerMetadataExtractor

class ServerConnectivityRepository(
    private val client: OkHttpClient,
    private val moshi: Moshi
) {

    fun checkServerConnectivity(baseUrl: String): ServerConnectivityResult {
        val requestUrl = buildConfigurationRequestUrl(baseUrl) ?: return ServerConnectivityResult(false)
        return runCatching {
            val request = Request.Builder()
                .url(requestUrl)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    return@use ServerConnectivityResult(false)
                }
                val body = response.body?.string() ?: ""
                if (body.isBlank()) {
                    return@use ServerConnectivityResult(true)
                }
                val metadata = ServerMetadataExtractor.extract(body, moshi)
                ServerConnectivityResult(true, metadata?.first, metadata?.second)
            }
        }.getOrDefault(ServerConnectivityResult(false))
    }

    private fun buildConfigurationRequestUrl(baseUrl: String): String? {
        return baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegments("db/configurations/_all_docs")
            ?.addQueryParameter("include_docs", "true")
            ?.build()
            ?.toString()
    }
}
