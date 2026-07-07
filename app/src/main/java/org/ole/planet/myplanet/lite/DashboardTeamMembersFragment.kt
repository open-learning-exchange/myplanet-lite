/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamMemberProfileActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.dashboard.JoinRequestDocument
import org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails
import org.ole.planet.myplanet.lite.databinding.DialogInviteMembersBinding
import org.ole.planet.myplanet.lite.databinding.FragmentDashboardTeamMembersBinding
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.enableDrag

class DashboardTeamMembersFragment : Fragment() {
    private var _binding: FragmentDashboardTeamMembersBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid between onCreateView and onDestroyView")

    private val repository = DashboardTeamsDependencies.provideRepository()
    private var avatarLoader: DashboardAvatarLoader? = null

    private val adapter =
        TeamMembersAdapter(
            avatarBinder = { imageView, username, hasAvatar ->
                val shouldAttemptLoad = hasAvatar || !username.isNullOrBlank()
                avatarLoader?.bind(imageView, username, shouldAttemptLoad)
            },
            onMemberClicked = { member ->
                openTeamMemberProfile(member)
            },
            onRemoveMemberClicked = { member ->
                confirmMemberRemoval(member)
            },
        )
    private val joinRequestsAdapter =
        TeamJoinRequestsAdapter(
            avatarBinder = { imageView, username, hasAvatar ->
                val shouldAttemptLoad = hasAvatar || !username.isNullOrBlank()
                avatarLoader?.bind(imageView, username, shouldAttemptLoad)
            },
            onAcceptClicked = { request ->
                confirmAcceptJoinRequest(request)
            },
            onRejectClicked = { request ->
                confirmRejectJoinRequest(request)
            },
        )

    private var currentMembers: List<TeamMemberDetails> = emptyList()
    private var currentJoinRequests: List<TeamJoinRequestUiModel> = emptyList()
    private var searchQuery: String = ""
    private var currentTeamId: String? = null
    private var baseUrl: String? = null
    private var credentials: StoredCredentials? = null
    private var sessionCookie: String? = null
    private var serverPlanetCode: String? = null
    private var serverParentCode: String? = null
    private var currentTeamPlanetCode: String? = null
    private var currentTeamType: String? = null
    private var isCurrentUserTeamLeader: Boolean = false
    private var currentUsername: String? = null
    private var fetchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardTeamMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        fetchJob?.cancel()
        fetchJob = null
        binding.dashboardTeamMembersList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DashboardTeamMembersFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        binding.dashboardTeamJoinRequestsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DashboardTeamMembersFragment.joinRequestsAdapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        binding.dashboardTeamMembersSwipeRefresh.setOnRefreshListener { onRefreshRequested() }
        binding.dashboardTeamMembersSearchInput.addTextChangedListener { editable ->
            searchQuery = editable?.toString().orEmpty()
            applySearchFilter()
        }
        binding.fabAddMember.setOnClickListener {
            animateFabClick(binding.fabAddMember)
            showInviteMembersDialog()
        }
        updateLeaderActionsVisibility()
        binding.fabAddMember.enableDrag()
        viewLifecycleOwner.lifecycleScope.launch {
            loadConnectionInfo()
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            if (baseUrl == null || credentials == null) {
                loadConnectionInfo()
            }
            refreshSelectionState()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fetchJob?.cancel()
        fetchJob = null
        avatarLoader?.destroy()
        avatarLoader = null
        binding.dashboardTeamMembersSwipeRefresh.setOnRefreshListener(null as SwipeRefreshLayout.OnRefreshListener?)
        _binding = null
    }

    private suspend fun loadConnectionInfo() {
        val context = requireContext().applicationContext
        baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        serverPlanetCode = DashboardServerPreferences.getServerCode(context)
        serverParentCode = DashboardServerPreferences.getServerParentCode(context)
        credentials = ProfileCredentialsStore.getStoredCredentials(context)
        baseUrl?.let { base ->
            val authService = AuthDependencies.provideAuthService(context, base)
            sessionCookie = withContext(Dispatchers.IO) { authService.getStoredToken() }
            if (avatarLoader == null) {
                avatarLoader = DashboardAvatarLoader(base, sessionCookie, credentials, viewLifecycleOwner.lifecycleScope)
            }
        }
    }

