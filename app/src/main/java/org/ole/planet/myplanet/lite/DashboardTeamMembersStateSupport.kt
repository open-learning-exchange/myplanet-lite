/*
Author: Walfre López Prado
Email: loppra@plataformasinformaticas.com
Creation date: 2026-08-09
 */

package org.ole.planet.myplanet.lite

import android.widget.Toast
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamMemberProfileActivity
import org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal data class DashboardTeamMembersState(
    val members: List<TeamMemberDetails>,
    val teamPlanetCode: String?,
    val teamType: String,
    val isCurrentUserLeader: Boolean,
    val currentUsername: String,
)

internal fun buildTeamMembersState(
    members: List<TeamMemberDetails>,
    credentials: StoredCredentials,
): DashboardTeamMembersState {
    val sortedMembers = members.sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER) { member ->
            member.fullName?.takeIf(String::isNotBlank) ?: member.username.orEmpty()
        },
    )
    val normalizedUsername = credentials.username.substringAfter("org.couchdb.user:", credentials.username)
    val isLeader = sortedMembers.any { member ->
        member.isLeader && (
            member.username.equals(credentials.username, ignoreCase = true) ||
                member.username.equals(normalizedUsername, ignoreCase = true)
        )
    }
    return DashboardTeamMembersState(
        members = sortedMembers,
        teamPlanetCode = sortedMembers.firstNotNullOfOrNull { it.membership?.teamPlanetCode?.takeIf(String::isNotBlank) },
        teamType = sortedMembers.firstNotNullOfOrNull { it.membership?.teamType?.takeIf(String::isNotBlank) } ?: "local",
        isCurrentUserLeader = isLeader,
        currentUsername = normalizedUsername,
    )
}

internal fun Fragment.openTeamMemberProfileSupport(member: TeamMemberDetails) {
    val username = member.username
    if (username.isNullOrBlank()) {
        Toast.makeText(requireContext(), R.string.dashboard_team_members_profile_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    val displayName = member.fullName?.ifBlank { null } ?: username
    startActivity(DashboardTeamMemberProfileActivity.buildIntent(requireContext(), username, displayName, member.isLeader))
}
