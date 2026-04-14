/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ListAdapter
import org.ole.planet.myplanet.lite.util.DiffUtils
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamMemberProfileActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository.TeamMemberDetails
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository.UserDocument
import org.ole.planet.myplanet.lite.databinding.DialogInviteMembersBinding
import org.ole.planet.myplanet.lite.databinding.FragmentDashboardTeamMembersBinding
import org.ole.planet.myplanet.lite.databinding.ItemInviteMemberBinding
import org.ole.planet.myplanet.lite.databinding.ItemTeamMemberBinding
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.enableDrag

class DashboardTeamMembersFragment : Fragment() {

    private var _binding: FragmentDashboardTeamMembersBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid between onCreateView and onDestroyView")

    private val repository = DashboardTeamsRepository()
    private var avatarLoader: DashboardAvatarLoader? = null

    private val adapter = TeamMembersAdapter(
        avatarBinder = { imageView, username, hasAvatar ->
            val shouldAttemptLoad = hasAvatar || !username.isNullOrBlank()
            avatarLoader?.bind(imageView, username, shouldAttemptLoad)
        },
        onMemberClicked = { member ->
            openTeamMemberProfile(member)
        },
        onRemoveMemberClicked = { member ->
            confirmMemberRemoval(member)
        }
    )

