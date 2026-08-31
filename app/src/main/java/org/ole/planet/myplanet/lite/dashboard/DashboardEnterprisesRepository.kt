package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class DashboardEnterprisesRepository(
    private val client: OkHttpClient = SharedBitmapDependencies.client,
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val teamsResponseAdapter = moshi.adapter(TeamsFindResponse::class.java)
    private val relationshipsResponseAdapter = moshi.adapter(MembershipFindResponse::class.java)
    private val enterpriseAdapter = moshi.adapter(TeamDocument::class.java)

    suspend fun fetchSnapshot(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        userId: String,
        userPlanetCode: String,
    ): Result<EnterpriseSnapshot> = withContext(dispatcher) {
        runCatching {
            val enterprises = findEnterprises(baseUrl, credentials, sessionCookie)
            val relationships = findRelationships(
                baseUrl,
                credentials,
                sessionCookie,
                userId,
                userPlanetCode,
            )
            val enterpriseIds = enterprises.mapNotNull { it.id }.toSet()
            val relevant = relationships.filter { it.teamId in enterpriseIds }
            val memberships = relevant
                .filter { it.docType == "membership" }
                .mapNotNull { relationship -> relationship.teamId?.let { it to relationship } }
                .toMap()
            val requests = relevant
                .filter { it.docType == "request" && it.teamId !in memberships }
                .mapNotNull { relationship ->
                    relationship.teamId?.let { id -> id to relationship.toJoinRequest() }
                }.toMap()
            EnterpriseSnapshot(enterprises, memberships, requests)
        }
    }

    suspend fun hasExistingRelationship(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        userId: String,
        userPlanetCode: String,
    ): Result<Boolean> = withContext(dispatcher) {
        runCatching {
            findRelationships(baseUrl, credentials, sessionCookie, userId, userPlanetCode)
                .any { it.teamId == teamId && it.docType in setOf("membership", "request") }
        }
    }

    suspend fun fetchEnterpriseMembers(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        enterpriseId: String,
        currentUserId: String,
        currentUserPlanetCode: String,
    ): Result<EnterpriseMembersResult> = withContext(dispatcher) {
        runCatching {
            val enterprise = fetchEnterprise(baseUrl, credentials, sessionCookie, enterpriseId)
            val enterprisePlanetCode = enterprise.teamPlanetCode
                ?: throw IOException("Enterprise has no planet code")
            val currentMemberships = findMemberships(
                baseUrl,
                credentials,
                sessionCookie,
                enterpriseId,
                currentUserId,
                currentUserPlanetCode,
                limit = 1,
            )
            if (currentMemberships.isEmpty()) return@runCatching EnterpriseMembersResult.NotMember

            val modernMemberships = findMemberships(
                baseUrl,
                credentials,
                sessionCookie,
                enterpriseId,
                userId = null,
                userPlanetCode = null,
                teamPlanetCode = enterprisePlanetCode,
                limit = 1000,
            )
            val legacyMemberships = findLegacyMemberships(
                baseUrl,
                credentials,
                sessionCookie,
                enterpriseId,
                enterprisePlanetCode,
            )
            val memberships = (modernMemberships + legacyMemberships)
                .distinctBy { "${it.userId}@${it.userPlanetCode}" }
            val profiles = findProfiles(
                baseUrl,
                credentials,
                sessionCookie,
                memberships.mapNotNull { it.userId }.distinct(),
            )
            val hasExplicitLeader = memberships.any { it.isLeader == true }
            val members = memberships.mapNotNull { membership ->
                val userId = membership.userId ?: return@mapNotNull null
                val userPlanet = membership.userPlanetCode
                val profile = profiles.firstOrNull { profile ->
                    (profile.id == userId || profile.couchId == userId) &&
                        (userPlanet.isNullOrBlank() || profile.planetCode.isNullOrBlank() || profile.planetCode == userPlanet)
                }
                val username = userId.substringAfter("org.couchdb.user:", userId)
                val fullName = listOfNotNull(profile?.firstName, profile?.middleName, profile?.lastName)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                    .ifBlank { username }
                val fallbackLeader = !hasExplicitLeader &&
                    userId == enterprise.createdBy &&
                    (userPlanet.isNullOrBlank() || userPlanet == enterprisePlanetCode)
                TeamMemberDetails(
                    username = username,
                    fullName = fullName,
                    isLeader = membership.isLeader == true || fallbackLeader,
                    hasAvatar = profile?.hasAvatar == true,
                    membership = membership,
                    role = membership.role,
                    userId = userId,
                    userPlanetCode = userPlanet,
                )
            }.sortedWith(compareByDescending<TeamMemberDetails> { it.isLeader }.thenBy { it.fullName?.lowercase() })
            EnterpriseMembersResult.Success(enterprise, members)
        }
    }

    private fun fetchEnterprise(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        enterpriseId: String,
    ): TeamDocument {
        val body = executeGet(baseUrl, "teams/$enterpriseId", credentials, sessionCookie)
        val enterprise = enterpriseAdapter.fromJson(body) ?: throw IOException("Invalid enterprise response")
        if (enterprise.type != "enterprise" || enterprise.status != "active") {
            throw IOException("Enterprise is not active")
        }
        return enterprise
    }

    private fun findMemberships(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        enterpriseId: String,
        userId: String?,
        userPlanetCode: String?,
        teamPlanetCode: String? = null,
        limit: Int,
    ): List<MembershipDocument> {
        val selector = JSONObject().put("teamId", enterpriseId).put("docType", "membership")
        userId?.let { selector.put("userId", it) }
        userPlanetCode?.let { selector.put("userPlanetCode", it) }
        teamPlanetCode?.let { selector.put("teamPlanetCode", it) }
        if (teamPlanetCode != null) {
            selector.put("status", JSONObject().put("$" + "or", JSONArray()
                .put(JSONObject().put("$" + "exists", false))
                .put(JSONObject().put("$" + "ne", "archived"))))
        }
        val body = executeFind(baseUrl, credentials, sessionCookie, selector, limit)
        return relationshipsResponseAdapter.fromJson(body)?.docs.orEmpty()
            .filter { it.docType == "membership" }
    }

    private fun findLegacyMemberships(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        enterpriseId: String,
        enterprisePlanetCode: String,
    ): List<MembershipDocument> {
        val selector = JSONObject().put("myTeamIds", JSONObject().put("$" + "in", JSONArray().put(enterpriseId)))
        val body = runCatching {
            executeDatabaseFind(baseUrl, "shelf", credentials, sessionCookie, selector, 1000)
        }.getOrElse { return emptyList() }
        return JSONObject(body).optJSONArray("docs")?.let { docs ->
            (0 until docs.length()).mapNotNull { index ->
                docs.optJSONObject(index)?.optString("_id")?.takeIf(String::isNotBlank)?.let { id ->
                    MembershipDocument(null, null, enterpriseId, id, enterprisePlanetCode, "sync", enterprisePlanetCode, "membership", false, null)
                }
            }
        }.orEmpty()
    }

    private fun findProfiles(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        userIds: List<String>,
    ): List<EnterpriseProfile> {
        if (userIds.isEmpty()) return emptyList()
        val ids = JSONArray().apply { userIds.forEach(::put) }
        val selector = JSONObject().put("$" + "or", JSONArray()
            .put(JSONObject().put("_id", JSONObject().put("$" + "in", ids)))
            .put(JSONObject().put("couchId", JSONObject().put("$" + "in", ids))))
        return listOf("_users", "child_users", "parent_users").flatMap { database ->
            val body = runCatching {
                executeDatabaseFind(baseUrl, database, credentials, sessionCookie, selector, 1000)
            }.getOrElse { return@flatMap emptyList() }
            val docs = JSONObject(body).optJSONArray("docs") ?: return@flatMap emptyList()
            (0 until docs.length()).mapNotNull { index -> docs.optJSONObject(index)?.toEnterpriseProfile() }
        }.distinctBy { "${it.id ?: it.couchId}@${it.planetCode}" }
    }

    private fun JSONObject.toEnterpriseProfile() = EnterpriseProfile(
        id = optString("_id").takeIf(String::isNotBlank),
        couchId = optString("couchId").takeIf(String::isNotBlank),
        planetCode = optString("planetCode").takeIf(String::isNotBlank),
        firstName = optString("firstName").takeIf(String::isNotBlank),
        middleName = optString("middleName").takeIf(String::isNotBlank),
        lastName = optString("lastName").takeIf(String::isNotBlank),
        hasAvatar = optJSONObject("_attachments")?.length()?.let { it > 0 } == true,
    )

    private fun executeGet(
        baseUrl: String,
        path: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
    ): String = executeRequest(baseUrl, path, credentials, sessionCookie, null)

    private fun findEnterprises(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
    ): List<TeamDocument> {
        val selector = JSONObject().put("type", "enterprise").put("status", "active")
        val body = executeFind(baseUrl, credentials, sessionCookie, selector, 1000)
        return teamsResponseAdapter.fromJson(body)?.docs.orEmpty()
            .filter { it.type == "enterprise" && it.status == "active" }
    }

    private fun findRelationships(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        userId: String,
        userPlanetCode: String,
    ): List<MembershipDocument> {
        val selector = JSONObject()
            .put("userId", userId)
            .put("userPlanetCode", userPlanetCode)
        val body = executeFind(baseUrl, credentials, sessionCookie, selector, 1000)
        return relationshipsResponseAdapter.fromJson(body)?.docs.orEmpty()
    }

    private fun executeFind(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        selector: JSONObject,
        limit: Int,
    ): String {
        return executeDatabaseFind(baseUrl, "teams", credentials, sessionCookie, selector, limit)
    }

    private fun executeDatabaseFind(
        baseUrl: String,
        database: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        selector: JSONObject,
        limit: Int,
    ): String {
        val payload = JSONObject().put("selector", selector).put("limit", limit).toString()
        return executeRequest(baseUrl, "$database/_find", credentials, sessionCookie, payload)
    }

    private fun executeRequest(
        baseUrl: String,
        path: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        payload: String?,
    ): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        val builder = Request.Builder()
            .url("$normalizedBase/db/$path")
            .header("Content-Type", "application/json")
        if (payload == null) builder.get() else builder.post(payload.toRequestBody(JSON_MEDIA_TYPE))
        credentials?.let { builder.header("Authorization", Credentials.basic(it.username, it.password)) }
        sessionCookie?.takeIf(String::isNotBlank)?.let { builder.header("Cookie", it) }
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            response.body.string()
        }
    }

    private fun MembershipDocument.toJoinRequest() = JoinRequestDocument(
        id = id,
        revision = revision,
        docType = docType,
        teamId = teamId,
        teamType = teamType,
        teamPlanetCode = teamPlanetCode,
        userId = userId,
        userPlanetCode = userPlanetCode,
    )

    data class EnterpriseSnapshot(
        val enterprises: List<TeamDocument>,
        val membershipsByEnterpriseId: Map<String, MembershipDocument>,
        val requestsByEnterpriseId: Map<String, JoinRequestDocument>,
    )

    sealed interface EnterpriseMembersResult {
        data object NotMember : EnterpriseMembersResult
        data class Success(val enterprise: TeamDocument, val members: List<TeamMemberDetails>) : EnterpriseMembersResult
    }

    private data class EnterpriseProfile(
        val id: String?,
        val couchId: String?,
        val planetCode: String?,
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val hasAvatar: Boolean,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
