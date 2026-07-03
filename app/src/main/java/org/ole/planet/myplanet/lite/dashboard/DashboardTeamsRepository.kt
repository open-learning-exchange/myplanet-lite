/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.DateStringAdapter

class DashboardTeamsRepository(
    client: OkHttpClient = OkHttpClient.Builder().build(),
    moshi: Moshi = Moshi.Builder()
        .add(DateStringAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val dispatcher = overrideDispatcher ?: dispatcher
    private val operations = DashboardTeamsOperations(client, moshi, teamMemberDetailsCache)

    suspend fun addTeamMember(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        request: AddTeamMemberRequest,
    ): Result<Unit> = runInDispatcher {
        operations.addTeamMember(baseUrl, credentials, sessionCookie, request)
    }

    suspend fun fetchMemberships(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        username: String,
    ): Result<List<MembershipDocument>> = runInDispatcher {
        operations.fetchMemberships(baseUrl, credentials, sessionCookie, username)
    }

    suspend fun fetchTeamMemberDetails(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
    ): Result<List<TeamMemberDetails>> = runInDispatcher {
        operations.fetchTeamMemberDetails(baseUrl, credentials, sessionCookie, teamId)
    }

    suspend fun fetchTeamMembers(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
    ): Result<List<MembershipDocument>> = runInDispatcher {
        operations.fetchTeamMembers(baseUrl, credentials, sessionCookie, teamId)
    }

    suspend fun removeTeamMember(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        membership: MembershipDocument,
    ): Result<Unit> = runInDispatcher {
        operations.removeTeamMember(baseUrl, credentials, sessionCookie, membership)
    }

    suspend fun fetchTeams(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamIds: List<String>,
    ): Result<List<TeamDocument>> = runInDispatcher {
        operations.fetchTeams(baseUrl, credentials, sessionCookie, teamIds)
    }

    suspend fun fetchAvailableTeams(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        excludedTeamIds: List<String>,
        skip: Int = 0,
        limit: Int = 25,
    ): Result<List<TeamDocument>> = runInDispatcher {
        operations.fetchAvailableTeams(baseUrl, credentials, sessionCookie, excludedTeamIds, skip, limit)
    }

    suspend fun fetchJoinRequests(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        userId: String,
    ): Result<List<JoinRequestDocument>> = runInDispatcher {
        operations.fetchJoinRequests(baseUrl, credentials, sessionCookie, userId)
    }

    suspend fun fetchTeamJoinRequests(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        teamPlanetCode: String,
    ): Result<List<JoinRequestDocument>> = runInDispatcher {
        operations.fetchTeamJoinRequests(baseUrl, credentials, sessionCookie, teamId, teamPlanetCode)
    }

    suspend fun hasExistingJoinRequest(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        userId: String,
    ): Result<Boolean> = runInDispatcher {
        operations.hasExistingJoinRequest(baseUrl, credentials, sessionCookie, teamId, userId)
    }

    suspend fun fetchMemberCounts(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamIds: List<String>,
    ): Result<Map<String, Int>> = runInDispatcher {
        operations.fetchMemberCounts(baseUrl, credentials, sessionCookie, teamIds)
    }

    suspend fun requestTeamMembership(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        request: JoinTeamRequest,
    ): Result<Unit> = runInDispatcher {
        operations.requestTeamMembership(baseUrl, credentials, sessionCookie, request)
    }

    suspend fun fetchTeamMemberProfileDetails(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        username: String,
    ): Result<TeamMemberProfileDetails> = runInDispatcher {
        operations.fetchTeamMemberProfileDetails(baseUrl, credentials, sessionCookie, username)
    }

    suspend fun cancelJoinRequest(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        documentId: String,
        revision: String,
    ): Result<Unit> = runInDispatcher {
        operations.cancelJoinRequest(baseUrl, credentials, sessionCookie, documentId, revision)
    }

    suspend fun cancelMembership(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        documentId: String,
        revision: String,
    ): Result<Unit> = runInDispatcher {
        operations.cancelMembership(baseUrl, credentials, sessionCookie, documentId, revision)
    }

    suspend fun fetchUserProfiles(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        userIds: List<String>,
    ): Result<List<UserDocument>> = runInDispatcher {
        operations.fetchUserProfiles(baseUrl, credentials, sessionCookie, userIds)
    }

    suspend fun fetchAllUsers(request: FetchUsersRequest): Result<List<UserDocument>> = runInDispatcher {
        operations.fetchAllUsers(request)
    }


    internal suspend fun fetchEnrichedTeamJoinRequests(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        teamPlanetCode: String,
    ): Result<List<EnrichedJoinRequest>> = runInDispatcher {
        val joinRequests = operations.fetchTeamJoinRequests(baseUrl, credentials, sessionCookie, teamId, teamPlanetCode)
        if (joinRequests.isEmpty()) return@runInDispatcher emptyList()

        val userIds = joinRequests.mapNotNull { it.userId?.takeIf(String::isNotBlank) }.distinct()
        val profilesById = runCatching { operations.fetchUserProfiles(baseUrl, credentials, sessionCookie, userIds) }.getOrDefault(emptyList()).associateBy { it._id }

        joinRequests.map { request ->
            val userId = request.userId.orEmpty()
            val profile = profilesById[userId]
            val username = userId.substringAfter("org.couchdb.user:", userId)
            val fullName = listOfNotNull(
                profile?.firstName,
                profile?.middleName,
                profile?.lastName
            ).filter { it.isNotBlank() }.joinToString(" ").ifBlank { username }
            EnrichedJoinRequest(
                id = request.id ?: userId,
                username = username,
                fullName = fullName,
                hasAvatar = profile?.attachments?.image != null,
                request = request,
            )
        }.sortedBy { it.fullName.lowercase() }
    }

    private suspend fun <T> runInDispatcher(block: () -> T): Result<T> {
        return withContext(dispatcher) {
            runCatching(block)
        }
    }

    companion object {
        private val teamMemberDetailsCache = java.util.concurrent.ConcurrentHashMap<String, List<TeamMemberDetails>>()

        @androidx.annotation.VisibleForTesting
        var overrideDispatcher: CoroutineDispatcher? = null

        @androidx.annotation.VisibleForTesting
        fun resetCacheForTesting() {
            teamMemberDetailsCache.clear()
            overrideDispatcher = null
        }
    }
}
