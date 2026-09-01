package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseSelectionPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseTasksRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterprisesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterprisesRepository.EnterpriseMembersResult
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsDependencies
import org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails
import org.ole.planet.myplanet.lite.dashboard.TeamDocument
import org.ole.planet.myplanet.lite.databinding.DialogInviteMembersBinding
import org.ole.planet.myplanet.lite.databinding.FragmentDashboardTeamMembersBinding
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.enableDrag

class DashboardEnterpriseMembersFragment : Fragment() {
    private var _binding: FragmentDashboardTeamMembersBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid while the view exists")
    private val repository = DashboardEnterprisesRepository()
    private val teamsRepository = DashboardTeamsDependencies.provideRepository()
    private val tasksRepository = DashboardEnterpriseTasksRepository()
    private var avatarLoader: DashboardAvatarLoader? = null
    private var enterpriseAvatarLoader: DashboardEnterpriseAvatarLoader? = null
    private var members: List<TeamMemberDetails> = emptyList()
    private var selectedEnterpriseId: String? = null
    private var selectedEnterprise: TeamDocument? = null
    private var baseUrl: String? = null
    private var credentials: StoredCredentials? = null
    private var sessionCookie: String? = null
    private var serverPlanetCode: String? = null
    private var serverParentCode: String? = null
    private var isCurrentUserLeader = false
    private var loadJob: Job? = null

    private val membersAdapter = TeamMembersAdapter(
        avatarBinder = { imageView, username, _ ->
            val member = members.firstOrNull { it.username == username }
            enterpriseAvatarLoader?.bind(imageView, member?.userId, member?.userPlanetCode, username)
        },
        onMemberClicked = { member -> openTeamMemberProfileSupport(member) },
        onRemoveMemberClicked = { member -> confirmMemberRemoval(member) },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentDashboardTeamMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.dashboardTeamMembersList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = membersAdapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        binding.dashboardTeamMembersListTitle.setText(R.string.dashboard_enterprise_members_title)
        binding.dashboardTeamJoinRequestsTitle.isVisible = false
        binding.dashboardTeamJoinRequestsList.isVisible = false
        binding.fabAddMember.isVisible = false
        binding.fabAddMember.setOnClickListener { showInviteMembersDialog() }
        binding.fabAddMember.enableDrag()
        binding.dashboardTeamMembersSwipeRefresh.setOnRefreshListener { loadSelectedEnterprise() }
        binding.dashboardTeamMembersSearchInput.addTextChangedListener { applySearch(it?.toString().orEmpty()) }
        loadSelectedEnterprise()
    }

    override fun onResume() {
        super.onResume()
        val latestSelection = DashboardEnterpriseSelectionPreferences.getSelectedEnterpriseId(requireContext())
        if (latestSelection != selectedEnterpriseId) loadSelectedEnterprise()
    }

