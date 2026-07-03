package org.ole.planet.myplanet.lite

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SignupRepository(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun checkUsernameAvailability(serverBaseUrl: String, username: String): UsernameAvailability =
        withContext(ioDispatcher) {
            val requestUrl = buildUsernameLookupUrl(serverBaseUrl, username)
                ?: return@withContext UsernameAvailability.UNKNOWN

            runCatching {
                val request = Request.Builder()
                    .url(requestUrl)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 200) {
                        UsernameAvailability.TAKEN
                    } else {
                        UsernameAvailability.AVAILABLE
                    }
                }
            }.getOrElse {
                UsernameAvailability.UNKNOWN
            }
        }

    internal suspend fun submitSignup(serverBaseUrl: String, username: String, payload: JSONObject): SignupSubmissionResult =
        withContext(ioDispatcher) {
            val requestUrl = buildUsernameLookupUrl(serverBaseUrl, username)
                ?: return@withContext SignupSubmissionResult.FAILED

            val mediaType = "application/json; charset=utf-8".toMediaType()

            try {
                val body = payload.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(requestUrl)
                    .put(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> SignupSubmissionResult.SUCCESS
                        409 -> SignupSubmissionResult.USERNAME_TAKEN
                        else -> SignupSubmissionResult.FAILED
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                SignupSubmissionResult.FAILED
            }
        }

    private fun buildUsernameLookupUrl(serverBaseUrl: String, username: String): String? {
        val baseUrl = serverBaseUrl.trim()
        if (baseUrl.isEmpty()) {
            return null
        }
        return baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegments("db/_users/org.couchdb.user:$username")
            ?.build()
            ?.toString()
    }
}
