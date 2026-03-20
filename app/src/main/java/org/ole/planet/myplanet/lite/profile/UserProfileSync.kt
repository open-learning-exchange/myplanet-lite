/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite.profile

import okhttp3.OkHttpClient
import okhttp3.Request

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.json.JSONObject
import org.ole.planet.myplanet.lite.util.nullIfBlank

import java.io.IOException

class UserProfileSync(
    private val client: OkHttpClient,
    private val database: UserProfileDatabase
) {

    suspend fun refreshProfile(
        serverBaseUrl: String,
        username: String,
        sessionCookie: String?
    ): Boolean {
        val normalizedBase = serverBaseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) {
            return false
        }
        val profileUrl = "$normalizedBase/db/_users/org.couchdb.user:$username"
        return withContext(Dispatchers.IO) {
            try {
                val profileRequestBuilder = Request.Builder()
                    .url(profileUrl)
                    .get()
                sessionCookie.nullIfBlank()?.let { cookie ->
                    profileRequestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(profileRequestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext false
                    }

                    val body = response.body.string()
                    if (body.isNullOrBlank()) {
                        return@withContext false
                    }

                    val json = JSONObject(body)
                    val hasAvatar = json.optJSONObject("_attachments")?.optJSONObject("img") != null
                    val avatarBytes = if (hasAvatar) {
                        fetchAvatarBytes("${profileUrl}/img", sessionCookie)
                    } else {
                        null
                    }

                    val userProfile = UserProfile(
                        username = json.optString("name").nullIfBlank() ?: username,
                        firstName = json.optString("firstName").nullIfBlank(),
                        middleName = json.optString("middleName").nullIfBlank(),
                        lastName = json.optString("lastName").nullIfBlank(),
                        email = json.optString("email").nullIfBlank(),
                        language = json.optString("language").nullIfBlank(),
                        phoneNumber = json.optString("phoneNumber").nullIfBlank(),
                        birthDate = json.optString("birthDate").nullIfBlank(),
                        gender = json.optString("gender").nullIfBlank(),
                        level = json.optString("level").nullIfBlank(),
                        avatarImage = avatarBytes,
                        revision = json.optString("_rev").nullIfBlank(),
                        derivedKey = json.optString("derived_key").nullIfBlank(),
                        rawDocument = json.toString(),
                        isUserAdmin = json.optBoolean("isUserAdmin", false)
                    )

                    database.saveProfile(userProfile)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun clearProfile() {
        withContext(Dispatchers.IO) {
            database.clearProfile()
        }
    }

    private fun fetchAvatarBytes(avatarUrl: String, sessionCookie: String?): ByteArray? {
        return try {
            val avatarRequestBuilder = Request.Builder()
                .url(avatarUrl)
                .get()
            sessionCookie.nullIfBlank()?.let { cookie ->
                avatarRequestBuilder.addHeader("Cookie", cookie)
            }

            client.newCall(avatarRequestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                response.body.bytes()
            }
        } catch (error: IOException) {
            null
        }
    }

    private companion object {}
}