    private var currentMembers: List<TeamMemberDetails> = emptyList()
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardTeamMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dashboardTeamMembersList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DashboardTeamMembersFragment.adapter
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )
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
            refreshSelectionState()
        }
    }

    override fun onResume() {
        super.onResume()
        val selectedTeamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
        if (selectedTeamId != currentTeamId) {
            refreshSelectionState()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
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
        currentTeamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
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
            Toast.makeText(
                requireContext(),
                R.string.dashboard_team_members_profile_unavailable,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val displayName = member.fullName?.ifBlank { null } ?: username
        val intent = DashboardTeamMemberProfileActivity.buildIntent(
            requireContext(),
            username,
            displayName,
            member.isLeader
        )
        startActivity(intent)
    }

    private fun loadTeamMembers(teamId: String, isPullToRefresh: Boolean = false) {
        val base = baseUrl
        val creds = credentials
        if (base.isNullOrBlank()) {
            showEmptyState(getString(R.string.dashboard_team_members_missing_server))
            binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
            isCurrentUserTeamLeader = false
            updateLeaderActionsVisibility()
            return
        }
        if (creds == null) {
            showEmptyState(getString(R.string.dashboard_team_members_missing_credentials))
            binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
            isCurrentUserTeamLeader = false
            updateLeaderActionsVisibility()
            return
        }

        showLoading(!isPullToRefresh)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.fetchTeamMemberDetails(base, creds, sessionCookie, teamId)
            val members = result.getOrElse {
                showEmptyState(getString(R.string.dashboard_team_members_error_loading))
                binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
                isCurrentUserTeamLeader = false
                updateLeaderActionsVisibility()
                return@launch
            }
            if (members.isEmpty()) {
                showEmptyState(getString(R.string.dashboard_team_members_empty))
                binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
                isCurrentUserTeamLeader = false
                updateLeaderActionsVisibility()
                return@launch
            }
            val sortedMembers = members.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { member ->
                    member.fullName?.takeIf { it.isNotBlank() }
                        ?: member.username.orEmpty()
                }
            )
            val normalizedCurrentUsername = creds.username.substringAfter(
                "org.couchdb.user:",
                creds.username
            )
            currentMembers = sortedMembers
            currentTeamPlanetCode = sortedMembers.firstNotNullOfOrNull { member ->
                member.membership?.teamPlanetCode?.takeIf { it.isNotBlank() }
            }
            currentTeamType = sortedMembers.firstNotNullOfOrNull { member ->
                member.membership?.teamType?.takeIf { it.isNotBlank() }
            } ?: "local"
            isCurrentUserTeamLeader = sortedMembers.any { member ->
                val username = member.username
                member.isLeader && (
                    username.equals(creds.username, ignoreCase = true) ||
                        username.equals(normalizedCurrentUsername, ignoreCase = true)
                    )
            }
            updateLeaderActionsVisibility()
            searchQuery = binding.dashboardTeamMembersSearchInput.text?.toString().orEmpty()
            applySearchFilter()
            binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
            showLoading(false)
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.dashboardTeamMembersLoading.isVisible = loading
        binding.dashboardTeamMembersList.isVisible = !loading
        if (loading) {
            binding.dashboardTeamMembersSearchEmptyView.isVisible = false
        }
    }

    private fun applySearchFilter() {
        val query = searchQuery.trim().lowercase()
        if (currentMembers.isEmpty()) {
            submitTeamModels(emptyList())
            binding.dashboardTeamMembersSearchEmptyView.isVisible = false
            binding.dashboardTeamMembersList.isVisible = false
            return
        }
        if (query.isEmpty()) {
            submitTeamModels(currentMembers)
            binding.dashboardTeamMembersSearchEmptyView.isVisible = false
            binding.dashboardTeamMembersList.isVisible = true
            return
        }
        val filtered = currentMembers.filter { member ->
            val name = member.fullName?.lowercase().orEmpty()
            val username = member.username?.lowercase().orEmpty()
            name.contains(query) || username.contains(query)
        }
        submitTeamModels(filtered)
        val hasResults = filtered.isNotEmpty()
        binding.dashboardTeamMembersSearchEmptyView.isVisible = !hasResults
        binding.dashboardTeamMembersList.isVisible = hasResults
    }

    private fun showEmptyState(message: String) {
        showLoading(false)
        binding.dashboardTeamMembersSearchEmptyView.text = message
        binding.dashboardTeamMembersSearchEmptyView.isVisible = true
        binding.dashboardTeamMembersList.isVisible = false
        currentMembers = emptyList()
        currentTeamPlanetCode = null
        currentTeamType = null
        isCurrentUserTeamLeader = false
        updateLeaderActionsVisibility()
        submitTeamModels(emptyList())
    }

    private fun updateLeaderActionsVisibility() {
        binding.fabAddMember.isVisible = isCurrentUserTeamLeader
    }

    private fun submitTeamModels(members: List<TeamMemberDetails>) {
        val currentUsername = credentials?.username?.substringAfter("org.couchdb.user:")
        adapter.submitList(members.map {
            TeamMemberUiModel(
                member = it,
                showRemoveAction = isCurrentUserTeamLeader,
                isCurrentUser = currentUsername != null && it.username.equals(currentUsername, ignoreCase = true)
            )
        })
    }

    private fun confirmMemberRemoval(member: TeamMemberDetails) {
        val displayName = member.fullName?.takeIf { it.isNotBlank() }
            ?: member.username?.takeIf { it.isNotBlank() }
            ?: getString(R.string.dashboard_team_members_unknown)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dashboard_team_members_remove_confirmation_title)
            .setMessage(
                getString(
                    R.string.dashboard_team_members_remove_confirmation_message,
                    displayName
                )
            )
            .setNegativeButton(R.string.dashboard_team_members_remove_cancel, null)
            .setPositiveButton(R.string.dashboard_team_members_remove_action) { _, _ ->
                removeTeamMember(member, displayName)
            }
            .show()
    }

    private fun removeTeamMember(member: TeamMemberDetails, displayName: String) {
        val teamId = currentTeamId
        val base = baseUrl
        val creds = credentials
        val membership = member.membership

        if (teamId.isNullOrBlank() || base.isNullOrBlank() || creds == null || membership == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.dashboard_team_members_remove_error, displayName),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding.dashboardTeamMembersSwipeRefresh.isRefreshing = true

        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.removeTeamMember(base, creds, sessionCookie, membership)
            result.onSuccess {
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.dashboard_team_members_remove_success,
                        displayName
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                loadTeamMembers(teamId)
            }.onFailure {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.dashboard_team_members_remove_error, displayName),
                    Toast.LENGTH_SHORT
                ).show()
                binding.dashboardTeamMembersSwipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showInviteMembersDialog() {
        val base = baseUrl
        val creds = credentials
        if (base.isNullOrBlank() || creds == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.dashboard_invite_members_load_error),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (avatarLoader == null) {
            avatarLoader = DashboardAvatarLoader(base, sessionCookie, creds, viewLifecycleOwner.lifecycleScope)
        }

        val teamId = currentTeamId
        val teamPlanetCode = currentTeamPlanetCode ?: serverPlanetCode
        val teamType = currentTeamType ?: "local"
        if (teamId.isNullOrBlank() || teamPlanetCode.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.dashboard_invite_members_add_error_generic),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val dialogBinding = DialogInviteMembersBinding.inflate(layoutInflater)
        InviteDialogController(dialogBinding, base, creds, teamId, teamPlanetCode, teamType).show()
    }

    private inner class InviteDialogController(
        val dialogBinding: DialogInviteMembersBinding,
        val base: String,
        val creds: StoredCredentials,
        val teamId: String,
        val teamPlanetCode: String,
        val teamType: String
    ) {
        private var isLoadingPage = false
        private var hasMorePages = true
        private var nextSkip = 0
        private var currentSearchTerm = ""
        private var pendingReset = false
        private var pagingDialog: AlertDialog? = null
        private lateinit var inviteAdapter: InviteMembersAdapter
        private val allCandidates = mutableListOf<InviteCandidate>()
        private val disabledUsernames = mutableSetOf<String>()

        private fun submitInviteModels() {
            inviteAdapter.submitList(allCandidates.map {
                InviteCandidateUiModel(it, disabledUsernames.contains(it.username))
            })
        }

        fun show() {
            inviteAdapter = InviteMembersAdapter(avatarLoader) { candidate ->
                addMember(candidate)
            }

            val layoutManager = LinearLayoutManager(requireContext())
            dialogBinding.inviteMembersList.adapter = inviteAdapter
            dialogBinding.inviteMembersList.layoutManager = layoutManager
            dialogBinding.inviteMembersList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy <= 0) return
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    if (hasMorePages && !isLoadingPage && lastVisible >= inviteAdapter.itemCount - 5) {
                        loadInvitePage()
                    }
                }
            })

            dialogBinding.inviteMembersSearchInput.addTextChangedListener { editable ->
                val newQuery = editable?.toString()?.trim().orEmpty()
                if (newQuery != currentSearchTerm) {
                    currentSearchTerm = newQuery
                    loadInvitePage(reset = true)
                }
            }

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogBinding.root)
                .create()

            dialogBinding.inviteMembersCancel.setOnClickListener { dialog.dismiss() }

            dialog.setOnDismissListener {
                pagingDialog?.dismiss()
                pagingDialog = null
                currentTeamId?.let { id -> loadTeamMembers(id) }
            }

            dialog.show()
            loadInvitePage(reset = true)
        }

        private fun addMember(candidate: InviteCandidate) {
            val userPlanet = candidate.planetCode ?: serverPlanetCode
            if (userPlanet.isNullOrBlank()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.dashboard_invite_members_add_error_generic),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            disabledUsernames.add(candidate.username)
            submitInviteModels()
            viewLifecycleOwner.lifecycleScope.launch {
                val result = repository.addTeamMember(
                    baseUrl = base,
                    credentials = creds,
                    sessionCookie = sessionCookie,
                    teamId = teamId,
                    teamPlanetCode = teamPlanetCode,
                    teamType = teamType,
                    userId = "org.couchdb.user:${candidate.username}",
                    userPlanetCode = userPlanet,
                )
                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.dashboard_invite_members_add_success,
                            candidate.name
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure {
                    disabledUsernames.removeIf { it.equals(candidate.username, ignoreCase = true) }
                    submitInviteModels()
                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.dashboard_invite_members_add_error,
                            candidate.name
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        private fun showPagingDialog() {
            if (pagingDialog == null) {
                val progressBar = ProgressBar(requireContext())
                val padding = resources.getDimensionPixelSize(R.dimen.padding_small)
                progressBar.setPadding(padding, padding, padding, padding)
                pagingDialog = AlertDialog.Builder(requireContext())
                    .setView(progressBar)
                    .setCancelable(false)
                    .create().apply {
                        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    }
            }
            pagingDialog?.show()
        }

        private fun hidePagingDialog() {
            pagingDialog?.dismiss()
        }

        private fun prepareForLoad(reset: Boolean): Boolean {
            if (isLoadingPage) {
                if (reset) {
                    pendingReset = true
                }
                return false
            }
            if (!hasMorePages && !reset) return false
            isLoadingPage = true
            if (reset) {
                nextSkip = 0
                hasMorePages = true
                pendingReset = false
                allCandidates.clear()
                disabledUsernames.clear()
                submitInviteModels()
            }
            if (nextSkip == 0) {
                dialogBinding.inviteMembersLoading.isVisible = true
                dialogBinding.inviteMembersList.isVisible = false
            } else {
                showPagingDialog()
            }
            return true
        }

        private fun getExcludedIds(): List<String> {
            return currentMembers.mapNotNull { member ->
                val userId = member.membership?.userId
                when {
                    userId.isNullOrBlank() -> null
                    userId.startsWith("org.couchdb.user:") -> userId
                    else -> "org.couchdb.user:$userId"
                }
            }
        }

        private fun handleFetchSuccess(users: List<UserDocument>, reset: Boolean) {
            val candidates = users.mapNotNull { user ->
                toInviteCandidate(user)
            }
            if (reset) {
                allCandidates.clear()
                disabledUsernames.clear()
                allCandidates.addAll(candidates)
                submitInviteModels()
            } else {
                allCandidates.addAll(candidates)
                submitInviteModels()
            }
            if (candidates.size < INVITE_PAGE_SIZE) {
                hasMorePages = false
            } else {
                nextSkip += INVITE_PAGE_SIZE
            }
        }

        private fun finishLoad(reset: Boolean) {
            dialogBinding.inviteMembersLoading.isVisible = false
            dialogBinding.inviteMembersList.isVisible = true
            isLoadingPage = false
            hidePagingDialog()
            if (pendingReset) {
                pendingReset = false
                loadInvitePage(reset = true)
            }
        }

        private fun loadInvitePage(reset: Boolean = false) {
            if (!prepareForLoad(reset)) return

            viewLifecycleOwner.lifecycleScope.launch {
                val result = repository.fetchAllUsers(
                    baseUrl = base,
                    credentials = creds,
                    sessionCookie = sessionCookie,
                    planetCode = serverPlanetCode,
                    parentCode = serverParentCode,
                    pageSize = INVITE_PAGE_SIZE,
                    skip = nextSkip,
                    searchTerm = currentSearchTerm.takeIf { it.isNotBlank() },
                    excludedUserIds = getExcludedIds(),
                )
                result.onSuccess { users ->
                    handleFetchSuccess(users, reset)
                }.onFailure {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.dashboard_invite_members_load_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                finishLoad(reset)
            }
        }
    }

    private fun toInviteCandidate(user: UserDocument): InviteCandidate? {
        val username = user._id?.substringAfter("org.couchdb.user:")?.takeIf { it.isNotBlank() }
            ?: return null
        val planetMatches = serverPlanetCode.isNullOrBlank() ||
            user.planetCode.isNullOrBlank() ||
            serverPlanetCode.equals(user.planetCode, ignoreCase = true)
        val parentMatches = serverParentCode.isNullOrBlank() ||
            user.parentCode.isNullOrBlank() ||
            serverParentCode.equals(user.parentCode, ignoreCase = true)
        if (!planetMatches || !parentMatches) {
            return null
        }
        val nameParts = listOfNotNull(user.firstName, user.lastName).filter { it.isNotBlank() }
        val displayName = nameParts.joinToString(" ").takeIf { it.isNotBlank() }
            ?: user.email?.takeIf { it.isNotBlank() }
            ?: username
        val colorIndex = abs(username.hashCode()) % INVITE_PLACEHOLDER_COLORS.size
        return InviteCandidate(
            name = displayName,
            username = username,
            planetCode = user.planetCode,
            hasAvatar = user.attachments?.image != null,
            colorRes = INVITE_PLACEHOLDER_COLORS[colorIndex],
        )
    }

    private fun animateFabClick(fab: FloatingActionButton) {
        fab.animate()
            .rotationBy(360f)
            .setDuration(250)
            .withEndAction { fab.rotation = 0f }
            .start()
    }
}

private data class TeamMemberUiModel(
    val member: TeamMemberDetails,
    val showRemoveAction: Boolean,
    val isCurrentUser: Boolean
)

private data class InviteCandidateUiModel(
    val candidate: InviteCandidate,
    val isDisabled: Boolean
)

private class TeamMembersAdapter(
    private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
    private val onMemberClicked: (TeamMemberDetails) -> Unit,
    private val onRemoveMemberClicked: (TeamMemberDetails) -> Unit
) : ListAdapter<TeamMemberUiModel, TeamMemberViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old.member.username == new.member.username },
        areContentsTheSame = { old, new -> old == new },
        getChangePayload = { old, new -> if (old.member == new.member && old.showRemoveAction != new.showRemoveAction) true else null }
    )
) {





    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamMemberViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTeamMemberBinding.inflate(inflater, parent, false)
        return TeamMemberViewHolder(binding, avatarBinder, onMemberClicked, onRemoveMemberClicked)
    }

    override fun onBindViewHolder(holder: TeamMemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: TeamMemberViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(true)) {
            val item = getItem(position)
            holder.bindPayload(item.showRemoveAction, item.isCurrentUser)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }


}

