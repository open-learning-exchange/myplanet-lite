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

internal class DashboardTeamsOperations(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val teamMemberDetailsCache: java.util.concurrent.ConcurrentHashMap<String, List<TeamMemberDetails>>,
) {
    private val membershipRequestAdapter = moshi.adapter(MembershipFindRequest::class.java)
    private val membershipResponseAdapter = moshi.adapter(MembershipFindResponse::class.java)
    private val teamMembershipRequestAdapter = moshi.adapter(TeamMembershipFindRequest::class.java)
    private val multipleMemberCountRequestAdapter = moshi.adapter(MultipleMemberCountFindRequest::class.java)
    private val multipleMemberCountResponseAdapter = moshi.adapter(MultipleMemberCountFindResponse::class.java)
    private val teamsRequestAdapter = moshi.adapter(TeamsFindRequest::class.java)
    private val teamsResponseAdapter = moshi.adapter(TeamsFindResponse::class.java)
    private val availableTeamsRequestAdapter = moshi.adapter(NonMemberTeamsFindRequest::class.java)
    private val joinRequestFindAdapter = moshi.adapter(JoinRequestFindRequest::class.java)
    private val teamJoinRequestFindAdapter = moshi.adapter(TeamJoinRequestFindRequest::class.java)
    private val joinRequestFindResponseAdapter = moshi.adapter(JoinRequestFindResponse::class.java)
    private val joinTeamRequestAdapter = moshi.adapter(JoinTeamRequest::class.java)
    private val deleteJoinRequestAdapter = moshi.adapter(DeleteDocumentRequest::class.java)
    private val deleteMembershipAdapter = moshi.adapter(DeleteDocumentRequest::class.java)
    private val membershipBulkDeleteAdapter = moshi.adapter(BulkMembershipDeleteRequest::class.java)
    private val membershipBulkAddAdapter = moshi.adapter(BulkMembershipAddRequest::class.java)
    private val usersFindResponseAdapter = moshi.adapter(UsersFindResponse::class.java)

    fun addTeamMember(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        request: AddTeamMemberRequest,
    ) {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        if (request.teamId.isBlank()) throw IOException("Missing team id")
        if (request.teamPlanetCode.isBlank()) throw IOException("Missing team planet code")
        if (request.userId.isBlank()) throw IOException("Missing user id")
        if (request.userPlanetCode.isBlank()) throw IOException("Missing user planet code")

        val payload = membershipBulkAddAdapter.toJson(
            BulkMembershipAddRequest(
                docs = listOf(
                    BulkMembershipAddDoc(
                        teamId = request.teamId,
                        teamPlanetCode = request.teamPlanetCode,
                        teamType = request.teamType.nullIfBlank() ?: "local",
                        userId = request.userId,
                        userPlanetCode = request.userPlanetCode,
                        docType = "membership",
                        isLeader = false,
                    ),
                ),
            ),
        )
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams/_bulk_docs")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            teamMemberDetailsCache.remove(request.teamId)
        }
    }

    fun fetchMemberships(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        username: String,
    ): List<MembershipDocument> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        if (username.isBlank()) throw IOException("Missing username")

        val selector = MembershipSelector(
            userId = "org.couchdb.user:$username",
            teamType = "local",
            docType = "membership",
            status = activeStatusClause(),
        )
        val payload = membershipRequestAdapter.toJson(MembershipFindRequest(selector))
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            membershipResponseAdapter.fromJson(response.body.string())?.docs ?: emptyList()
        }
    }

    fun fetchTeamMemberDetails(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
    ): List<TeamMemberDetails> {
        teamMemberDetailsCache[teamId]?.let { return it }

        val memberships = fetchTeamMembers(baseUrl, credentials, sessionCookie, teamId)
        val normalizedBase = baseUrl.trim().trimEnd('/')
        val validMemberships = memberships.filter { !it.userId.isNullOrBlank() }
        val userIds = validMemberships.mapNotNull { it.userId }

        val profiles = if (userIds.isNotEmpty()) {
            fetchUserProfiles(normalizedBase, credentials, sessionCookie, userIds)
        } else {
            emptyList()
        }

        val profileMap = profiles.associateBy { it._id }
        val details = validMemberships.mapNotNull { member ->
            val userId = member.userId ?: return@mapNotNull null
            val username = userId.substringAfter("org.couchdb.user:")
            if (username.isBlank()) return@mapNotNull null

            val profile = profileMap[userId]
            val fullName = listOfNotNull(profile?.firstName, profile?.middleName, profile?.lastName)
                .joinToString(" ")
                .ifBlank { username }

            TeamMemberDetails(
                username = username,
                fullName = fullName,
                isLeader = member.isLeader == true,
                hasAvatar = profile?.attachments?.image != null,
                membership = member,
            )
        }

        teamMemberDetailsCache[teamId] = details
        return details
    }

    fun fetchTeamMembers(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
    ): List<MembershipDocument> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        if (teamId.isBlank()) throw IOException("Missing team id")

        val selector = TeamMembershipSelector(teamId = teamId, docType = "membership", status = activeStatusClause())
        val payload = teamMembershipRequestAdapter.toJson(TeamMembershipFindRequest(selector))
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            membershipResponseAdapter.fromJson(response.body.string())?.docs ?: emptyList()
        }
    }

    fun removeTeamMember(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        membership: MembershipDocument,
    ) {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")

        val id = membership.id.nullIfBlank() ?: throw IOException("Missing membership id")
        val revision = membership.revision.nullIfBlank() ?: throw IOException("Missing membership revision")
        val teamId = membership.teamId.nullIfBlank() ?: throw IOException("Missing team id")
        val teamPlanetCode = membership.teamPlanetCode.nullIfBlank() ?: throw IOException("Missing team planet code")
        val userId = membership.userId.nullIfBlank() ?: throw IOException("Missing user id")
        val userPlanetCode = membership.userPlanetCode.nullIfBlank() ?: throw IOException("Missing user planet code")
        val teamType = membership.teamType.nullIfBlank() ?: "local"
        val docType = membership.docType.nullIfBlank() ?: "membership"

        val payload = membershipBulkDeleteAdapter.toJson(
            BulkMembershipDeleteRequest(
                docs = listOf(
                    BulkMembershipDeleteDoc(
                        id = id,
                        revision = revision,
                        teamId = teamId,
                        teamPlanetCode = teamPlanetCode,
                        teamType = teamType,
                        userId = userId,
                        userPlanetCode = userPlanetCode,
                        docType = docType,
                        isLeader = false,
                        deleted = true,
                    ),
                ),
            ),
        )
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams/_bulk_docs")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            teamMemberDetailsCache.remove(teamId)
        }
    }

    fun fetchTeams(baseUrl: String, credentials: StoredCredentials?, sessionCookie: String?, teamIds: List<String>): List<TeamDocument> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        if (teamIds.isEmpty()) return emptyList()

        val selector = TeamsSelector(status = "active", type = "team", teamType = "local", ids = IdsInClause(ids = teamIds))
        val payload = teamsRequestAdapter.toJson(TeamsFindRequest(selector))
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            teamsResponseAdapter.fromJson(response.body.string())?.docs ?: emptyList()
        }
    }

    fun fetchAvailableTeams(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        excludedTeamIds: List<String>,
        skip: Int = 0,
        limit: Int = 25,
    ): List<TeamDocument> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")

        val selector = NonMemberTeamsSelector(
            ids = IdsNotInClause(ids = excludedTeamIds),
            status = "active",
            type = "team",
            teamType = "local",
        )
        val payload = availableTeamsRequestAdapter.toJson(NonMemberTeamsFindRequest(selector = selector, limit = limit, skip = skip))
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            teamsResponseAdapter.fromJson(response.body.string())?.docs ?: emptyList()
        }
    }

    fun fetchJoinRequests(baseUrl: String, credentials: StoredCredentials?, sessionCookie: String?, userId: String): List<JoinRequestDocument> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        if (userId.isBlank()) throw IOException("Missing user id")

        val selector = JoinRequestSelector(docType = "request", teamType = "local", userId = userId)
        val payload = joinRequestFindAdapter.toJson(JoinRequestFindRequest(selector))
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            joinRequestFindResponseAdapter.fromJson(response.body.string())?.docs ?: emptyList()
        }
    }

    fun fetchTeamJoinRequests(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        teamPlanetCode: String,
    ): List<JoinRequestDocument> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        if (teamId.isBlank()) throw IOException("Missing team id")
        if (teamPlanetCode.isBlank()) throw IOException("Missing team planet code")

        val selector = TeamJoinRequestSelector(
            teamId = teamId,
            teamPlanetCode = teamPlanetCode,
            docType = "request",
            status = activeStatusClause(),
        )
        val payload = teamJoinRequestFindAdapter.toJson(TeamJoinRequestFindRequest(selector))
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            joinRequestFindResponseAdapter.fromJson(response.body.string())?.docs ?: emptyList()
        }
    }

    fun hasExistingJoinRequest(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        userId: String,
    ): Boolean {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        if (teamId.isBlank()) throw IOException("Missing team id")
        if (userId.isBlank()) throw IOException("Missing user id")

        val selector = JoinRequestSelector(docType = "request", teamType = "local", userId = userId, teamId = teamId)
        val payload = joinRequestFindAdapter.toJson(JoinRequestFindRequest(selector))
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            val docs = joinRequestFindResponseAdapter.fromJson(response.body.string())?.docs
            !docs.isNullOrEmpty()
        }
    }

    fun fetchMemberCounts(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamIds: List<String>,
    ): Map<String, Int> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        if (teamIds.isEmpty()) return emptyMap()

        val selector = MultipleMemberCountSelector(teamId = IdsInClause(ids = teamIds), docType = "membership", status = activeStatusClause())
        val payload = multipleMemberCountRequestAdapter.toJson(
            MultipleMemberCountFindRequest(selector = selector, fields = listOf("_id", "teamId"), limit = 50000),
        )
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        val allDocs = client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            multipleMemberCountResponseAdapter.fromJson(response.body.string())?.docs ?: emptyList()
        }

        return buildMap {
            allDocs.forEach { doc ->
                doc.teamId?.let { teamId -> put(teamId, (get(teamId) ?: 0) + 1) }
            }
        }
    }

    fun requestTeamMembership(baseUrl: String, credentials: StoredCredentials?, sessionCookie: String?, request: JoinTeamRequest) {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        if (request.teamId.isBlank()) throw IOException("Missing team id")
        if (request.userId.isBlank()) throw IOException("Missing user id")

        val payload = joinTeamRequestAdapter.toJson(request)
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            teamMemberDetailsCache.remove(request.teamId)
        }
    }

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

    fun cancelJoinRequest(baseUrl: String, credentials: StoredCredentials?, sessionCookie: String?, documentId: String, revision: String) {
        cancelDocument(baseUrl, credentials, sessionCookie, documentId, revision, deleteJoinRequestAdapter)
    }

    fun cancelMembership(baseUrl: String, credentials: StoredCredentials?, sessionCookie: String?, documentId: String, revision: String) {
        cancelDocument(baseUrl, credentials, sessionCookie, documentId, revision, deleteMembershipAdapter)
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
        val payload = moshi.adapter(UsersFindRequest::class.java).toJson(UsersFindRequest(selector))
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/_users/_find")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", basicAuth)
            .header("Content-Type", "application/json")
        sessionCookie.nullIfBlank()?.let { requestBuilder.addHeader("Cookie", it) }

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            moshi.adapter(UsersFindResponse::class.java).fromJson(response.body.string())?.docs ?: emptyList()
        }
    }

    fun fetchAllUsers(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        planetCode: String?,
        parentCode: String?,
        pageSize: Int = 25,
        skip: Int = 0,
        searchTerm: String? = null,
        excludedUserIds: List<String> = emptyList(),
    ): List<UserDocument> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        val basicAuth = credentials?.let { Credentials.basic(it.username, it.password) }
            ?: throw IOException("Missing credentials for basic auth")
        if (pageSize <= 0) return emptyList()

        val filteredPlanet = planetCode.nullIfBlank() ?: throw IOException("Missing planet code for user search")
        val filteredParent = parentCode.nullIfBlank() ?: throw IOException("Missing parent code for user search")
        val payload = buildUsersFindPayload(filteredPlanet, filteredParent, pageSize, skip, searchTerm, excludedUserIds)

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

    private fun cancelDocument(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        documentId: String,
        revision: String,
        adapter: com.squareup.moshi.JsonAdapter<DeleteDocumentRequest>,
    ) {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
        if (documentId.isBlank()) throw IOException("Missing document id")
        if (revision.isBlank()) throw IOException("Missing document revision")

        val payload = adapter.toJson(DeleteDocumentRequest(id = documentId, revision = revision, deleted = true))
        val requestBuilder = Request.Builder()
            .url("$normalizedBase/db/teams")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .withAuth(credentials, sessionCookie)

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            teamMemberDetailsCache.clear()
        }
    }

    private fun fetchUserProfile(
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
        val filteredExcludedIds = excludedUserIds.filter { it.isNotBlank() }
        val selector = JSONObject().put("planetCode", planetCode).put("parentCode", parentCode)

        if (filteredExcludedIds.isNotEmpty()) {
            val excludedArray = JSONArray()
            filteredExcludedIds.forEach { excludedArray.put(it) }
            selector.put("_id", JSONObject().put("$" + "nin", excludedArray))
        }

        if (!searchTerm.isNullOrBlank()) {
            val regexValue = "(?i)${searchTerm.trim()}"
            val orArray = JSONArray()
            listOf("name", "firstName", "middleName", "lastName").forEach { field ->
                val regexObject = JSONObject().put("$" + "regex", regexValue)
                orArray.put(JSONObject().put(field, regexObject))
            }
            selector.put("$" + "or", orArray)
        }

        return JSONObject().put("selector", selector).put("skip", skip).put("limit", pageSize).toString()
    }

    private fun activeStatusClause(): StatusClause {
        return StatusClause(or = listOf(StatusCondition(exists = false), StatusCondition(notEquals = "archived")))
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
