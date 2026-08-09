/*
Author: Walfre López Prado
Email: loppra@plataformasinformaticas.com
Creation date: 2026-08-09
 */

package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import java.io.IOException
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.nullIfBlank

internal class DashboardTeamsUserOperations(
    private val client: OkHttpClient,
    private val moshi: Moshi,
) {
    private val usersFindRequestAdapter = moshi.adapter(UsersFindRequest::class.java)
    private val usersFindResponseAdapter = moshi.adapter(UsersFindResponse::class.java)

    fun fetchTeamMemberProfileDetails(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        username: String,
    ): TeamMemberProfileDetails {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing base url")
        return fetchUserProfile(normalizedBase, username, credentials, sessionCookie)
            ?: throw IOException("Profile not found")
    }

    fun fetchUserProfiles(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        userIds: List<String>,
    ): List<UserDocument> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        val basicAuth = credentials?.let { Credentials.basic(it.username, it.password) }
            ?: throw IOException("Missing credentials for basic auth")
        if (userIds.isEmpty()) return emptyList()

        val selector = UserIdSelector(ids = IdsInClause(ids = userIds))
        val payload = usersFindRequestAdapter.toJson(UsersFindRequest(selector))
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/_users/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", basicAuth)
            .header("Content-Type", "application/json")
        sessionCookie.nullIfBlank()?.let { requestBuilder.addHeader("Cookie", it) }

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            usersFindResponseAdapter.fromJson(response.body.string())?.docs ?: emptyList()
        }
    }

    fun fetchAllUsers(request: FetchUsersRequest): List<UserDocument> {
        val normalizedBase = request.baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        val basicAuth = request.credentials?.let { Credentials.basic(it.username, it.password) }
            ?: throw IOException("Missing credentials for basic auth")
        if (request.pageSize <= 0) return emptyList()

        val planetCode = request.planetCode.nullIfBlank() ?: throw IOException("Missing planet code for user search")
        val parentCode = request.parentCode.nullIfBlank() ?: throw IOException("Missing parent code for user search")
        val payload = buildUsersFindPayload(
            planetCode,
            parentCode,
            request.pageSize,
            request.skip,
            request.searchTerm,
            request.excludedUserIds,
        )
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/_users/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", basicAuth)
            .header("Content-Type", "application/json")
        request.sessionCookie.nullIfBlank()?.let { requestBuilder.addHeader("Cookie", it) }

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            usersFindResponseAdapter.fromJson(response.body.string())?.docs ?: emptyList()
        }
    }

    internal fun fetchUserProfile(
        baseUrl: String,
        username: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
    ): TeamMemberProfileDetails? {
        if (baseUrl.isBlank() || username.isBlank()) return null
        val requestBuilder = Request.Builder()
            .url("$baseUrl/db/_users/org.couchdb.user:$username")
            .get()
            .withAuth(credentials, sessionCookie)

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string().nullIfBlank() ?: return null
                val json = JSONObject(body)
                val attachments = json.optJSONObject("_attachments")
                TeamMemberProfileDetails(
                    username = json.optString("name").nullIfBlank() ?: username,
                    firstName = json.optString("firstName").nullIfBlank(),
                    middleName = json.optString("middleName").nullIfBlank(),
                    lastName = json.optString("lastName").nullIfBlank(),
                    email = json.optString("email").nullIfBlank(),
                    phoneNumber = json.optString("phoneNumber").nullIfBlank(),
                    language = json.optString("language").nullIfBlank(),
                    level = json.optString("level").nullIfBlank(),
                    gender = json.optString("gender").nullIfBlank(),
                    birthDate = json.optString("birthDate").nullIfBlank(),
                    hasAvatar = attachments?.optJSONObject("img") != null,
                )
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun buildUsersFindPayload(
        planetCode: String,
        parentCode: String,
        pageSize: Int,
        skip: Int,
        searchTerm: String?,
        excludedUserIds: List<String>,
    ): String {
        val selector = JSONObject().put("planetCode", planetCode).put("parentCode", parentCode)
        val filteredExcludedIds = excludedUserIds.filter(String::isNotBlank)
        if (filteredExcludedIds.isNotEmpty()) {
            val excludedArray = JSONArray()
            filteredExcludedIds.forEach(excludedArray::put)
            selector.put("_id", JSONObject().put("$" + "nin", excludedArray))
        }
        if (!searchTerm.isNullOrBlank()) {
            val orArray = JSONArray()
            listOf("name", "firstName", "middleName", "lastName").forEach { field ->
                val regex = JSONObject().put("$" + "regex", "(?i)${searchTerm.trim()}")
                orArray.put(JSONObject().put(field, regex))
            }
            selector.put("$" + "or", orArray)
        }
        return JSONObject().put("selector", selector).put("skip", skip).put("limit", pageSize).toString()
    }

    private fun Request.Builder.withAuth(credentials: StoredCredentials?, sessionCookie: String?): Request.Builder {
        credentials?.let { addHeader("Authorization", Credentials.basic(it.username, it.password)) }
        sessionCookie.nullIfBlank()?.let { addHeader("Cookie", it) }
        return this
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
