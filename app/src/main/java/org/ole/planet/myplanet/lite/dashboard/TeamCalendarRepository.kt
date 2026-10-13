package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal class CalendarHttpException(val status: Int) : IOException("Calendar request failed: $status")

internal class CalendarAccessDeniedException : IOException()

internal class TeamCalendarRepository(
    private val client: OkHttpClient = AuthDependencies.client,
) {
    suspend fun fetch(
        baseUrl: String,
        credentials: StoredCredentials,
        cookie: String?,
        teamId: String,
    ): Result<List<TeamMeetup>> = withContext(Dispatchers.IO) {
        try {
            require(teamId.isNotBlank())
            val root = baseUrl.trim().trimEnd('/').removeSuffix("/db") + "/db/"
            val teamUrl = root.toHttpUrl().newBuilder().addPathSegment("teams").addPathSegment(teamId).build()
            val team = request(teamUrl.toString(), null, credentials, cookie)
            require(team.getString("_id") == teamId)
            if (!team.optBoolean("public", false)) {
                val selector = JSONObject().put("docType", "membership")
                    .put("teamId", teamId)
                    .put("userId", "org.couchdb.user:${credentials.username}")
                val members = request(root + "teams/_find", JSONObject().put("selector", selector).put("limit", 1), credentials, cookie)
                    .getJSONArray("docs")
                if (members.length() == 0) {
                    throw CalendarAccessDeniedException()
                }
            }
            val all = mutableListOf<TeamMeetup>()
            var bookmark: String? = null
            do {
                val payload = JSONObject().put("selector", JSONObject().put("link", JSONObject().put("teams", teamId))).put("limit", 1000)
                bookmark?.let { payload.put("bookmark", it) }
                val response = request(root + "meetups/_find", payload, credentials, cookie)
                val docs = response.getJSONArray("docs")
                for (index in 0 until docs.length()) {
                    val document = docs.getJSONObject(index)
                    if (document.optJSONObject("link")?.optString("teams") != teamId || document.optBoolean("_deleted")) continue
                    document.toTeamMeetup()?.let(all::add)
                }
                val next = response.optString("bookmark").takeIf { it.isNotBlank() }
                if (docs.length() < 1000 || next == null || next == bookmark) break
                bookmark = next
            } while (true)
            Result.success(all.distinctBy { it.id })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun create(
        baseUrl: String,
        credentials: StoredCredentials,
        cookie: String?,
        teamId: String,
        createdBy: String,
        sourcePlanet: String,
        draft: TeamEventDraft,
    ): Result<TeamMeetup> = withContext(Dispatchers.IO) {
        try {
            val document = draft.toCreationJson()
            require(teamId.isNotBlank() && createdBy.isNotBlank() && sourcePlanet.isNotBlank())
            val root = baseUrl.trim().trimEnd('/').removeSuffix("/db") + "/db/"
            val teamUrl = root.toHttpUrl().newBuilder().addPathSegment("teams").addPathSegment(teamId).build()
            val team = request(teamUrl.toString(), null, credentials, cookie)
            require(team.getString("_id") == teamId)
            val type = team.getString("teamType").also { require(it.isNotBlank()) }
            val planetCode = team.getString("teamPlanetCode").also { require(it.isNotBlank()) }
            document.put("createdBy", createdBy).put("sourcePlanet", sourcePlanet)
                .put("createdDate", System.currentTimeMillis())
                .put("link", JSONObject().put("teams", teamId))
                .put("sync", JSONObject().put("type", type).put("planetCode", planetCode))
            val response = request(root + "meetups", document, credentials, cookie)
            if (!response.optBoolean("ok")) throw IOException("Event creation failed")
            val id = response.getString("id").also { require(it.isNotBlank()) }
            val revision = response.getString("rev").also { require(it.isNotBlank()) }
            document.put("_id", id).put("_rev", revision)
            Result.success(document.toTeamMeetup() ?: throw IOException("Invalid event"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    internal fun request(url: String, payload: JSONObject?, credentials: StoredCredentials, cookie: String?): JSONObject {
        val builder = Request.Builder().url(url)
            .header("Authorization", Credentials.basic(credentials.username, credentials.password))
            .header("Accept", "application/json")
        cookie?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        val json = payload?.toString()
        if (json == null) builder.get() else builder.post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
        val request = builder.build()
        return client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                if (response.code == 403) throw CalendarAccessDeniedException()
                throw CalendarHttpException(response.code)
            }
            JSONObject(responseBody)
        }
    }
}
