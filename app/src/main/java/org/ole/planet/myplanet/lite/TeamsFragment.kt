/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-23
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository.JoinRequestDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository.JoinTeamRequest
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository.MembershipDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository.TeamDocument
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase

class TeamsFragment : Fragment(R.layout.fragment_dashboard_teams) {

    private lateinit var myTeamsContainer: LinearLayout
    private lateinit var exploreTeamsContainer: LinearLayout
    private lateinit var scrollView: NestedScrollView
    private lateinit var loadingView: View
    private lateinit var emptyView: TextView
    private lateinit var myTeamsSection: View
    private lateinit var exploreSection: View
    private lateinit var refreshLayout: SwipeRefreshLayout

    private val repository = DashboardTeamsRepository()
    private var baseUrl: String? = null
    private var sessionCookie: String? = null
    private var currentUsername: String? = null
    private var isLoading = false
    private var isPaging = false
    private var selectedTeamId: String? = null
    private var pendingJoinRequests: Set<String> = emptySet()
    private var joinRequestsByTeamId: Map<String, JoinRequestDocument> = emptyMap()
    private var membershipsByTeamId: Map<String, MembershipDocument> = emptyMap()
    private var memberTeams: List<TeamDocument> = emptyList()
    private val availableTeams: MutableList<TeamDocument> = mutableListOf()
    private val memberCounts: MutableMap<String, Int> = mutableMapOf()
    private var hasMoreAvailableTeams = true
    private var pagingDialog: AlertDialog? = null
    private var availableSkip = 0

