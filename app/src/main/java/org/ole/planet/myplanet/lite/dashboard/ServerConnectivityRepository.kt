package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.ole.planet.myplanet.lite.model.ServerConnectivityResult
import org.ole.planet.myplanet.lite.util.ServerMetadataExtractor

class ServerConnectivityRepository(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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
                val body = response.body.string()
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

    suspend fun recordLoginActivity(requestUrl: String, payload: JSONObject, sessionCookie: String?) {
        postActivity(requestUrl, payload, sessionCookie, authHeader = null)
    }

    suspend fun recordResourceActivity(requestUrl: String, payload: JSONObject, authHeader: String?) {
        postActivity(requestUrl, payload, sessionCookie = null, authHeader = authHeader)
    }

    suspend fun recordCourseActivity(requestUrl: String, payload: JSONObject, sessionCookie: String?) {
        postActivity(requestUrl, payload, sessionCookie, authHeader = null)
    }

    suspend fun recordMyPlanetActivity(requestUrl: String, payload: JSONObject, sessionCookie: String?) {
        postActivity(requestUrl, payload, sessionCookie, authHeader = null)
    }

    private suspend fun postActivity(
        requestUrl: String,
        payload: JSONObject,
        sessionCookie: String?,
        authHeader: String?,
    ) {
        withContext(ioDispatcher) {
            runCatching {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = payload.toString().toRequestBody(mediaType)
                val requestBuilder = Request.Builder()
                    .url(requestUrl)
                    .post(body)
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                authHeader?.takeIf { it.isNotBlank() }?.let { authorization ->
                    requestBuilder.addHeader("Authorization", authorization)
                }
                client.newCall(requestBuilder.build()).execute().use { _ ->
                    // Intentionally ignoring response
                }
            }
        }
    }
}