private class TeamMemberViewHolder(
    private val binding: ItemTeamMemberBinding,
    private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
    private val onMemberClicked: (TeamMemberDetails) -> Unit,
    private val onRemoveMemberClicked: (TeamMemberDetails) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(model: TeamMemberUiModel) {
        val member = model.member
        val showRemoveAction = model.showRemoveAction
        val isCurrentUser = model.isCurrentUser
        val username = member.username
        binding.teamMemberAvatar.setImageResource(R.drawable.ic_person_placeholder_24)
        avatarBinder(binding.teamMemberAvatar, username, member.hasAvatar)

        val displayName = member.fullName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: itemView.context.getString(R.string.dashboard_team_members_unknown)
        binding.teamMemberName.text = displayName
        binding.teamMemberUsername.text = username?.takeIf { it.isNotBlank() }?.let {
            itemView.context.getString(
                R.string.dashboard_team_member_profile_username_format,
                it
            )
        } ?: itemView.context.getString(R.string.dashboard_team_members_unknown_username)

        val roleLabel = if (member.isLeader) {
            R.string.dashboard_team_members_leader_role
        } else {
            R.string.dashboard_team_members_member_role
        }
        binding.teamMemberRole.setText(roleLabel)

        val clickListener = View.OnClickListener {
            onMemberClicked(member)
        }
        itemView.setOnClickListener(clickListener)
        binding.teamMemberAvatar.setOnClickListener(clickListener)
        binding.teamMemberName.setOnClickListener(clickListener)
        binding.teamMemberUsername.setOnClickListener(clickListener)
        binding.teamMemberRole.setOnClickListener(clickListener)


        binding.teamMemberRemoveButton.isVisible = showRemoveAction && !isCurrentUser
        binding.teamMemberRemoveButton.setOnClickListener {
            onRemoveMemberClicked(member)
        }
    }

    fun bindPayload(showRemoveAction: Boolean, isCurrentUser: Boolean) {
        binding.teamMemberRemoveButton.isVisible = showRemoveAction && !isCurrentUser
    }
}