    private val pageSize = 25

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        myTeamsContainer = view.findViewById(R.id.teamsContainer)
        exploreTeamsContainer = view.findViewById(R.id.exploreTeamsContainer)
        scrollView = view.findViewById(R.id.dashboardTeamsScroll)
        loadingView = view.findViewById(R.id.teamsLoading)
        emptyView = view.findViewById(R.id.teamsEmptyView)
        myTeamsSection = view.findViewById(R.id.myTeamsSection)
        exploreSection = view.findViewById(R.id.exploreTeamsSection)
        refreshLayout = view.findViewById(R.id.dashboardTeamsRefresh)
        refreshLayout.setOnRefreshListener { loadTeams() }
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY && !scrollView.canScrollVertically(1)) {
                loadMoreAvailableTeams()
            }
        }

        selectedTeamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            initializeSession()
            loadTeams()
        }
    }

    override fun onResume() {
        super.onResume()
        selectedTeamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
        updateBookmarkSelection()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isLoading = false
        isPaging = false
        hidePagingDialog()
    }

    private suspend fun initializeSession() {
        val context = requireContext().applicationContext
        baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        val credentials = ProfileCredentialsStore.getStoredCredentials(context)
        currentUsername = withContext(Dispatchers.IO) {
            UserProfileDatabase.getInstance(context).getProfile()?.username
        } ?: credentials?.username
        baseUrl?.let { base ->
            val authService = AuthDependencies.provideAuthService(context, base)
            sessionCookie = authService.getStoredToken()
        }
    }

    private fun loadTeams() {
        val base = baseUrl
        val username = currentUsername
        val context = context ?: return
        val credentials = ProfileCredentialsStore.getStoredCredentials(context)
        if (base.isNullOrBlank()) {
            showEmptyState(R.string.dashboard_teams_no_server)
            return
        }
        if (username.isNullOrBlank()) {
            showEmptyState(R.string.dashboard_teams_no_user)
            return
        }
        pendingJoinRequests = emptySet()
        if (credentials == null) {
            showEmptyState(R.string.dashboard_teams_no_credentials)
            return
        }
        if (isLoading) {
            stopRefreshing()
            return
        }
        isLoading = true
        isPaging = false
        hasMoreAvailableTeams = true
        availableSkip = 0
        availableTeams.clear()
        memberTeams = emptyList()
        memberCounts.clear()
        updateLoadingVisibility()
        viewLifecycleOwner.lifecycleScope.launch {
            val membershipResult = repository.fetchMemberships(base, credentials, sessionCookie, username)
            val memberships = membershipResult.getOrElse {
                handleLoadError()
                return@launch
            }
            membershipsByTeamId = memberships.mapNotNull { membership ->
                val teamId = membership.teamId?.takeIf { it.isNotBlank() }
                teamId?.let { id -> id to membership }
            }.toMap()
            val teamIds = membershipsByTeamId.keys.toList()

            val pendingMembershipIds = membershipsByTeamId.keys

            val userId = "org.couchdb.user:$username"
            val joinRequestsResult = repository.fetchJoinRequests(
                base,
                credentials,
                sessionCookie,
                userId
            )
            val remoteJoinRequests = joinRequestsResult.getOrElse { emptyList() }
            joinRequestsByTeamId = remoteJoinRequests.mapNotNull { doc ->
                val teamId = doc.teamId
                if (teamId.isNullOrBlank()) return@mapNotNull null
                teamId to doc
            }.toMap()
            val pendingTeamIds = remoteJoinRequests.mapNotNull { it.teamId }
            pendingJoinRequests = pendingTeamIds.toSet() - pendingMembershipIds

            val teamsResult = repository.fetchTeams(base, credentials, sessionCookie, teamIds)
            memberTeams = teamsResult.getOrElse {
                handleLoadError()
                return@launch
            }

            val memberTeamIds = memberTeams.mapNotNull { it.id }.filter { it.isNotBlank() }
            val memberCountsResult = repository.fetchMemberCounts(base, credentials, sessionCookie, memberTeamIds)
            memberCountsResult.getOrNull()?.let { counts ->
                memberCounts.putAll(counts)
            }

            val availableLoaded = fetchAvailableTeamsPage(reset = true)
            if (!availableLoaded) {
                handleLoadError()
                isLoading = false
                updateLoadingVisibility()
                return@launch
            }

            isLoading = false
            updateLoadingVisibility()
        }
    }

    private fun loadMoreAvailableTeams() {
        if (isLoading || isPaging || !hasMoreAvailableTeams) {
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

    private suspend fun fetchAvailableTeamsPage(reset: Boolean): Boolean {
        val base = baseUrl ?: return false
        val context = context ?: return false
        val credentials = ProfileCredentialsStore.getStoredCredentials(context) ?: return false

        if (reset) {
            availableTeams.clear()
            availableSkip = 0
            hasMoreAvailableTeams = true
        }

        val availableTeamsResult = repository.fetchAvailableTeams(
            base,
            credentials,
            sessionCookie,
            membershipsByTeamId.keys.toList(),
            skip = availableSkip,
            limit = pageSize,
        )
        val newTeams = availableTeamsResult.getOrElse { return false }

        if (newTeams.isEmpty()) {
            hasMoreAvailableTeams = false
        }

        availableSkip += newTeams.size
        if (newTeams.size < pageSize) {
            hasMoreAvailableTeams = false
        }

        availableTeams.addAll(newTeams)

        val newTeamIds = newTeams.mapNotNull { it.id }.filter { it.isNotBlank() }
        val newCountsResult = repository.fetchMemberCounts(base, credentials, sessionCookie, newTeamIds)
        newCountsResult.getOrNull()?.let { counts ->
            memberCounts.putAll(counts)
        }

        renderTeams(memberTeams, availableTeams, memberCounts)
        return true
    }

    private fun showPagingDialog() {
        if (pagingDialog?.isShowing == true) {
            return
        }
        val progressBar = ProgressBar(requireContext()).apply {
            isIndeterminate = true
            setBackgroundColor(Color.TRANSPARENT)
        }
        pagingDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressBar)
            .setCancelable(false)
            .create()
        pagingDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pagingDialog?.show()
    }

    private fun hidePagingDialog() {
        pagingDialog?.dismiss()
        pagingDialog = null
    }

    private fun renderTeams(
        memberTeams: List<TeamDocument>,
        availableTeams: List<TeamDocument>,
        memberCounts: Map<String, Int>
    ) {
        myTeamsContainer.removeAllViews()
        exploreTeamsContainer.removeAllViews()

        if (memberTeams.isEmpty() && availableTeams.isEmpty()) {
            showEmptyState(R.string.dashboard_teams_join_prompt)
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

    private fun buildTeamCard(
        team: TeamDocument,
        memberCounts: Map<String, Int>,
        membership: MembershipDocument?
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
        card.tag = teamId
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
                if (teamId == selectedTeamId) R.drawable.ic_bookmark_selected_24 else R.drawable.ic_bookmark_24
            )
            bookmarkButton.contentDescription = getString(R.string.dashboard_teams_bookmark)
            bookmarkButton.setOnClickListener {
                val idToSelect = teamId ?: return@setOnClickListener
                val newSelection = if (idToSelect == selectedTeamId) null else idToSelect
                val newSelectionName = if (newSelection == null) null else displayName
                selectedTeamId = newSelection
                DashboardTeamSelectionPreferences.setSelectedTeam(
                    requireContext(),
                    newSelection,
                    newSelectionName
                )
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

    private fun configureJoinAction(
        actionButton: ImageButton,
        team: TeamDocument,
        teamId: String?
    ) {
        actionButton.setImageResource(R.drawable.ic_group_join_24)
        actionButton.contentDescription = getString(R.string.dashboard_teams_join_team)
        actionButton.setOnClickListener {
            val idToJoin = teamId ?: return@setOnClickListener
            val base = baseUrl ?: return@setOnClickListener
            val username = currentUsername ?: return@setOnClickListener
            val userId = "org.couchdb.user:$username"
            val credentials = ProfileCredentialsStore.getStoredCredentials(requireContext())
                ?: return@setOnClickListener
            val serverCode = DashboardServerPreferences.getServerCode(requireContext())
            val request = JoinTeamRequest(
                teamId = idToJoin,
                teamPlanetCode = team.teamPlanetCode ?: team.planetCode ?: serverCode,
                userId = userId,
                userPlanetCode = serverCode,
            )

            actionButton.isEnabled = false
            actionButton.setImageResource(R.drawable.ic_wait_response_24)

            viewLifecycleOwner.lifecycleScope.launch {
                val hasExistingRequest = repository.hasExistingJoinRequest(
                    base,
                    credentials,
                    sessionCookie,
                    idToJoin,
                    userId
                )
                val existing = hasExistingRequest.getOrElse {
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

                val result = repository.requestTeamMembership(
                    base,
                    credentials,
                    sessionCookie,
                    request
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

    private fun leaveTeam(
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
            val result = repository.cancelMembership(
                base,
                credentials,
                sessionCookie,
                membershipId,
                revision
            )

            if (result.isSuccess) {
                val teamId = team.id
                if (teamId != null) {
                    membershipsByTeamId = membershipsByTeamId - teamId
                }
                loadTeams()
            } else {
                actionButton.isEnabled = true
                actionButton.setImageResource(R.drawable.ic_group_leave_24)
            }
        }
    }

    private fun showCancelJoinRequestDialog(
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
            }
            .show()
    }

    private fun cancelJoinRequest(
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
            val result = repository.cancelJoinRequest(
                base,
                credentials,
                sessionCookie,
                requestId,
                revision
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

    private fun updateBookmarkSelection() {
        val selectedId = selectedTeamId
        if (!::myTeamsContainer.isInitialized) return
        for (i in 0 until myTeamsContainer.childCount) {
            val card = myTeamsContainer.getChildAt(i)
            val bookmark = card.findViewById<ImageButton?>(R.id.teamBookmark)
            val teamId = card.tag as? String
            if (bookmark != null && teamId != null) {
                bookmark.setImageResource(
                    if (teamId == selectedId) R.drawable.ic_bookmark_selected_24 else R.drawable.ic_bookmark_24
                )
            }
        }
    }

    private fun updateLoadingVisibility() {
        loadingView.isVisible = isLoading
        val hasContent = myTeamsContainer.childCount > 0 || exploreTeamsContainer.childCount > 0
        myTeamsContainer.isVisible = !isLoading && myTeamsContainer.childCount > 0
        exploreTeamsContainer.isVisible = !isLoading && exploreTeamsContainer.childCount > 0
        myTeamsSection.isVisible = !isLoading && myTeamsContainer.childCount > 0
        exploreSection.isVisible = !isLoading && exploreTeamsContainer.childCount > 0
        emptyView.isVisible = !isLoading && !hasContent && emptyView.text.isNotBlank()
        if (!isLoading) {
            stopRefreshing()
        }
    }

    private fun handleLoadError() {
        isLoading = false
        showEmptyState(R.string.dashboard_teams_error_loading)
    }

    private fun showEmptyState(messageRes: Int) {
        myTeamsContainer.removeAllViews()
        exploreTeamsContainer.removeAllViews()
        emptyView.setText(messageRes)
        myTeamsSection.isVisible = false
        exploreSection.isVisible = false
        updateLoadingVisibility()
    }

    private fun stopRefreshing() {
        if (::refreshLayout.isInitialized && refreshLayout.isRefreshing) {
            refreshLayout.isRefreshing = false
        }
    }

    private fun resolveMembersLabel(team: TeamDocument, memberCounts: Map<String, Int>): String {
        val count = memberCounts[team.id] ?: team.memberCount ?: team.membersCount ?: team.members?.size
        return if (count != null) {
            val safeCount = count.coerceAtLeast(0)
            resources.getQuantityString(R.plurals.dashboard_teams_member_count, safeCount, safeCount)
        } else {
            getString(R.string.dashboard_teams_member_unknown)
        }
    }

    private fun resolveTeamName(team: TeamDocument): String {
        val candidate = listOf(
            team.name,
            team.teamName,
            team.teamPlanetCode,
            team.planetCode,
            team.id
        ).firstOrNull { !it.isNullOrBlank() }
        return candidate ?: getString(R.string.dashboard_teams_unknown_name)
    }

    private fun buildInitials(name: String): String {
        val parts = name.trim().split(" ", limit = 2).filter { it.isNotBlank() }
        if (parts.isEmpty()) return getString(R.string.dashboard_teams_default_initials)
        if (parts.size == 1) {
            return parts.first().take(2).uppercase()
        }
        return "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
    }
}
