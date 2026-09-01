package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.profile.StoredCredentials

data class CreateTeamRequest(
    val name: String,
    val description: String,
    val services: String = "",
    val rules: String = "",
    val isPublic: Boolean,
    val planetCode: String,
    val parentCode: String,
    val userId: String,
    val entityType: String = "team",
    val teamType: String = TEAM_TYPE_LOCAL_VALUE,
)

private const val TEAM_TYPE_LOCAL_VALUE = "local"

data class CreatedTeam(val id: String, val revision: String?)

class IncompleteTeamCreationException(val teamId: String, cause: Throwable) :
    IOException("Team created but leader membership failed", cause)

internal class DashboardTeamsCreateOperations(private val client: OkHttpClient) {
    fun createTeam(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        request: CreateTeamRequest,
    ): CreatedTeam {
        val normalizedBase = baseUrl.trim().trimEnd('/').ifEmpty { throw IOException("Missing server base URL") }
        validateRequest(request)
        if (teamNameExists(normalizedBase, credentials, sessionCookie, request.name, request.entityType)) {
            throw DuplicateTeamNameException()
        }

        val createdTeam = postTeam(normalizedBase, credentials, sessionCookie, request)
        try {
            ensureLeaderMembership(normalizedBase, credentials, sessionCookie, createdTeam.id, request)
        } catch (error: Exception) {
            throw IncompleteTeamCreationException(createdTeam.id, error)
        }
        return createdTeam
    }

    fun retryLeaderMembership(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        request: CreateTeamRequest,
    ): CreatedTeam {
        ensureLeaderMembership(baseUrl.trim().trimEnd('/'), credentials, sessionCookie, teamId, request)
        return CreatedTeam(teamId, null)
    }

    private fun teamNameExists(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        name: String,
        entityType: String,
    ): Boolean {
        val selector = JSONObject()
            .put("name", JSONObject().put("\$regex", "(?i)^\\s*${escapeRegex(name.trim())}\\s*$"))
            .put("_id", JSONObject().put("\$ne", ""))
            .put("status", "active")
            .put("type", entityType)
        val payload = JSONObject()
            .put("selector", selector)
            .put("fields", JSONArray().put("_id"))
            .put("limit", 1)
            .put("skip", 0)
        return executeJson(baseUrl, "teams/_find", credentials, sessionCookie, payload)
            .getJSONArray("docs").length() > 0
    }

    private fun postTeam(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        request: CreateTeamRequest,
    ): CreatedTeam {
        val payload = JSONObject()
            .put("name", request.name.trim())
            .put("description", request.description.trim())
            .put("services", request.services.trim())
            .put("rules", request.rules.trim())
            .put("requests", JSONArray())
            .put("teamType", request.teamType)
            .put("public", request.isPublic)
            .put("limit", DEFAULT_TEAM_LIMIT)
            .put("status", "active")
            .put("createdDate", System.currentTimeMillis())
            .put("teamPlanetCode", request.planetCode)
            .put("parentCode", request.parentCode)
            .put("createdBy", request.userId)
            .put("type", request.entityType)
        val response = executeJson(baseUrl, "teams", credentials, sessionCookie, payload)
        val id = response.optString("id").takeIf(String::isNotBlank) ?: throw IOException("Missing created team id")
        return CreatedTeam(id, response.optString("rev").takeIf(String::isNotBlank))
    }

    private fun ensureLeaderMembership(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        request: CreateTeamRequest,
    ) {
        val membership = JSONObject()
            .put("teamId", teamId)
            .put("teamPlanetCode", request.planetCode)
            .put("teamType", request.teamType)
            .put("userId", request.userId)
            .put("userPlanetCode", request.planetCode)
            .put("docType", "membership")
            .put("isLeader", true)
        val findPayload = JSONObject().put("selector", membership).put("skip", 0).put("limit", 1000)
        val existing = executeJson(baseUrl, "teams/_find", credentials, sessionCookie, findPayload)
        if (existing.getJSONArray("docs").length() > 0) return

        val bulkPayload = JSONObject().put("docs", JSONArray().put(membership))
        val response = executeJsonArray(baseUrl, "teams/_bulk_docs", credentials, sessionCookie, bulkPayload)
        val result = response.optJSONObject(0) ?: throw IOException("Missing membership response")
        if (!result.optBoolean("ok")) throw IOException(result.optString("error", "Membership creation failed"))
    }

    private fun executeJson(
        baseUrl: String,
        path: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        payload: JSONObject,
    ): JSONObject = JSONObject(execute(baseUrl, path, credentials, sessionCookie, payload))

    private fun executeJsonArray(
        baseUrl: String,
        path: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        payload: JSONObject,
    ): JSONArray = JSONArray(execute(baseUrl, path, credentials, sessionCookie, payload))

    private fun execute(
        baseUrl: String,
        path: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        payload: JSONObject,
    ): String {
        val builder = Request.Builder()
            .url("$baseUrl/db/$path")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        credentials?.let { builder.header("Authorization", Credentials.basic(it.username, it.password)) }
        sessionCookie?.takeIf(String::isNotBlank)?.let { builder.header("Cookie", it) }
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            response.body.string()
        }
    }

    private fun validateRequest(request: CreateTeamRequest) {
        if (request.name.isBlank()) throw IllegalArgumentException("Missing team name")
        if (request.planetCode.isBlank()) throw IllegalArgumentException("Missing planet code")
        if (request.parentCode.isBlank()) throw IllegalArgumentException("Missing parent code")
        if (request.userId.isBlank()) throw IllegalArgumentException("Missing user id")
    }

    private fun escapeRegex(value: String): String {
        val metaCharacters = "\\.^\$|?*+()[]{}"
        return buildString {
            value.forEach { character ->
                if (character in metaCharacters) append('\\')
                append(character)
            }
        }
    }

    private companion object {
        const val DEFAULT_TEAM_LIMIT = 12
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class DuplicateTeamNameException : IOException("An active team with this name already exists")