    private fun refreshSelectionState() {
        val selectedTeamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
        if (selectedTeamId == currentTeamId && currentMembers.isNotEmpty()) {
            applySearchFilter()
            return
        }
        currentTeamId = selectedTeamId
        val hasSelection = !currentTeamId.isNullOrBlank()

        if (!hasSelection) {
            showEmptyState(getString(R.string.dashboard_teams_select_team_hint))
            binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
            return
        }

        currentTeamId?.let { teamId ->
            binding.dashboardTeamMembersSwipeRefresh.isRefreshing = true
            loadTeamMembers(teamId)
        }
    }

    private fun onRefreshRequested() {
        val teamId = currentTeamId
        if (teamId.isNullOrBlank()) {
            binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
            refreshSelectionState()
            return
        }
        loadTeamMembers(teamId, isPullToRefresh = true)
    }

    private fun openTeamMemberProfile(member: TeamMemberDetails) {
        val username = member.username
        if (username.isNullOrBlank()) {
            Toast
                .makeText(
                    requireContext(),
                    R.string.dashboard_team_members_profile_unavailable,
                    Toast.LENGTH_SHORT,
                ).show()
            return
        }
        val displayName = member.fullName?.ifBlank { null } ?: username
        val intent =
            DashboardTeamMemberProfileActivity.buildIntent(
                requireContext(),
                username,
                displayName,
                member.isLeader,
            )
        startActivity(intent)
    }

    private fun handleLoadError(messageResId: Int) {
        showEmptyState(getString(messageResId))
        binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
        isCurrentUserTeamLeader = false
        updateLeaderActionsVisibility()
    }

