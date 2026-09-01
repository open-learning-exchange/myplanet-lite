package org.ole.planet.myplanet.lite

import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal suspend fun TeamsFragment.fetchAllEnterprisesData(
    base: String,
    username: String,
    credentials: StoredCredentials,
    sessionCookie: String?,
): Result<TeamsLoadResult> {
    val userId = "org.couchdb.user:$username"
    val userPlanetCode = DashboardServerPreferences.getServerCode(requireContext()).orEmpty()
    if (userPlanetCode.isBlank()) {
        return Result.failure(IllegalStateException("Missing user planet code"))
    }
    return enterprisesRepository.fetchSnapshot(
        baseUrl = base,
        credentials = credentials,
        sessionCookie = sessionCookie,
        userId = userId,
        userPlanetCode = userPlanetCode,
    ).map { snapshot ->
        val memberships = snapshot.membershipsByEnterpriseId
        val memberEnterprises = snapshot.enterprises.filter { it.id in memberships }
        val availableEnterprises = snapshot.enterprises.filterNot { it.id in memberships }
        val ids = snapshot.enterprises.mapNotNull { it.id }
        val counts = if (ids.isEmpty()) {
            emptyMap()
        } else {
            repository.fetchMemberCounts(base, credentials, sessionCookie, ids).getOrDefault(emptyMap())
        }
        TeamsLoadResult(
            membershipsByTeamId = memberships,
            joinRequestsByTeamId = snapshot.requestsByEnterpriseId,
            memberTeams = memberEnterprises,
            memberCounts = counts,
            availableTeamsData = AvailableTeamsData(availableEnterprises, counts),
        )
    }
}
