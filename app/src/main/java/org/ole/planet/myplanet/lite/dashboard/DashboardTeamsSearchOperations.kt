package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import java.io.IOException
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal class DashboardTeamsSearchOperations(
    private val client: OkHttpClient,
    moshi: Moshi,
) {
    private val requestAdapter = moshi.adapter(SearchTeamsFindRequest::class.java)
    private val responseAdapter = moshi.adapter(TeamsFindResponse::class.java)

    fun searchTeams(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        name: String,
    ): List<TeamDocument> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return emptyList()

        val payload = requestAdapter.toJson(
            SearchTeamsFindRequest(
                SearchTeamsSelector(
                    name = RegexCondition(buildContainsRegex(normalizedName)),
                    id = NotEqualCondition(),
                ),
            ),
        )
        val request = Request.Builder()
            .url("$normalizedBase/db/teams/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                credentials?.let { header("Authorization", Credentials.basic(it.username, it.password)) }
                sessionCookie?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            responseAdapter.fromJson(response.body.string())?.docs ?: emptyList()
        }
    }

    private fun buildContainsRegex(name: String): String {
        val regexMetaCharacters = "\\.^\$|?*+()[]{}"
        val escapedName = buildString {
            name.forEach { character ->
                if (character in regexMetaCharacters) append('\\')
                append(character)
            }
        }
        return "(?i).*$escapedName.*"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
