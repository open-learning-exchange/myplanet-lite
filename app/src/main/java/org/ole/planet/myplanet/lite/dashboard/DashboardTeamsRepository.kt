/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite.dashboard

import org.ole.planet.myplanet.lite.profile.StoredCredentials
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.util.nullIfBlank

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class BirthDateString

class DateStringAdapter {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    @FromJson
    @BirthDateString
    fun fromJson(dateString: String?): Long? {
        return if (dateString == null) {
            null
        } else {
            try {
                dateFormat.parse(dateString)?.time
            } catch (e: Exception) {
                // It might already be a long
                dateString.toLongOrNull()
            }
        }
    }

    @ToJson
    fun toJson(@BirthDateString value: Long?): String? {
        return if (value == null) {
            null
        } else {
            dateFormat.format(value)
        }
    }
}

class DashboardTeamsRepository {

    private val client: OkHttpClient = OkHttpClient.Builder().build()
    private val moshi: Moshi = Moshi.Builder()
        .add(DateStringAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val membershipRequestAdapter = moshi.adapter(MembershipFindRequest::class.java)
    private val membershipResponseAdapter = moshi.adapter(MembershipFindResponse::class.java)
    private val teamMembershipRequestAdapter = moshi.adapter(TeamMembershipFindRequest::class.java)
    private val memberCountRequestAdapter = moshi.adapter(MemberCountFindRequest::class.java)
    private val memberCountResponseAdapter = moshi.adapter(MemberCountFindResponse::class.java)
    private val teamsRequestAdapter = moshi.adapter(TeamsFindRequest::class.java)
    private val teamsResponseAdapter = moshi.adapter(TeamsFindResponse::class.java)
    private val availableTeamsRequestAdapter = moshi.adapter(NonMemberTeamsFindRequest::class.java)
    private val joinRequestFindAdapter = moshi.adapter(JoinRequestFindRequest::class.java)
    private val joinRequestFindResponseAdapter = moshi.adapter(JoinRequestFindResponse::class.java)
    private val joinTeamRequestAdapter = moshi.adapter(JoinTeamRequest::class.java)
    private val deleteJoinRequestAdapter = moshi.adapter(DeleteDocumentRequest::class.java)
    private val deleteMembershipAdapter = moshi.adapter(DeleteDocumentRequest::class.java)
    private val membershipBulkDeleteAdapter = moshi.adapter(BulkMembershipDeleteRequest::class.java)
    private val membershipBulkAddAdapter = moshi.adapter(BulkMembershipAddRequest::class.java)
    private val usersFindResponseAdapter = moshi.adapter(UsersFindResponse::class.java)

    suspend fun fetchUserProfile(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        userId: String
    ): Result<UserDocument> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (userId.isBlank()) {
                    throw IOException("Missing user id")
                }
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/_users/$userId")
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    val users = usersFindResponseAdapter.fromJson(body)?.docs ?: emptyList()
                    if (users.isEmpty()) {
                        throw IOException("User not found")
                    }
                    users.first()
                }
            }
        }
    }

    suspend fun addTeamMember(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        teamPlanetCode: String,
        teamType: String = "local",
        userId: String,
        userPlanetCode: String,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (teamId.isBlank()) {
                    throw IOException("Missing team id")
                }
                if (teamPlanetCode.isBlank()) {
                    throw IOException("Missing team planet code")
                }
                if (userId.isBlank()) {
                    throw IOException("Missing user id")
                }
                if (userPlanetCode.isBlank()) {
                    throw IOException("Missing user planet code")
                }

                val payload = membershipBulkAddAdapter.toJson(
                    BulkMembershipAddRequest(
                        docs = listOf(
                            BulkMembershipAddDoc(
                                teamId = teamId,
                                teamPlanetCode = teamPlanetCode,
                                teamType = teamType.nullIfBlank() ?: "local",
                                userId = userId,
                                userPlanetCode = userPlanetCode,
                                docType = "membership",
                                isLeader = false,
                            )
                        )
                    )
                )
                val bulkAddUrl = "$normalizedBase/db/teams/_bulk_docs"
                val requestBuilder = Request.Builder()
                    .url(bulkAddUrl)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                }
            }
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
        val selector = JSONObject()
            .put("planetCode", planetCode)
            .put("parentCode", parentCode)

        if (filteredExcludedIds.isNotEmpty()) {
            val excludedArray = JSONArray()
            filteredExcludedIds.forEach { excludedArray.put(it) }
            selector.put("_id", JSONObject().put("\$nin", excludedArray))
        }

        if (!searchTerm.isNullOrBlank()) {
            val regexValue = "(?i)${searchTerm.trim()}"
            val orArray = JSONArray()
            listOf("name", "firstName", "middleName", "lastName").forEach { field ->
                val regexObject = JSONObject().put("\$regex", regexValue)
                orArray.put(JSONObject().put(field, regexObject))
            }
            selector.put("\$or", orArray)
        }

        return JSONObject()
            .put("selector", selector)
            .put("skip", skip)
            .put("limit", pageSize)
            .toString()
    }

    suspend fun fetchMemberships(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        username: String
    ): Result<List<MembershipDocument>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (username.isBlank()) {
                    throw IOException("Missing username")
                }
                val selector = MembershipSelector(
                    userId = "org.couchdb.user:$username",
                    teamType = "local",
                    docType = "membership",
                    status = StatusClause(
                        or = listOf(
                            StatusCondition(exists = false),
                            StatusCondition(notEquals = "archived")
                        )
                    )
                )
                val payload = membershipRequestAdapter.toJson(MembershipFindRequest(selector))
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/teams/_find")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    membershipResponseAdapter.fromJson(body)?.docs ?: emptyList()
                }
            }
        }
    }

    suspend fun fetchTeamMemberDetails(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
    ): Result<List<TeamMemberDetails>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val memberships = fetchTeamMembers(baseUrl, credentials, sessionCookie, teamId)
                    .getOrThrow()
                val normalizedBase = baseUrl.trim().trimEnd('/')

                val validMemberships = memberships.filter { !it.userId.isNullOrBlank() }
                val userIds = validMemberships.mapNotNull { it.userId }

                val profiles = if (userIds.isNotEmpty()) {
                    fetchUserProfiles(normalizedBase, credentials, sessionCookie, userIds).getOrNull() ?: emptyList()
                } else {
                    emptyList()
                }

                val profileMap = profiles.associateBy { it._id }

                validMemberships.mapNotNull { member ->
                    val userId = member.userId!!
                    val username = userId.substringAfter("org.couchdb.user:")
                    if (username.isBlank()) {
                        return@mapNotNull null
                    }
                    val profile = profileMap[userId]
                    val fullName = listOfNotNull(
                        profile?.firstName,
                        profile?.middleName,
                        profile?.lastName,
                    ).joinToString(" ").ifBlank { username }

                    TeamMemberDetails(
                        username = username,
                        fullName = fullName,
                        isLeader = member.isLeader == true,
                        hasAvatar = profile?.attachments?.image != null,
                        membership = member,
                    )
                }
            }
        }
    }

    suspend fun fetchTeamMembers(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
    ): Result<List<MembershipDocument>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (teamId.isBlank()) {
                    throw IOException("Missing team id")
                }

                val selector = TeamMembershipSelector(
                    teamId = teamId,
                    docType = "membership",
                    status = StatusClause(
                        or = listOf(
                            StatusCondition(exists = false),
                            StatusCondition(notEquals = "archived"),
                        ),
                    ),
                )
                val payload = teamMembershipRequestAdapter.toJson(TeamMembershipFindRequest(selector))
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/teams/_find")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    membershipResponseAdapter.fromJson(body)?.docs ?: emptyList()
                }
            }
        }
    }

    suspend fun removeTeamMember(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        membership: MembershipDocument,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }

                val id = membership.id.nullIfBlank() ?: throw IOException("Missing membership id")
                val revision = membership.revision.nullIfBlank()
                    ?: throw IOException("Missing membership revision")
                val teamId = membership.teamId.nullIfBlank() ?: throw IOException("Missing team id")
                val teamPlanetCode = membership.teamPlanetCode.nullIfBlank()
                    ?: throw IOException("Missing team planet code")
                val userId = membership.userId.nullIfBlank() ?: throw IOException("Missing user id")
                val userPlanetCode = membership.userPlanetCode.nullIfBlank()
                    ?: throw IOException("Missing user planet code")
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
                val bulkDeleteUrl = "$normalizedBase/db/teams/_bulk_docs"
                val requestBuilder = Request.Builder()
                    .url(bulkDeleteUrl)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                }
            }
        }
    }

    suspend fun fetchTeams(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamIds: List<String>
    ): Result<List<TeamDocument>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (teamIds.isEmpty()) {
                    return@runCatching emptyList()
                }
                val selector = TeamsSelector(
                    status = "active",
                    type = "team",
                    teamType = "local",
                    ids = IdsInClause(ids = teamIds)
                )
                val payload = teamsRequestAdapter.toJson(TeamsFindRequest(selector))
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/teams/_find")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    teamsResponseAdapter.fromJson(body)?.docs ?: emptyList()
                }
            }
        }
    }

    suspend fun fetchAvailableTeams(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        excludedTeamIds: List<String>,
        skip: Int = 0,
        limit: Int = 25
    ): Result<List<TeamDocument>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }

                val selector = NonMemberTeamsSelector(
                    ids = IdsNotInClause(ids = excludedTeamIds),
                    status = "active",
                    type = "team",
                    teamType = "local"
                )
                val payload = availableTeamsRequestAdapter.toJson(
                    NonMemberTeamsFindRequest(
                        selector = selector,
                        limit = limit,
                        skip = skip,
                    )
                )
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/teams/_find")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    teamsResponseAdapter.fromJson(body)?.docs ?: emptyList()
                }
            }
        }
    }

    suspend fun fetchJoinRequests(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        userId: String,
    ): Result<List<JoinRequestDocument>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (userId.isBlank()) {
                    throw IOException("Missing user id")
                }

                val selector = JoinRequestSelector(
                    docType = "request",
                    teamType = "local",
                    userId = userId,
                )
                val payload = joinRequestFindAdapter.toJson(JoinRequestFindRequest(selector))
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/teams/_find")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    joinRequestFindResponseAdapter.fromJson(body)?.docs ?: emptyList()
                }
            }
        }
    }

    suspend fun hasExistingJoinRequest(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        userId: String,
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (teamId.isBlank()) {
                    throw IOException("Missing team id")
                }
                if (userId.isBlank()) {
                    throw IOException("Missing user id")
                }

                val selector = JoinRequestSelector(
                    docType = "request",
                    teamType = "local",
                    userId = userId,
                    teamId = teamId,
                )
                val payload = joinRequestFindAdapter.toJson(JoinRequestFindRequest(selector))
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/teams/_find")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    val docs = joinRequestFindResponseAdapter.fromJson(body)?.docs
                    !docs.isNullOrEmpty()
                }
            }
        }
    }

    suspend fun fetchMemberCount(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (teamId.isBlank()) {
                    throw IOException("Missing team id")
                }

                val selector = MemberCountSelector(
                    teamId = teamId,
                    docType = "membership",
                    status = StatusClause(
                        or = listOf(
                            StatusCondition(exists = false),
                            StatusCondition(notEquals = "archived")
                        )
                    )
                )
                val payload = memberCountRequestAdapter.toJson(
                    MemberCountFindRequest(selector = selector, fields = listOf("_id"))
                )
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/teams/_find")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    val docs = memberCountResponseAdapter.fromJson(body)?.docs
                    docs?.size ?: 0
                }
            }
        }
    }

    suspend fun requestTeamMembership(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        request: JoinTeamRequest,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (request.teamId.isBlank()) {
                    throw IOException("Missing team id")
                }
                if (request.userId.isBlank()) {
                    throw IOException("Missing user id")
                }

                val payload = joinTeamRequestAdapter.toJson(request)
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/teams")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                }
            }
        }
    }

    private fun fetchUserProfile(
        baseUrl: String,
        username: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
    ): TeamMemberProfileDetails? {
        if (baseUrl.isBlank() || username.isBlank()) {
            return null
        }
        val requestBuilder = Request.Builder()
            .url("$baseUrl/db/_users/org.couchdb.user:$username")
            .get()
        credentials?.let {
            requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
        }
        sessionCookie.nullIfBlank()?.let { cookie ->
            requestBuilder.addHeader("Cookie", cookie)
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
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
        } catch (error: IOException) {
            null
        }
    }

    suspend fun fetchTeamMemberProfileDetails(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        username: String,
    ): Result<TeamMemberProfileDetails> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing base url")
                }
                fetchUserProfile(normalizedBase, username, credentials, sessionCookie)
                    ?: throw IOException("Profile not found")
            }
        }
    }

    suspend fun cancelJoinRequest(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        documentId: String,
        revision: String,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (documentId.isBlank()) {
                    throw IOException("Missing document id")
                }
                if (revision.isBlank()) {
                    throw IOException("Missing document revision")
                }

                val payload = deleteJoinRequestAdapter.toJson(
                    DeleteDocumentRequest(id = documentId, revision = revision, deleted = true)
                )
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/teams")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                }
            }
        }
    }

    suspend fun cancelMembership(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        documentId: String,
        revision: String,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (documentId.isBlank()) {
                    throw IOException("Missing document id")
                }
                if (revision.isBlank()) {
                    throw IOException("Missing document revision")
                }

                val payload = deleteMembershipAdapter.toJson(
                    DeleteDocumentRequest(id = documentId, revision = revision, deleted = true)
                )
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/teams")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                }
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class MembershipFindRequest(val selector: MembershipSelector)

    @JsonClass(generateAdapter = true)
    data class MembershipSelector(
        val userId: String,
        val teamType: String,
        val docType: String,
        val status: StatusClause
    )

    @JsonClass(generateAdapter = true)
    data class TeamMembershipFindRequest(val selector: TeamMembershipSelector)

    @JsonClass(generateAdapter = true)
    data class TeamMembershipSelector(
        val teamId: String,
        val docType: String,
        val status: StatusClause,
    )

    @JsonClass(generateAdapter = true)
    data class StatusClause(
        @param:Json(name = "\$or") val or: List<StatusCondition>
    )

    @JsonClass(generateAdapter = true)
    data class StatusCondition(
        @param:Json(name = "\$exists") val exists: Boolean? = null,
        @param:Json(name = "\$ne") val notEquals: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class MembershipFindResponse(val docs: List<MembershipDocument>?)

    @JsonClass(generateAdapter = true)
    data class MemberCountFindRequest(
        val selector: MemberCountSelector,
        val fields: List<String>
    )

    @JsonClass(generateAdapter = true)
    data class MemberCountSelector(
        val teamId: String,
        val docType: String,
        val status: StatusClause
    )

    @JsonClass(generateAdapter = true)
    data class MemberIdDocument(@param:Json(name = "_id") val id: String?)

    @JsonClass(generateAdapter = true)
    data class MemberCountFindResponse(val docs: List<MemberIdDocument>?)

    @JsonClass(generateAdapter = true)
    data class MembershipDocument(
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "_rev") val revision: String?,
        val teamId: String?,
        val userId: String?,
        val teamPlanetCode: String?,
        val teamType: String?,
        val userPlanetCode: String?,
        val docType: String?,
        val isLeader: Boolean?,
        val status: String?
    )

    data class TeamMemberDetails(
        val username: String?,
        val fullName: String?,
        val isLeader: Boolean,
        val hasAvatar: Boolean,
        val membership: MembershipDocument?,
    )

    data class TeamMemberProfileDetails(
        val username: String,
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val email: String?,
        val phoneNumber: String?,
        val language: String?,
        val level: String?,
        val gender: String?,
        val birthDate: String?,
        val hasAvatar: Boolean,
    ) {
        val fullName: String?
            get() {
                val parts = listOfNotNull(firstName, middleName, lastName)
                    .filter { it.isNotBlank() }
                return if (parts.isEmpty()) null else parts.joinToString(" ")
            }
    }

    @JsonClass(generateAdapter = true)
    data class TeamsFindRequest(val selector: TeamsSelector)

    @JsonClass(generateAdapter = true)
    data class TeamsSelector(
        val status: String,
        val type: String,
        val teamType: String,
        @param:Json(name = "_id") val ids: IdsInClause
    )

    @JsonClass(generateAdapter = true)
    data class IdsInClause(
        @param:Json(name = "\$in") val ids: List<String>
    )

    @JsonClass(generateAdapter = true)
    data class NonMemberTeamsFindRequest(
        val selector: NonMemberTeamsSelector,
        val limit: Int? = null,
        val skip: Int? = null,
    )

    @JsonClass(generateAdapter = true)
    data class NonMemberTeamsSelector(
        @param:Json(name = "_id") val ids: IdsNotInClause?,
        val status: String,
        val type: String,
        val teamType: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class IdsNotInClause(
        @param:Json(name = "\$nin") val ids: List<String>
    )

    @JsonClass(generateAdapter = true)
    data class TeamsFindResponse(val docs: List<TeamDocument>?)

    @JsonClass(generateAdapter = true)
    data class TeamDocument(
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "_rev") val revision: String?,
        val limit: Int?,
        val status: String?,
        val type: String?,
        val teamType: String?,
        val name: String?,
        @param:Json(name = "teamName") val teamName: String?,
        @param:Json(name = "planetCode") val planetCode: String?,
        val teamPlanetCode: String?,
        val description: String?,
        val services: String?,
        val rules: String?,
        val requests: List<Any>?,
        val createdDate: Long?,
        val createdBy: String?,
        @param:Json(name = "parentCode") val parentCode: String?,
        @param:Json(name = "public") val isPublic: Boolean?,
        @param:Json(name = "memberCount") val memberCount: Int?,
        @param:Json(name = "membersCount") val membersCount: Int?,
        val members: List<Any>?
    )

    @JsonClass(generateAdapter = true)
    data class JoinRequestFindRequest(val selector: JoinRequestSelector)

    @JsonClass(generateAdapter = true)
    data class JoinRequestSelector(
        val docType: String,
        val teamType: String? = null,
        val teamId: String? = null,
        val userId: String,
    )

    @JsonClass(generateAdapter = true)
    data class JoinRequestDocument(
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "_rev") val revision: String?,
        val docType: String?,
        val teamId: String?,
        val teamType: String?,
        val teamPlanetCode: String?,
        val userId: String?,
        val userPlanetCode: String?,
    )

    @JsonClass(generateAdapter = true)
    data class JoinRequestFindResponse(val docs: List<JoinRequestDocument>?)

    @JsonClass(generateAdapter = true)
    data class JoinTeamRequest(
        val docType: String = "request",
        val teamId: String,
        val teamType: String = "local",
        val teamPlanetCode: String?,
        val userId: String,
        val userPlanetCode: String?,
    )

    @JsonClass(generateAdapter = true)
    data class DeleteDocumentRequest(
        @param:Json(name = "_id") val id: String,
        @param:Json(name = "_rev") val revision: String,
        @param:Json(name = "_deleted") val deleted: Boolean,
    )

    @JsonClass(generateAdapter = true)
    data class BulkMembershipDeleteRequest(val docs: List<BulkMembershipDeleteDoc>)

    @JsonClass(generateAdapter = true)
    data class BulkMembershipDeleteDoc(
        @param:Json(name = "_id") val id: String,
        @param:Json(name = "_rev") val revision: String,
        val teamId: String,
        val teamPlanetCode: String,
        val teamType: String,
        val userId: String,
        val userPlanetCode: String,
        val docType: String,
        val isLeader: Boolean,
        @param:Json(name = "_deleted") val deleted: Boolean,
    )

    @JsonClass(generateAdapter = true)
    data class BulkMembershipAddRequest(val docs: List<BulkMembershipAddDoc>)

    @JsonClass(generateAdapter = true)
    data class BulkMembershipAddDoc(
        val teamId: String,
        val teamPlanetCode: String,
        val teamType: String,
        val userId: String,
        val userPlanetCode: String,
        val docType: String,
        val isLeader: Boolean,
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    @JsonClass(generateAdapter = true)
    data class UserDocument(
        @param:Json(name = "_id") val _id: String?,
        @param:Json(name = "_attachments") val attachments: Attachments?,
        @param:Json(name = "planetCode") val planetCode: String?,
        @param:Json(name = "parentCode") val parentCode: String?,
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val email: String?,
        val language: String?,
        val phoneNumber: String?,
        @param:BirthDateString val birthDate: Long?,
        val gender: String?,
        val level: String?
    )

    @JsonClass(generateAdapter = true)
    data class Attachments(
        @param:Json(name = "img") val image: Attachment?
    )

    @JsonClass(generateAdapter = true)
    data class Attachment(
        @param:Json(name = "content_type") val contentType: String?,
        val revpos: Int?,
        val digest: String?,
        val length: Long?,
        val stub: Boolean?
    )

    suspend fun fetchUserProfiles(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        userIds: List<String>
    ): Result<List<UserDocument>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val basicAuth = credentials?.let { Credentials.basic(it.username, it.password) }
                    ?: throw IOException("Missing credentials for basic auth")
                if (userIds.isEmpty()) {
                    return@runCatching emptyList()
                }

                val selector = UserIdSelector(ids = IdsInClause(ids = userIds))
                val payload = moshi.adapter(UsersFindRequest::class.java).toJson(UsersFindRequest(selector = selector))
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/_users/_find")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .header("Authorization", basicAuth)
                    .header("Content-Type", "application/json")
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    moshi.adapter(UsersFindResponse::class.java).fromJson(body)?.docs ?: emptyList()
                }
            }
        }
    }

    suspend fun fetchAllUsers(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        planetCode: String?,
        parentCode: String?,
        pageSize: Int = 25,
        skip: Int = 0,
        searchTerm: String? = null,
        excludedUserIds: List<String> = emptyList(),
    ): Result<List<UserDocument>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val basicAuth = credentials?.let { Credentials.basic(it.username, it.password) }
                    ?: throw IOException("Missing credentials for basic auth")
                if (pageSize <= 0) {
                    return@runCatching emptyList()
                }

                val filteredPlanet = planetCode.nullIfBlank()
                    ?: throw IOException("Missing planet code for user search")
                val filteredParent = parentCode.nullIfBlank()
                    ?: throw IOException("Missing parent code for user search")

                val payload = buildUsersFindPayload(
                    planetCode = filteredPlanet,
                    parentCode = filteredParent,
                    pageSize = pageSize,
                    skip = skip,
                    searchTerm = searchTerm,
                    excludedUserIds = excludedUserIds,
                )
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/_users/_find")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .header("Authorization", basicAuth)
                    .header("Content-Type", "application/json")
                sessionCookie.nullIfBlank()?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    usersFindResponseAdapter.fromJson(body)?.docs ?: emptyList()
                }
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class UsersFindRequest(val selector: UserIdSelector)

    @JsonClass(generateAdapter = true)
    data class UserIdSelector(@param:Json(name = "_id") val ids: IdsInClause)

    @JsonClass(generateAdapter = true)
    data class UsersFindResponse(val docs: List<UserDocument>?)

}
