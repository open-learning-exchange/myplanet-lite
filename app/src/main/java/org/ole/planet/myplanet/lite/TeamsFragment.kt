/*
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
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterprisesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseSelectionPreferences
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

class TeamsFragment : Fragment(R.layout.fragment_dashboard_teams) {
    internal val isEnterpriseMode: Boolean
        get() = arguments?.getBoolean(ARG_ENTERPRISE_MODE) == true
    internal val enterprisesRepository = DashboardEnterprisesRepository()
    internal lateinit var myTeamsContainer: LinearLayout
    internal lateinit var exploreTeamsContainer: LinearLayout
    internal lateinit var scrollView: NestedScrollView
    internal lateinit var loadingView: View
    internal lateinit var emptyView: TextView
    internal lateinit var myTeamsSection: View
    internal lateinit var exploreSection: View
    internal lateinit var refreshLayout: SwipeRefreshLayout
    internal lateinit var searchInput: EditText

    internal val repository: DashboardTeamsRepository
        get() = DashboardTeamsDependencies.provideRepository()
    internal var baseUrl: String? = null
    internal var sessionCookie: String? = null
    internal var currentUsername: String? = null
    internal var isLoading = false
    internal var isPaging = false
    internal var selectedTeamId: String? = null
    internal var pendingJoinRequests: Set<String> = emptySet()
    internal var joinRequestsByTeamId: Map<String, JoinRequestDocument> = emptyMap()
    internal var membershipsByTeamId: Map<String, MembershipDocument> = emptyMap()
    internal var memberTeams: List<TeamDocument> = emptyList()
    internal val availableTeams: MutableList<TeamDocument> = mutableListOf()
    internal val memberCounts: MutableMap<String, Int> = mutableMapOf()
    internal var hasMoreAvailableTeams = true
    internal var pagingDialog: AlertDialog? = null
    internal var availableSkip = 0
    internal var searchJob: Job? = null
    internal var searchQuery: String = ""

    internal val pageSize = 25

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        myTeamsContainer = view.findViewById(R.id.teamsContainer)
        exploreTeamsContainer = view.findViewById(R.id.exploreTeamsContainer)
        scrollView = view.findViewById(R.id.dashboardTeamsScroll)
        loadingView = view.findViewById(R.id.teamsLoading)
        emptyView = view.findViewById(R.id.teamsEmptyView)
        myTeamsSection = view.findViewById(R.id.myTeamsSection)
        exploreSection = view.findViewById(R.id.exploreTeamsSection)
        refreshLayout = view.findViewById(R.id.dashboardTeamsRefresh)
        searchInput = view.findViewById(R.id.teamsSearchInput)
        if (isEnterpriseMode) configureEnterpriseLabels(view)
        searchInput.doAfterTextChanged { text -> scheduleTeamSearch(text?.toString().orEmpty()) }
        refreshLayout.setOnRefreshListener { loadTeams() }
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY && !scrollView.canScrollVertically(1)) {
                loadMoreAvailableTeams()
            }
        }

        selectedTeamId = getSelectedItemId()

        viewLifecycleOwner.lifecycleScope.launch {
            initializeSession()
            loadTeams()
        }
    }

    override fun onResume() {
        super.onResume()
        selectedTeamId = getSelectedItemId()
        updateBookmarkSelection()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isLoading = false
        isPaging = false
        hidePagingDialog()
        searchJob?.cancel()
    }

    internal suspend fun initializeSession() {
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

    internal data class AvailableTeamsData(
        val teams: List<TeamDocument>,
        val counts: Map<String, Int>,
    )

    internal data class TeamsLoadResult(
        val membershipsByTeamId: Map<String, MembershipDocument>,
        val joinRequestsByTeamId: Map<String, JoinRequestDocument>,
        val memberTeams: List<TeamDocument>,
        val memberCounts: Map<String, Int>,
        val availableTeamsData: AvailableTeamsData,
    )

    internal data class ValidationContext(
        val base: String,
        val username: String,
        val credentials: StoredCredentials,
    )

    internal fun validatePreconditions(): ValidationContext? {
        val base = baseUrl
        val username = currentUsername
        val context = context ?: return null
        val credentials = ProfileCredentialsStore.getStoredCredentials(context)

        if (base.isNullOrBlank()) {
            showEmptyState(R.string.dashboard_teams_no_server)
            return null
        }
        if (username.isNullOrBlank()) {
            showEmptyState(R.string.dashboard_teams_no_user)
            return null
        }
        pendingJoinRequests = emptySet()
        if (credentials == null) {
            showEmptyState(R.string.dashboard_teams_no_credentials)
            return null
        }
        if (isLoading) {
            stopRefreshing()
            return null
        }
        return ValidationContext(base, username, credentials)
    }

    internal fun prepareForLoading() {
        isLoading = true
        isPaging = false
        hasMoreAvailableTeams = true
        availableSkip = 0
        availableTeams.clear()
        memberTeams = emptyList()
        memberCounts.clear()
        updateLoadingVisibility()
    }

    internal fun processTeamsData(data: TeamsLoadResult) {
        membershipsByTeamId = data.membershipsByTeamId
        joinRequestsByTeamId = data.joinRequestsByTeamId
        pendingJoinRequests = joinRequestsByTeamId.keys - membershipsByTeamId.keys

        memberTeams = data.memberTeams
        memberCounts.putAll(data.memberCounts)

        val availableData = data.availableTeamsData
        availableTeams.addAll(availableData.teams)
        memberCounts.putAll(availableData.counts)
        availableData.teams.mapNotNull { it.id }.forEach { id ->
            memberCounts.putIfAbsent(id, 0)
        }

        availableSkip = availableData.teams.size
        hasMoreAvailableTeams = availableData.teams.size >= pageSize

        renderTeams(memberTeams, availableTeams, memberCounts)
    }

    private fun loadTeams() {
        val validationContext = validatePreconditions() ?: return

        prepareForLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result =
                    fetchAllTeamsData(
                        validationContext.base,
                        validationContext.username,
                        validationContext.credentials,
                        sessionCookie,
                    )
                val data =
                    result.getOrElse {
                        handleLoadError()
                        return@launch
                    }
                processTeamsData(data)
            } finally {
                isLoading = false
                updateLoadingVisibility()
            }
        }
    }

    internal fun reloadTeams() = loadTeams()

    internal fun scheduleTeamSearch(rawQuery: String) {
        val query = rawQuery.trim()
        val previousQuery = searchQuery
        searchQuery = query
        searchJob?.cancel()
        if (query.isEmpty()) {
            if (previousQuery.isNotEmpty()) loadTeams()
            return
        }
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(350)
            if (isEnterpriseMode) {
                val allEnterprises = memberTeams + availableTeams
                val matches = allEnterprises.filter { resolveTeamName(it).contains(query, ignoreCase = true) }
                val members = matches.filter { membershipsByTeamId.containsKey(it.id) }
                val available = matches.filterNot { membershipsByTeamId.containsKey(it.id) }
                renderSearchResults(members, available)
                return@launch
            }
            val base = baseUrl ?: return@launch
            val credentials = ProfileCredentialsStore.getStoredCredentials(requireContext()) ?: return@launch
            val result = repository.searchTeams(base, credentials, sessionCookie, query)
            if (searchQuery != query) return@launch
            result.onSuccess { teams ->
                val members = teams.filter { membershipsByTeamId.containsKey(it.id) }
                val available = teams.filterNot { membershipsByTeamId.containsKey(it.id) }
                renderSearchResults(members, available)
            }.onFailure {
                showEmptyState(loadErrorMessageRes())
            }
        }
    }

    internal fun renderSearchResults(
        members: List<TeamDocument>,
        available: List<TeamDocument>,
    ) {
        if (members.isEmpty() && available.isEmpty()) {
            myTeamsContainer.removeAllViews()
            exploreTeamsContainer.removeAllViews()
            emptyView.setText(if (isEnterpriseMode) R.string.dashboard_enterprises_search_empty else R.string.dashboard_teams_search_empty)
            myTeamsSection.isVisible = false
            exploreSection.isVisible = false
            updateLoadingVisibility()
            return
        }
        renderTeams(members, available, memberCounts)
        updateLoadingVisibility()
    }

    internal data class TeamViewHolder(
        val teamId: String?,
        val bookmark: ImageButton?,
    )

    internal fun updateBookmarkSelection() {
        val selectedId = selectedTeamId
        if (!::myTeamsContainer.isInitialized) return
        for (i in 0 until myTeamsContainer.childCount) {
            val card = myTeamsContainer.getChildAt(i)
            val viewHolder = card.tag as? TeamViewHolder
            val bookmark = viewHolder?.bookmark
            val teamId = viewHolder?.teamId
            if (bookmark != null && teamId != null) {
                bookmark.setImageResource(
                    if (teamId == selectedId) R.drawable.ic_bookmark_selected_24 else R.drawable.ic_bookmark_24,
                )
            }
        }
    }

    internal fun updateLoadingVisibility() {
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

    internal fun handleLoadError() {
        isLoading = false
        showEmptyState(loadErrorMessageRes())
    }

    internal fun showEmptyState(messageRes: Int) {
        myTeamsContainer.removeAllViews()
        exploreTeamsContainer.removeAllViews()
        emptyView.setText(messageRes)
        myTeamsSection.isVisible = false
        exploreSection.isVisible = false
        updateLoadingVisibility()
    }

    internal fun stopRefreshing() {
        if (::refreshLayout.isInitialized && refreshLayout.isRefreshing) {
            refreshLayout.isRefreshing = false
        }
    }

    internal fun resolveMembersLabel(
        team: TeamDocument,
        memberCounts: Map<String, Int>,
    ): String {
        val count = memberCounts[team.id] ?: team.memberCount ?: team.membersCount ?: team.members?.size
        return if (count != null) {
            val safeCount = count.coerceAtLeast(0)
            resources.getQuantityString(R.plurals.dashboard_teams_member_count, safeCount, safeCount)
        } else {
            getString(R.string.dashboard_teams_member_unknown)
        }
    }

    internal fun resolveTeamName(team: TeamDocument): String {
        val candidate =
            listOf(
                team.name,
                team.teamName,
                team.teamPlanetCode,
                team.planetCode,
                team.id,
            ).firstOrNull { !it.isNullOrBlank() }
        return candidate ?: getString(R.string.dashboard_teams_unknown_name)
    }

    internal fun buildInitials(name: String): String {
        val parts = name.trim().split(" ", limit = 2).filter { it.isNotBlank() }
        if (parts.isEmpty()) return getString(R.string.dashboard_teams_default_initials)
        if (parts.size == 1) {
            return parts.first().take(2).uppercase()
        }
        return "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
    }

    internal fun getSelectedItemId(): String? = if (isEnterpriseMode) {
        DashboardEnterpriseSelectionPreferences.getSelectedEnterpriseId(requireContext())
    } else {
        DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
    }

    internal fun setSelectedItem(id: String?, name: String?) {
        if (isEnterpriseMode) {
            DashboardEnterpriseSelectionPreferences.setSelectedEnterprise(requireContext(), id, name)
        } else {
            DashboardTeamSelectionPreferences.setSelectedTeam(requireContext(), id, name)
        }
    }

    private fun configureEnterpriseLabels(view: View) {
        view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.teamsSearchLayout)
            .hint = getString(R.string.dashboard_enterprises_search_hint)
        view.findViewById<TextView>(R.id.myTeamsTitle).setText(R.string.dashboard_enterprises_my_title)
        view.findViewById<TextView>(R.id.myTeamsDescription).setText(R.string.dashboard_enterprises_my_description)
        view.findViewById<TextView>(R.id.selectedTeamHint).setText(R.string.dashboard_enterprises_select_hint)
        view.findViewById<TextView>(R.id.exploreTeamsTitle).setText(R.string.dashboard_enterprises_explore_title)
        view.findViewById<TextView>(R.id.exploreTeamsDescription).setText(R.string.dashboard_enterprises_explore_description)
    }

    internal fun loadErrorMessageRes() =
        if (isEnterpriseMode) R.string.dashboard_enterprises_error_loading else R.string.dashboard_teams_error_loading

    internal fun joinPromptMessageRes() =
        if (isEnterpriseMode) R.string.dashboard_enterprises_join_prompt else R.string.dashboard_teams_join_prompt

    companion object {
        private const val ARG_ENTERPRISE_MODE = "enterprise_mode"

        fun newEnterprisesInstance() = TeamsFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_ENTERPRISE_MODE, true) }
        }
    }
}
