/*
Author: Walfre López Prado
Email: loppra@plataformasinformaticas.com
Creation date: 2026-08-09
 */

package org.ole.planet.myplanet.lite

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.dashboard.JoinRequestDocument
import org.ole.planet.myplanet.lite.dashboard.JoinTeamRequest
import org.ole.planet.myplanet.lite.dashboard.MembershipDocument
import org.ole.planet.myplanet.lite.dashboard.TeamDocument
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase

internal typealias AvailableTeamsData = TeamsFragment.AvailableTeamsData
internal typealias TeamsLoadResult = TeamsFragment.TeamsLoadResult
internal typealias ValidationContext = TeamsFragment.ValidationContext
internal typealias TeamViewHolder = TeamsFragment.TeamViewHolder

internal suspend fun TeamsFragment.fetchAllTeamsData(
        base: String,
        username: String,
        credentials: StoredCredentials,
        sessionCookie: String?,
    ): Result<TeamsLoadResult> =
        if (isEnterpriseMode) {
            fetchAllEnterprisesData(base, username, credentials, sessionCookie)
        } else runCatching {
            val membershipsResult = repository.fetchMemberships(base, credentials, sessionCookie, username)
            val memberships = membershipsResult.getOrThrow()

            val membershipsByTeamId =
                memberships
                    .mapNotNull { membership ->
                        membership.teamId?.takeIf { it.isNotBlank() }?.let { id -> id to membership }
                    }.toMap()

            val teamIds = membershipsByTeamId.keys.toList()
            val userId = "org.couchdb.user:$username"

            coroutineScope {
                val joinRequestsDeferred =
                    async {
                        repository.fetchJoinRequests(base, credentials, sessionCookie, userId)
                    }
                val teamsDeferred =
                    async {
                        repository.fetchTeams(base, credentials, sessionCookie, teamIds)
                    }
                val memberCountsDeferred =
                    async {
                        if (teamIds.isNotEmpty()) {
                            repository.fetchMemberCounts(base, credentials, sessionCookie, teamIds)
                        } else {
                            Result.success(emptyMap<String, Int>())
                        }
                    }
                val availableTeamsDeferred =
                    async {
                        fetchAvailableTeamsData(base, credentials, sessionCookie, teamIds, 0, pageSize)
                    }

                val remoteJoinRequests = joinRequestsDeferred.await().getOrElse { emptyList() }
                val joinRequestsByTeamId =
                    remoteJoinRequests
                        .mapNotNull { doc ->
                            doc.teamId?.takeIf { it.isNotBlank() }?.let { it to doc }
                        }.toMap()

                val memberTeams = teamsDeferred.await().getOrThrow()

                val memberCounts = memberCountsDeferred.await().getOrNull()?.toMutableMap() ?: mutableMapOf()
                for (team in memberTeams) {
                    val id = team.id
                    if (id != null && !memberCounts.containsKey(id)) {
                        memberCounts[id] = 0
                    }
                }

                val availableTeamsData = availableTeamsDeferred.await().getOrThrow()

                TeamsLoadResult(
                    membershipsByTeamId = membershipsByTeamId,
                    joinRequestsByTeamId = joinRequestsByTeamId,
                    memberTeams = memberTeams,
                    memberCounts = memberCounts,
                    availableTeamsData = availableTeamsData,
                )
            }
        }

internal suspend fun TeamsFragment.fetchAvailableTeamsData(
        base: String,
        credentials: StoredCredentials,
        sessionCookie: String?,
        excludedTeamIds: List<String>,
        skip: Int,
        limit: Int,
    ): Result<AvailableTeamsData> =
        runCatching {
            val teamsResult =
                repository.fetchAvailableTeams(
                    base,
                    credentials,
                    sessionCookie,
                    excludedTeamIds,
                    skip = skip,
                    limit = limit,
                )
            val teams = teamsResult.getOrThrow()
            val teamIds = teams.mapNotNull { it.id }.filter { it.isNotBlank() }
            val counts =
                if (teamIds.isNotEmpty()) {
                    repository.fetchMemberCounts(base, credentials, sessionCookie, teamIds).getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }
            AvailableTeamsData(teams, counts)
        }

internal fun TeamsFragment.loadMoreAvailableTeams() {
        if (isEnterpriseMode) return
        if (searchQuery.isNotEmpty() || isLoading || isPaging || !hasMoreAvailableTeams) {
            return
        }
        isPaging = true
        showPagingDialog()
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = fetchAvailableTeamsPage(reset = false)
            hidePagingDialog()
            isPaging = false
            if (!loaded) {
                hasMoreAvailableTeams = false
            }
        }
    }