    private fun loadSelectedEnterprise() {
        val context = requireContext().applicationContext
        val enterpriseId = DashboardEnterpriseSelectionPreferences.getSelectedEnterpriseId(context)
        selectedEnterpriseId = enterpriseId
        if (enterpriseId.isNullOrBlank()) {
            showMessage(R.string.dashboard_enterprise_members_select_hint)
            return
        }
        baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        serverPlanetCode = DashboardServerPreferences.getServerCode(context)
        serverParentCode = DashboardServerPreferences.getServerParentCode(context)
        credentials = ProfileCredentialsStore.getStoredCredentials(context)
        val base = baseUrl
        val planetCode = serverPlanetCode
        val creds = credentials
        if (base.isNullOrBlank() || planetCode.isNullOrBlank() || creds == null) {
            showMessage(R.string.dashboard_enterprise_members_error_loading)
            return
        }
        showLoading(true)
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val authService = AuthDependencies.provideAuthService(context, base)
            sessionCookie = withContext(Dispatchers.IO) { authService.getStoredToken() }
            if (avatarLoader == null) {
                avatarLoader = DashboardAvatarLoader(base, sessionCookie, creds, viewLifecycleOwner.lifecycleScope)
                enterpriseAvatarLoader = DashboardEnterpriseAvatarLoader(
                    base,
                    sessionCookie,
                    creds,
                    viewLifecycleOwner.lifecycleScope,
                    avatarLoader!!,
                )
            }
            val result = repository.fetchEnterpriseMembers(
                base,
                creds,
                sessionCookie,
                enterpriseId,
                "org.couchdb.user:${creds.username}",
                planetCode,
            ).getOrElse {
                showMessage(R.string.dashboard_enterprise_members_error_loading)
                return@launch
            }
            when (result) {
                EnterpriseMembersResult.NotMember -> showMessage(R.string.dashboard_enterprise_members_not_member)
                is EnterpriseMembersResult.Success -> {
                    members = result.members
                    selectedEnterprise = result.enterprise
                    isCurrentUserLeader = members.any {
                        it.isLeader && it.username.equals(creds.username, ignoreCase = true)
                    }
                    binding.fabAddMember.isVisible = isCurrentUserLeader
                    if (members.isEmpty()) {
                        showMessage(R.string.dashboard_enterprise_members_empty)
                    } else {
                        showLoading(false)
                        applySearch(binding.dashboardTeamMembersSearchInput.text?.toString().orEmpty())
                    }
                }
            }
        }
    }

    private fun showInviteMembersDialog() {
        val enterprise = selectedEnterprise ?: return
        val enterpriseId = enterprise.id ?: return
        val enterprisePlanetCode = enterprise.teamPlanetCode ?: return
        val base = baseUrl ?: return
        val creds = credentials ?: return
        val loader = avatarLoader ?: return
        if (!isCurrentUserLeader) return

        DashboardTeamMembersInviteDialogController(
            fragment = this,
            dialogBinding = DialogInviteMembersBinding.inflate(layoutInflater),
            repository = teamsRepository,
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            avatarLoader = loader,
            base = base,
            creds = creds,
            teamId = enterpriseId,
            teamPlanetCode = enterprisePlanetCode,
            teamType = enterprise.teamType ?: "sync",
            sessionCookie = sessionCookie,
            serverPlanetCode = serverPlanetCode,
            serverParentCode = serverParentCode,
            currentMembersProvider = { members },
            onReload = { loadSelectedEnterprise() },
        ).show()
    }

    private fun confirmMemberRemoval(member: TeamMemberDetails) {
        if (!isCurrentUserLeader) return
        confirmMemberRemovalDialog(this, member) { selectedMember, displayName ->
            runRemoveTeamMember(
                DashboardTeamActionContext(
                    fragment = this,
                    repository = teamsRepository,
                    baseUrl = baseUrl,
                    credentials = credentials,
                    sessionCookie = sessionCookie,
                ),
                selectedEnterpriseId,
                selectedMember,
                displayName,
                onStart = { binding.dashboardTeamMembersSwipeRefresh.isRefreshing = true },
                onStop = { binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false },
                onReload = {
                    val base = baseUrl
                    val creds = credentials
                    val enterpriseId = selectedEnterpriseId
                    val userId = selectedMember.userId
                    if (base != null && creds != null && enterpriseId != null && userId != null) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            tasksRepository.unassignMemberTasks(
                                base, creds, sessionCookie, enterpriseId, userId,
                            )
                            loadSelectedEnterprise()
                        }
                    } else {
                        loadSelectedEnterprise()
                    }
                },
            )
        }
    }

    private fun applySearch(rawQuery: String) {
        val query = rawQuery.trim()
        val filtered = if (query.isEmpty()) members else members.filter {
            it.fullName.orEmpty().contains(query, true) || it.username.orEmpty().contains(query, true)
        }
        membersAdapter.submitList(
            filtered.map {
                val membership = it.membership
                TeamMemberUiModel(
                    member = it,
                    showRemoveAction = isCurrentUserLeader &&
                        membership != null &&
                        !membership.id.isNullOrBlank() &&
                        !membership.revision.isNullOrBlank(),
                    currentUsername = credentials?.username,
                )
            },
        )
        binding.dashboardTeamMembersSearchEmptyView.isVisible = filtered.isEmpty()
        if (filtered.isEmpty() && members.isNotEmpty()) {
            binding.dashboardTeamMembersSearchEmptyView.setText(R.string.dashboard_team_members_search_empty)
        }
        binding.dashboardTeamMembersList.isVisible = filtered.isNotEmpty()
        binding.dashboardTeamMembersListTitle.isVisible = filtered.isNotEmpty()
    }

    private fun showLoading(loading: Boolean) {
        binding.dashboardTeamMembersLoading.isVisible = loading
        binding.dashboardTeamMembersContent.isVisible = !loading
        binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
        if (loading) binding.dashboardTeamMembersSearchEmptyView.isVisible = false
    }

    private fun showMessage(messageRes: Int) {
        members = emptyList()
        selectedEnterprise = null
        isCurrentUserLeader = false
        binding.fabAddMember.isVisible = false
        membersAdapter.submitList(emptyList())
        showLoading(false)
        binding.dashboardTeamMembersContent.isVisible = false
        binding.dashboardTeamMembersSearchEmptyView.setText(messageRes)
        binding.dashboardTeamMembersSearchEmptyView.isVisible = true
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        avatarLoader?.destroy()
        avatarLoader = null
        enterpriseAvatarLoader = null
        _binding = null
        super.onDestroyView()
    }
}