    private fun processTeamMembers(
        members: List<TeamMemberDetails>,
        creds: StoredCredentials,
    ) {
        val sortedMembers =
            members.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { member ->
                    member.fullName?.takeIf { it.isNotBlank() }
                        ?: member.username.orEmpty()
                },
            )
        val normalizedCurrentUsername =
            creds.username.substringAfter(
                "org.couchdb.user:",
                creds.username,
            )
        currentMembers = sortedMembers
        currentTeamPlanetCode =
            sortedMembers.firstNotNullOfOrNull { member ->
                member.membership?.teamPlanetCode?.takeIf { it.isNotBlank() }
            }
        currentTeamType = sortedMembers.firstNotNullOfOrNull { member ->
            member.membership?.teamType?.takeIf { it.isNotBlank() }
        } ?: "local"
        isCurrentUserTeamLeader =
            sortedMembers.any { member ->
                val username = member.username
                member.isLeader && (
                    username.equals(creds.username, ignoreCase = true) ||
                        username.equals(normalizedCurrentUsername, ignoreCase = true)
                )
            }
        currentUsername = normalizedCurrentUsername
        updateLeaderActionsVisibility()
    }

    private suspend fun fetchAndProcessJoinRequests(
        base: String,
        creds: StoredCredentials,
        teamId: String,
    ) {
        if (!isCurrentUserTeamLeader) {
            hideJoinRequestsSection()
            return
        }

        val teamPlanetCodeForRequests = currentTeamPlanetCode ?: serverPlanetCode
        val joinRequests =
            if (teamPlanetCodeForRequests.isNullOrBlank()) {
                emptyList<JoinRequestDocument>()
            } else {
                repository
                    .fetchTeamJoinRequests(
                        baseUrl = base,
                        credentials = creds,
                        sessionCookie = sessionCookie,
                        teamId = teamId,
                        teamPlanetCode = teamPlanetCodeForRequests,
                    ).getOrElse {
                        showJoinRequestsError()
                        null
                    }
            }
        if (joinRequests != null) {
            loadJoinRequestsData(base, creds, joinRequests)
        } else {
            hideJoinRequestsSection()
        }
    }

    private fun loadTeamMembers(
        teamId: String,
        isPullToRefresh: Boolean = false,
    ) {
        val base = baseUrl
        val creds = credentials
        if (base.isNullOrBlank()) {
            handleLoadError(R.string.dashboard_team_members_missing_server)
            return
        }
        if (creds == null) {
            handleLoadError(R.string.dashboard_team_members_missing_credentials)
            return
        }

        showLoading(!isPullToRefresh)
        fetchJob?.cancel()
        fetchJob =
            viewLifecycleOwner.lifecycleScope.launch {
                val result = repository.fetchTeamMemberDetails(base, creds, sessionCookie, teamId)
                val members =
                    result.getOrElse {
                        handleLoadError(R.string.dashboard_team_members_error_loading)
                        return@launch
                    }
                if (members.isEmpty()) {
                    handleLoadError(R.string.dashboard_team_members_empty)
                    return@launch
                }

                processTeamMembers(members, creds)
                fetchAndProcessJoinRequests(base, creds, teamId)

                searchQuery =
                    binding.dashboardTeamMembersSearchInput.text
                        ?.toString()
                        .orEmpty()
                applySearchFilter()
                binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
                showLoading(false)
            }
    }

    private suspend fun loadJoinRequestsData(
        base: String,
        creds: StoredCredentials,
        joinRequests: List<JoinRequestDocument>,
    ) {
        if (joinRequests.isEmpty()) {
            currentJoinRequests = emptyList()
            updateJoinRequestsAdapterList(emptyList())
            showJoinRequestsEmptyState()
            return
        }

        val userIds = joinRequests.mapNotNull { it.userId?.takeIf(String::isNotBlank) }.distinct()
        val profilesById =
            repository
                .fetchUserProfiles(base, creds, sessionCookie, userIds)
                .getOrDefault(emptyList())
                .associateBy { it._id }
        val requests =
            joinRequests
                .map { request ->
                    val userId = request.userId.orEmpty()
                    val profile = profilesById[userId]
                    val username = userId.substringAfter("org.couchdb.user:", userId)
                    val fullName =
                        listOfNotNull(
                            profile?.firstName,
                            profile?.middleName,
                            profile?.lastName,
                        ).filter { it.isNotBlank() }.joinToString(" ").ifBlank { username }
                    TeamJoinRequestUiModel(
                        id = request.id ?: userId,
                        username = username,
                        fullName = fullName,
                        hasAvatar = profile?.attachments?.image != null,
                        request = request,
                    )
                }.sortedBy { it.fullName.lowercase() }

        currentJoinRequests = requests
        updateJoinRequestsAdapterList(requests)
    }

    private fun showLoading(loading: Boolean) {
        binding.dashboardTeamMembersLoading.isVisible = loading
        binding.dashboardTeamMembersContent.isVisible = !loading
        if (loading) {
            binding.dashboardTeamMembersSearchEmptyView.isVisible = false
        }
    }

    private fun applySearchFilter() {
        val query = searchQuery.trim().lowercase()
        if (currentMembers.isEmpty()) {
            updateTeamMembersAdapterList(emptyList())
            binding.dashboardTeamMembersSearchEmptyView.isVisible = false
            binding.dashboardTeamMembersList.isVisible = false
            binding.dashboardTeamMembersListTitle.isVisible = false
            return
        }
        if (query.isEmpty()) {
            updateTeamMembersAdapterList(currentMembers)
            binding.dashboardTeamMembersSearchEmptyView.isVisible = false
            binding.dashboardTeamMembersList.isVisible = true
            binding.dashboardTeamMembersListTitle.isVisible = true
            return
        }
        val filtered =
            currentMembers.filter { member ->
                val name = member.fullName?.lowercase().orEmpty()
                val username = member.username?.lowercase().orEmpty()
                name.contains(query) || username.contains(query)
            }
        updateTeamMembersAdapterList(filtered)
        val hasResults = filtered.isNotEmpty()
        binding.dashboardTeamMembersSearchEmptyView.isVisible = !hasResults
        binding.dashboardTeamMembersList.isVisible = hasResults
        binding.dashboardTeamMembersListTitle.isVisible = hasResults
    }

    private fun showEmptyState(message: String) {
        showLoading(false)
        binding.dashboardTeamMembersSearchEmptyView.text = message
        binding.dashboardTeamMembersSearchEmptyView.isVisible = true
        binding.dashboardTeamMembersContent.isVisible = false
        currentMembers = emptyList()
        currentJoinRequests = emptyList()
        currentTeamPlanetCode = null
        currentTeamType = null
        updateTeamMembersAdapterList(emptyList())
        updateJoinRequestsAdapterList(emptyList())
        isCurrentUserTeamLeader = false
        updateLeaderActionsVisibility()
    }

    private fun hideJoinRequestsSection() {
        currentJoinRequests = emptyList()
        joinRequestsAdapter.submitList(emptyList())
        binding.dashboardTeamJoinRequestsTitle.isVisible = false
        binding.dashboardTeamJoinRequestsList.isVisible = false
    }

    private fun showJoinRequestsEmptyState() {
        binding.dashboardTeamJoinRequestsTitle.isVisible = false
        binding.dashboardTeamJoinRequestsList.isVisible = false
    }

    private fun showJoinRequestsError() {
        binding.dashboardTeamJoinRequestsTitle.isVisible = false
        binding.dashboardTeamJoinRequestsList.isVisible = false
        Toast
            .makeText(
                requireContext(),
                getString(R.string.dashboard_team_members_requests_error_loading),
                Toast.LENGTH_SHORT,
            ).show()
    }

    private fun updateLeaderActionsVisibility() {
        binding.fabAddMember.isVisible = isCurrentUserTeamLeader
    }

    private fun updateTeamMembersAdapterList(list: List<TeamMemberDetails>) {
        val uiModels = list.map { TeamMemberUiModel(it, isCurrentUserTeamLeader, currentUsername) }
        adapter.submitList(uiModels)
    }

    private fun updateJoinRequestsAdapterList(list: List<TeamJoinRequestUiModel>) {
        joinRequestsAdapter.submitList(list)
        val hasRequests = list.isNotEmpty()
        binding.dashboardTeamJoinRequestsTitle.isVisible = hasRequests
        binding.dashboardTeamJoinRequestsTitle.text = getString(R.string.dashboard_team_members_requests_title)
        binding.dashboardTeamJoinRequestsList.isVisible = hasRequests
    }

    private fun createActionContext(): DashboardTeamActionContext =
        DashboardTeamActionContext(this, repository, baseUrl, credentials, sessionCookie)

    private fun acceptJoinRequest(request: TeamJoinRequestUiModel) =
        runAcceptJoinRequest(
            AcceptJoinRequestParams(
                fragment = this,
                repository = repository,
                baseUrl = baseUrl,
                credentials = credentials,
                sessionCookie = sessionCookie,
                request = request,
                currentTeamPlanetCode = currentTeamPlanetCode,
                serverPlanetCode = serverPlanetCode,
                onReload = { currentTeamId?.let(::loadTeamMembers) },
            ),
        )

    private fun confirmAcceptJoinRequest(request: TeamJoinRequestUiModel) {
        confirmAcceptJoinRequestDialog(this, request) { acceptJoinRequest(it) }
    }

    private fun rejectJoinRequest(request: TeamJoinRequestUiModel) =
        runRejectJoinRequest(createActionContext(), request) {
            currentTeamId?.let(::loadTeamMembers)
        }

    private fun confirmRejectJoinRequest(request: TeamJoinRequestUiModel) {
        confirmRejectJoinRequestDialog(this, request) { rejectJoinRequest(it) }
    }

    private fun confirmMemberRemoval(member: TeamMemberDetails) =
        confirmMemberRemovalDialog(this, member) { selectedMember, displayName ->
            runRemoveTeamMember(
                createActionContext(),
                currentTeamId,
                selectedMember,
                displayName,
                onStart = { binding.dashboardTeamMembersSwipeRefresh.isRefreshing = true },
                onStop = { binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false },
                onReload = { currentTeamId?.let(::loadTeamMembers) },
            )
        }

    private fun showInviteMembersDialog() {
        val base = baseUrl
        val creds = credentials
        if (base.isNullOrBlank() || creds == null) {
            Toast.makeText(requireContext(), getString(R.string.dashboard_invite_members_load_error), Toast.LENGTH_SHORT).show()
            return
        }

        if (avatarLoader == null) {
            avatarLoader = DashboardAvatarLoader(base, sessionCookie, creds, viewLifecycleOwner.lifecycleScope)
        }

        val teamId = currentTeamId
        val teamPlanetCode = currentTeamPlanetCode ?: serverPlanetCode
        val teamType = currentTeamType ?: "local"
        if (teamId.isNullOrBlank() || teamPlanetCode.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.dashboard_invite_members_add_error_generic), Toast.LENGTH_SHORT).show()
            return
        }

        DashboardTeamMembersInviteDialogController(
            fragment = this,
            dialogBinding = DialogInviteMembersBinding.inflate(layoutInflater),
            repository = repository,
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            avatarLoader = avatarLoader,
            base = base,
            creds = creds,
            teamId = teamId,
            teamPlanetCode = teamPlanetCode,
            teamType = teamType,
            sessionCookie = sessionCookie,
            serverPlanetCode = serverPlanetCode,
            serverParentCode = serverParentCode,
            currentMembersProvider = { currentMembers },
            onReload = { currentTeamId?.let { loadTeamMembers(it) } },
        ).show()
    }

    private fun animateFabClick(fab: FloatingActionButton) = animateFabClickSupport(fab)
}