internal suspend fun TeamsFragment.fetchAvailableTeamsPage(reset: Boolean): Boolean {
        val base = baseUrl ?: return false
        val context = context ?: return false
        val credentials = ProfileCredentialsStore.getStoredCredentials(context) ?: return false

        if (reset) {
            availableTeams.clear()
            availableSkip = 0
            hasMoreAvailableTeams = true
        }

        val result =
            fetchAvailableTeamsData(
                base,
                credentials,
                sessionCookie,
                membershipsByTeamId.keys.toList(),
                skip = availableSkip,
                limit = pageSize,
            )

        val data = result.getOrElse { return false }
        val newTeams = data.teams

        availableTeams.addAll(newTeams)
        memberCounts.putAll(data.counts)
        newTeams.mapNotNull { it.id }.forEach { id ->
            memberCounts.putIfAbsent(id, 0)
        }

        availableSkip += newTeams.size
        hasMoreAvailableTeams = newTeams.size >= pageSize

        renderTeams(memberTeams, availableTeams, memberCounts)
        return true
    }

internal fun TeamsFragment.showPagingDialog() {
        if (pagingDialog?.isShowing == true) {
            return
        }
        val progressBar =
            ProgressBar(requireContext()).apply {
                isIndeterminate = true
                setBackgroundColor(Color.TRANSPARENT)
            }
        pagingDialog =
            MaterialAlertDialogBuilder(requireContext())
                .setView(progressBar)
                .setCancelable(false)
                .create()
        pagingDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pagingDialog?.show()
    }

internal fun TeamsFragment.hidePagingDialog() {
        pagingDialog?.dismiss()
        pagingDialog = null
    }

internal fun TeamsFragment.renderTeams(
        memberTeams: List<TeamDocument>,
        availableTeams: List<TeamDocument>,
        memberCounts: Map<String, Int>,
    ) {
        myTeamsContainer.removeAllViews()
        exploreTeamsContainer.removeAllViews()

        if (memberTeams.isEmpty() && availableTeams.isEmpty()) {
            showEmptyState(joinPromptMessageRes())
            return
        }

        if (memberTeams.isNotEmpty()) {
            myTeamsSection.isVisible = true
            memberTeams.forEach { team ->
                val card = buildTeamCard(team, memberCounts, membershipsByTeamId[team.id])
                myTeamsContainer.addView(card)
            }
        } else {
            myTeamsSection.isVisible = false
        }

        if (availableTeams.isNotEmpty()) {
            exploreSection.isVisible = true
            availableTeams.forEach { team ->
                val card = buildTeamCard(team, memberCounts, null)
                exploreTeamsContainer.addView(card)
            }
        } else {
            exploreSection.isVisible = false
        }

        emptyView.text = null
    }

internal fun TeamsFragment.buildTeamCard(
        team: TeamDocument,
        memberCounts: Map<String, Int>,
        membership: MembershipDocument?,
    ): View {
        val inflater = LayoutInflater.from(myTeamsContainer.context)
        val card = inflater.inflate(R.layout.item_dashboard_team, myTeamsContainer, false) as MaterialCardView
        val initialsView: TextView = card.findViewById(R.id.teamInitials)
        val nameView: TextView = card.findViewById(R.id.teamName)
        val membersView: TextView = card.findViewById(R.id.teamMembers)
        val leaderBadge: ImageView = card.findViewById(R.id.teamLeaderBadge)
        val actionButton: ImageButton = card.findViewById(R.id.teamAction)
        val bookmarkButton: ImageButton = card.findViewById(R.id.teamBookmark)

        val teamId = team.id
        val displayName = resolveTeamName(team)
        card.tag = TeamViewHolder(teamId, bookmarkButton)
        initialsView.text = buildInitials(displayName)
        nameView.text = displayName
        membersView.text = resolveMembersLabel(team, memberCounts)

        val isMember = membership != null
        val isLeader = membership?.isLeader == true
        val isPendingJoin = !isMember && teamId != null && pendingJoinRequests.contains(teamId)

        leaderBadge.isVisible = isMember && isLeader
        actionButton.isVisible = !isMember || !isLeader

        if (isMember) {
            actionButton.setImageResource(R.drawable.ic_group_leave_24)
            actionButton.contentDescription = getString(R.string.dashboard_teams_leave_team)
            actionButton.setOnClickListener {
                leaveTeam(actionButton, team, membership)
            }
            bookmarkButton.isVisible = true
            bookmarkButton.setImageResource(
                if (teamId == selectedTeamId) R.drawable.ic_bookmark_selected_24 else R.drawable.ic_bookmark_24,
            )
            bookmarkButton.contentDescription = getString(R.string.dashboard_teams_bookmark)
            bookmarkButton.setOnClickListener {
                val idToSelect = teamId ?: return@setOnClickListener
                val newSelection = if (idToSelect == selectedTeamId) null else idToSelect
                val newSelectionName = if (newSelection == null) null else displayName
                selectedTeamId = newSelection
                setSelectedItem(newSelection, newSelectionName)
                updateBookmarkSelection()
            }
        } else {
            bookmarkButton.isVisible = false
            if (isPendingJoin) {
                actionButton.isEnabled = true
                actionButton.setImageResource(R.drawable.ic_wait_response_24)
                actionButton.contentDescription = getString(R.string.dashboard_teams_join_pending)
                actionButton.setOnClickListener {
                    val idToCancel = teamId
                    val requestDoc = joinRequestsByTeamId[idToCancel] ?: return@setOnClickListener
                    showCancelJoinRequestDialog(actionButton, team, requestDoc)
                }
            } else {
                configureJoinAction(actionButton, team, teamId)
            }
        }

        return card
    }