private const val INVITE_PAGE_SIZE = 25
private val INVITE_PLACEHOLDER_COLORS = listOf(
    R.color.login_primary,
    R.color.blueOle,
    R.color.greenOleLogo,
)

private data class InviteCandidate(
    val name: String,
    val username: String,
    val planetCode: String?,
    val hasAvatar: Boolean,
    val colorRes: Int,
)

private class InviteMembersAdapter(
    private val avatarLoader: DashboardAvatarLoader?,
    private val onAddClicked: (InviteCandidate) -> Unit
) : ListAdapter<InviteCandidateUiModel, InviteMemberViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old.candidate.username == new.candidate.username },
        areContentsTheSame = { old, new -> old == new },
        getChangePayload = { old, new -> if (old.candidate == new.candidate && old.isDisabled != new.isDisabled) true else null }
    )
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InviteMemberViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemInviteMemberBinding.inflate(inflater, parent, false)
        return InviteMemberViewHolder(binding, avatarLoader, onAddClicked)
    }

    override fun onBindViewHolder(holder: InviteMemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: InviteMemberViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(true)) {
            holder.bindPayload(getItem(position).isDisabled)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
}

private class InviteMemberViewHolder(
    private val binding: ItemInviteMemberBinding,
    private val avatarLoader: DashboardAvatarLoader?,
    private val onAddClicked: (InviteCandidate) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(model: InviteCandidateUiModel) {
        val candidate = model.candidate
        val isDisabled = model.isDisabled
        binding.inviteMemberName.text = candidate.name
        binding.inviteMemberUsername.text = "@${candidate.username}"
        binding.inviteMemberAvatar.setImageDrawable(null)
        binding.inviteMemberAvatar.setImageResource(R.drawable.ic_person_placeholder_24)
        binding.inviteMemberAvatar.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(binding.root.context, candidate.colorRes)
        )
        val shouldAttemptLoad = candidate.hasAvatar || candidate.username.isNotBlank()
        avatarLoader?.bind(binding.inviteMemberAvatar, candidate.username, shouldAttemptLoad)
        binding.inviteMemberAdd.isEnabled = !isDisabled
        binding.inviteMemberAdd.alpha = if (isDisabled) 0.5f else 1f
        binding.inviteMemberAdd.setOnClickListener {
            if (binding.inviteMemberAdd.isEnabled) {
                onAddClicked(candidate)
            }
        }
    }

    fun bindPayload(isDisabled: Boolean) {
        binding.inviteMemberAdd.isEnabled = !isDisabled
        binding.inviteMemberAdd.alpha = if (isDisabled) 0.5f else 1f
    }
}