internal fun TeamsFragment.configureJoinAction(
        actionButton: ImageButton,
        team: TeamDocument,
        teamId: String?,
    ) {
        actionButton.setImageResource(R.drawable.ic_group_join_24)
        actionButton.contentDescription = getString(R.string.dashboard_teams_join_team)
        actionButton.setOnClickListener {
            val idToJoin = teamId ?: return@setOnClickListener
            val base = baseUrl ?: return@setOnClickListener
            val username = currentUsername ?: return@setOnClickListener
            val userId = "org.couchdb.user:$username"
            val credentials =
                ProfileCredentialsStore.getStoredCredentials(requireContext())
                    ?: return@setOnClickListener
            val serverCode = DashboardServerPreferences.getServerCode(requireContext())
            val request =
                JoinTeamRequest(
                    teamId = idToJoin,
                    teamType = if (isEnterpriseMode) "sync" else "local",
                    teamPlanetCode = team.teamPlanetCode ?: team.planetCode ?: serverCode,
                    userId = userId,
                    userPlanetCode = serverCode,
                )

            actionButton.isEnabled = false
            actionButton.setImageResource(R.drawable.ic_wait_response_24)

            viewLifecycleOwner.lifecycleScope.launch {
                val hasExistingRequest = if (isEnterpriseMode) {
                    enterprisesRepository.hasExistingRelationship(
                        base,
                        credentials,
                        sessionCookie,
                        idToJoin,
                        userId,
                        serverCode.orEmpty(),
                    )
                } else {
                    repository.hasExistingJoinRequest(
                        base,
                        credentials,
                        sessionCookie,
                        idToJoin,
                        userId,
                    )
                }
                val existing =
                    hasExistingRequest.getOrElse {
                        actionButton.isEnabled = true
                        actionButton.setImageResource(R.drawable.ic_group_join_24)
                        return@launch
                    }

                if (existing) {
                    pendingJoinRequests = pendingJoinRequests + idToJoin
                    actionButton.contentDescription =
                        getString(R.string.dashboard_teams_join_pending)
                    return@launch
                }

                val result =
                    repository.requestTeamMembership(
                        base,
                        credentials,
                        sessionCookie,
                        request,
                    )
                if (result.isSuccess) {
                    pendingJoinRequests = pendingJoinRequests + idToJoin
                    actionButton.contentDescription =
                        getString(R.string.dashboard_teams_join_pending)
                } else {
                    actionButton.isEnabled = true
                    actionButton.setImageResource(R.drawable.ic_group_join_24)
                }
            }
        }
    }

internal fun TeamsFragment.leaveTeam(
        actionButton: ImageButton,
        team: TeamDocument,
        membership: MembershipDocument,
    ) {
        val membershipId = membership.id ?: return
        val revision = membership.revision ?: return
        val credentials = ProfileCredentialsStore.getStoredCredentials(requireContext()) ?: return
        val base = baseUrl ?: return
        actionButton.isEnabled = false
        actionButton.setImageResource(R.drawable.ic_wait_response_24)

        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                repository.cancelMembership(
                    base,
                    credentials,
                    sessionCookie,
                    membershipId,
                    revision,
                )

            if (result.isSuccess) {
                val teamId = team.id
                if (teamId != null) {
                    membershipsByTeamId = membershipsByTeamId - teamId
                }
                reloadTeams()
            } else {
                actionButton.isEnabled = true
                actionButton.setImageResource(R.drawable.ic_group_leave_24)
            }
        }
    }

internal fun TeamsFragment.showCancelJoinRequestDialog(
        actionButton: ImageButton,
        team: TeamDocument,
        requestDoc: JoinRequestDocument,
    ) {
        val context = requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dashboard_teams_cancel_join_title)
            .setMessage(R.string.dashboard_teams_cancel_join_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dashboard_teams_cancel_join_confirm) { _, _ ->
                cancelJoinRequest(actionButton, team, requestDoc)
            }.show()
    }

internal fun TeamsFragment.cancelJoinRequest(
        actionButton: ImageButton,
        team: TeamDocument,
        requestDoc: JoinRequestDocument,
    ) {
        val idToCancel = requestDoc.teamId ?: return
        val credentials = ProfileCredentialsStore.getStoredCredentials(requireContext()) ?: return
        val base = baseUrl ?: return
        val revision = requestDoc.revision ?: return
        val requestId = requestDoc.id ?: return
        actionButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                repository.cancelJoinRequest(
                    base,
                    credentials,
                    sessionCookie,
                    requestId,
                    revision,
                )

            if (result.isSuccess) {
                pendingJoinRequests = pendingJoinRequests - idToCancel
                joinRequestsByTeamId = joinRequestsByTeamId - idToCancel
                actionButton.isEnabled = true
                configureJoinAction(actionButton, team, idToCancel)
            } else {
                actionButton.isEnabled = true
            }
        }
    }
