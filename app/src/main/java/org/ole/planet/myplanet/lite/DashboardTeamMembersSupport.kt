package org.ole.planet.myplanet.lite

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.dashboard.DashboardAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.dashboard.FetchAllUsersRequest
import org.ole.planet.myplanet.lite.dashboard.JoinRequestDocument
import org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails
import org.ole.planet.myplanet.lite.dashboard.UserDocument
import org.ole.planet.myplanet.lite.databinding.DialogInviteMembersBinding
import org.ole.planet.myplanet.lite.databinding.ItemInviteMemberBinding
import org.ole.planet.myplanet.lite.databinding.ItemTeamJoinRequestBinding
import org.ole.planet.myplanet.lite.databinding.ItemTeamMemberBinding
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal fun confirmAcceptJoinRequestDialog(fragment: Fragment, request: TeamJoinRequestUiModel, onAccept: (TeamJoinRequestUiModel) -> Unit) {
    val displayName = request.fullName.ifBlank { request.username }
    AlertDialog.Builder(fragment.requireContext())
        .setTitle(R.string.dashboard_team_members_request_accept_confirm_title)
        .setMessage(fragment.getString(R.string.dashboard_team_members_request_accept_confirm_message, displayName))
        .setNegativeButton(R.string.dashboard_team_members_remove_cancel, null)
        .setPositiveButton(R.string.dashboard_team_members_request_accept_confirm_action) { _, _ ->
            onAccept(request)
        }
        .show()
}

internal data class DashboardTeamActionContext(
    val fragment: Fragment,
    val repository: DashboardTeamsRepository,
    val baseUrl: String?,
    val credentials: StoredCredentials?,
    val sessionCookie: String?
)

internal fun confirmRejectJoinRequestDialog(fragment: Fragment, request: TeamJoinRequestUiModel, onReject: (TeamJoinRequestUiModel) -> Unit) {
    val displayName = request.fullName.ifBlank { request.username }
    AlertDialog.Builder(fragment.requireContext())
        .setTitle(R.string.dashboard_team_members_request_reject_confirm_title)
        .setMessage(fragment.getString(R.string.dashboard_team_members_request_reject_confirm_message, displayName))
        .setNegativeButton(R.string.dashboard_team_members_remove_cancel, null)
        .setPositiveButton(R.string.dashboard_team_members_request_reject_confirm_action) { _, _ ->
            onReject(request)
        }
        .show()
}

internal fun confirmMemberRemovalDialog(fragment: Fragment, member: TeamMemberDetails, onRemove: (TeamMemberDetails, String) -> Unit) {
    val displayName = member.fullName?.takeIf { it.isNotBlank() }
        ?: member.username?.takeIf { it.isNotBlank() }
        ?: fragment.getString(R.string.dashboard_team_members_unknown)
    AlertDialog.Builder(fragment.requireContext())
        .setTitle(R.string.dashboard_team_members_remove_confirmation_title)
        .setMessage(fragment.getString(R.string.dashboard_team_members_remove_confirmation_message, displayName))
        .setNegativeButton(R.string.dashboard_team_members_remove_cancel, null)
        .setPositiveButton(R.string.dashboard_team_members_remove_action) { _, _ ->
            onRemove(member, displayName)
        }
        .show()
}

internal data class AcceptJoinRequestParams(
    val fragment: Fragment,
    val repository: DashboardTeamsRepository,
    val baseUrl: String?,
    val credentials: StoredCredentials?,
    val sessionCookie: String?,
    val request: TeamJoinRequestUiModel,
    val currentTeamPlanetCode: String?,
    val serverPlanetCode: String?,
    val onReload: () -> Unit,
)

internal fun runAcceptJoinRequest(params: AcceptJoinRequestParams) {
    val base = params.baseUrl
    val creds = params.credentials
    val teamId = params.request.request.teamId
    val userId = params.request.request.userId
    val teamPlanetCode =
        params.request.request.teamPlanetCode
            ?: params.currentTeamPlanetCode
            ?: params.serverPlanetCode
    val userPlanetCode =
        params.request.request.userPlanetCode
            ?: params.serverPlanetCode
    val requestId = params.request.request.id
    val requestRevision = params.request.request.revision

    if (
        base.isNullOrBlank() || creds == null ||
        teamId.isNullOrBlank() || userId.isNullOrBlank() ||
        teamPlanetCode.isNullOrBlank() || userPlanetCode.isNullOrBlank() ||
        requestId.isNullOrBlank() || requestRevision.isNullOrBlank()
    ) {
        Toast.makeText(
            params.fragment.requireContext(),
            params.fragment.getString(R.string.dashboard_invite_members_add_error_generic),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }

    params.fragment.viewLifecycleOwner.lifecycleScope.launch {
        val addResult = params.repository.addTeamMember(
            baseUrl = base,
            credentials = creds,
            sessionCookie = params.sessionCookie,
            teamId = teamId,
            teamPlanetCode = teamPlanetCode,
            teamType = params.request.request.teamType ?: "local",
            userId = userId,
            userPlanetCode = userPlanetCode,
        )

        addResult.onSuccess {
            params.repository.cancelJoinRequest(
                baseUrl = base,
                credentials = creds,
                sessionCookie = params.sessionCookie,
                documentId = requestId,
                revision = requestRevision,
            )
            params.onReload()
        }.onFailure {
            Toast.makeText(
                params.fragment.requireContext(),
                params.fragment.getString(
                    R.string.dashboard_invite_members_add_error,
                    params.request.fullName,
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

internal fun runRejectJoinRequest(
    context: DashboardTeamActionContext,
    request: TeamJoinRequestUiModel,
    onReload: () -> Unit,
) {
    val base = context.baseUrl
    val creds = context.credentials
    val requestId = request.request.id
    val requestRevision = request.request.revision
    if (base.isNullOrBlank() || creds == null || requestId.isNullOrBlank() || requestRevision.isNullOrBlank()) {
        Toast.makeText(context.fragment.requireContext(), context.fragment.getString(R.string.dashboard_invite_members_add_error_generic), Toast.LENGTH_SHORT).show()
        return
    }

    context.fragment.viewLifecycleOwner.lifecycleScope.launch {
        val result = context.repository.cancelJoinRequest(base, creds, context.sessionCookie, requestId, requestRevision)
        result.onSuccess { onReload() }
            .onFailure {
                Toast.makeText(context.fragment.requireContext(), context.fragment.getString(R.string.dashboard_invite_members_add_error_generic), Toast.LENGTH_SHORT).show()
            }
    }
}

internal fun runRemoveTeamMember(
    context: DashboardTeamActionContext,
    teamId: String?,
    member: TeamMemberDetails,
    displayName: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReload: () -> Unit,
) {
    val membership = member.membership
    if (teamId.isNullOrBlank() || context.baseUrl.isNullOrBlank() || context.credentials == null || membership == null) {
        Toast.makeText(context.fragment.requireContext(), context.fragment.getString(R.string.dashboard_team_members_remove_error, displayName), Toast.LENGTH_SHORT).show()
        return
    }

    onStart()
    context.fragment.viewLifecycleOwner.lifecycleScope.launch {
        val result = context.repository.removeTeamMember(context.baseUrl, context.credentials, context.sessionCookie, membership)
        result.onSuccess {
            Toast.makeText(context.fragment.requireContext(), context.fragment.getString(R.string.dashboard_team_members_remove_success, displayName), Toast.LENGTH_SHORT).show()
            onReload()
        }.onFailure {
            Toast.makeText(context.fragment.requireContext(), context.fragment.getString(R.string.dashboard_team_members_remove_error, displayName), Toast.LENGTH_SHORT).show()
            onStop()
        }
    }
}

internal fun animateFabClickSupport(fab: FloatingActionButton) {
    fab.animate().rotationBy(360f).setDuration(250).withEndAction { fab.rotation = 0f }.start()
}

internal class DashboardTeamMembersInviteDialogController(
    private val fragment: Fragment,
    private val dialogBinding: DialogInviteMembersBinding,
    private val repository: DashboardTeamsRepository,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val avatarLoader: DashboardAvatarLoader?,
    private val base: String,
    private val creds: StoredCredentials,
    private val teamId: String,
    private val teamPlanetCode: String,
    private val teamType: String,
    private val sessionCookie: String?,
    private val serverPlanetCode: String?,
    private val serverParentCode: String?,
    private val currentMembersProvider: () -> List<TeamMemberDetails>,
    private val onReload: () -> Unit,
) {
    private var isLoadingPage = false
    private var hasMorePages = true
    private var nextSkip = 0
    private var currentSearchTerm = ""
    private var pendingReset = false
    private var pagingDialog: AlertDialog? = null
    private lateinit var inviteAdapter: InviteMembersAdapter
    private val currentCandidates = mutableListOf<InviteCandidate>()
    private val disabledCandidates = mutableSetOf<String>()

    fun show() {
        inviteAdapter = InviteMembersAdapter(avatarLoader) { candidate -> addMember(candidate) }
        val layoutManager = LinearLayoutManager(fragment.requireContext())
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

        val dialog = AlertDialog.Builder(fragment.requireContext()).setView(dialogBinding.root).create()
        dialogBinding.inviteMembersCancel.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            pagingDialog?.dismiss()
            pagingDialog = null
            onReload()
        }
        dialog.show()
        loadInvitePage(reset = true)
    }

    private fun submitCandidates() {
        inviteAdapter.submitList(currentCandidates.map { InviteCandidateUiModel(it, disabledCandidates.contains(it.username)) })
    }

    private fun addMember(candidate: InviteCandidate) {
        val userPlanet = candidate.planetCode ?: serverPlanetCode
        if (userPlanet.isNullOrBlank()) {
            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.dashboard_invite_members_add_error_generic), Toast.LENGTH_SHORT).show()
            return
        }
        disabledCandidates.add(candidate.username)
        submitCandidates()
        lifecycleScope.launch {
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
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.dashboard_invite_members_add_success, candidate.name), Toast.LENGTH_SHORT).show()
            }.onFailure {
                disabledCandidates.remove(candidate.username)
                submitCandidates()
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.dashboard_invite_members_add_error, candidate.name), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadInvitePage(reset: Boolean = false) {
        if (!prepareForLoad(reset)) return
        lifecycleScope.launch {
            val result = repository.fetchAllUsers(
                FetchAllUsersRequest(
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
            )
            result.onSuccess { users -> handleFetchSuccess(users, reset) }
                .onFailure {
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.dashboard_invite_members_load_error), Toast.LENGTH_SHORT).show()
                }
            finishLoad()
        }
    }

    private fun prepareForLoad(reset: Boolean): Boolean {
        if (isLoadingPage) {
            if (reset) pendingReset = true
            return false
        }
        if (!hasMorePages && !reset) return false
        isLoadingPage = true
        if (reset) {
            nextSkip = 0
            hasMorePages = true
            pendingReset = false
            currentCandidates.clear()
            disabledCandidates.clear()
            submitCandidates()
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
        return currentMembersProvider().mapNotNull { member ->
            val userId = member.membership?.userId
            when {
                userId.isNullOrBlank() -> null
                userId.startsWith("org.couchdb.user:") -> userId
                else -> "org.couchdb.user:$userId"
            }
        }
    }

    private fun handleFetchSuccess(users: List<UserDocument>?, reset: Boolean) {
        val candidates = users.orEmpty().mapNotNull { toInviteCandidate(it) }
        if (reset) {
            currentCandidates.clear()
            disabledCandidates.clear()
            currentCandidates.addAll(candidates)
        } else {
            currentCandidates.addAll(candidates)
        }
        submitCandidates()
        if (candidates.size < INVITE_PAGE_SIZE) hasMorePages = false else nextSkip += INVITE_PAGE_SIZE
    }

    private fun finishLoad() {
        dialogBinding.inviteMembersLoading.isVisible = false
        dialogBinding.inviteMembersList.isVisible = true
        isLoadingPage = false
        hidePagingDialog()
        if (pendingReset) {
            pendingReset = false
            loadInvitePage(reset = true)
        }
    }

    private fun showPagingDialog() {
        if (pagingDialog == null) {
            val progressBar = ProgressBar(fragment.requireContext())
            val padding = fragment.resources.getDimensionPixelSize(R.dimen.padding_small)
            progressBar.setPadding(padding, padding, padding, padding)
            pagingDialog = AlertDialog.Builder(fragment.requireContext())
                .setView(progressBar)
                .setCancelable(false)
                .create().apply { window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }
        }
        pagingDialog?.show()
    }

    private fun hidePagingDialog() { pagingDialog?.dismiss() }

    private fun toInviteCandidate(user: UserDocument): InviteCandidate? {
        val username = user._id?.substringAfter("org.couchdb.user:")?.takeIf { it.isNotBlank() } ?: return null
        val planetMatches = serverPlanetCode.isNullOrBlank() || user.planetCode.isNullOrBlank() || serverPlanetCode.equals(user.planetCode, ignoreCase = true)
        val parentMatches = serverParentCode.isNullOrBlank() || user.parentCode.isNullOrBlank() || serverParentCode.equals(user.parentCode, ignoreCase = true)
        if (!planetMatches || !parentMatches) return null

        val nameParts = listOfNotNull(user.firstName, user.lastName).filter { it.isNotBlank() }
        val displayName = nameParts.joinToString(" ").takeIf { it.isNotBlank() } ?: user.email?.takeIf { it.isNotBlank() } ?: username
        val colorIndex = abs(username.hashCode()) % INVITE_PLACEHOLDER_COLORS.size
        return InviteCandidate(displayName, username, user.planetCode, user.attachments?.image != null, INVITE_PLACEHOLDER_COLORS[colorIndex])
    }
}

internal data class TeamMemberUiModel(val member: TeamMemberDetails, val showRemoveAction: Boolean, val currentUsername: String?)
internal data class TeamJoinRequestUiModel(val id: String, val username: String, val fullName: String, val hasAvatar: Boolean, val request: JoinRequestDocument)

private const val INVITE_PAGE_SIZE = 25
private val INVITE_PLACEHOLDER_COLORS = listOf(R.color.login_primary, R.color.blueOle, R.color.greenOleLogo)

internal data class InviteCandidate(val name: String, val username: String, val planetCode: String?, val hasAvatar: Boolean, val colorRes: Int)
internal data class InviteCandidateUiModel(val candidate: InviteCandidate, val isDisabled: Boolean)

internal class TeamMembersAdapter(
    private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
    private val onMemberClicked: (TeamMemberDetails) -> Unit,
    private val onRemoveMemberClicked: (TeamMemberDetails) -> Unit,
) : ListAdapter<TeamMemberUiModel, TeamMemberViewHolder>(
    org.ole.planet.myplanet.lite.util.DiffUtils.itemCallback({ oldItem, newItem -> oldItem.member.username == newItem.member.username }),
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamMemberViewHolder {
        return TeamMemberViewHolder(ItemTeamMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false), avatarBinder, onMemberClicked, onRemoveMemberClicked)
    }

    override fun onBindViewHolder(holder: TeamMemberViewHolder, position: Int) {
        val uiModel = getItem(position)
        holder.bind(uiModel.member, uiModel.showRemoveAction, uiModel.currentUsername)
    }
}

internal class TeamJoinRequestsAdapter(
    private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
    private val onAcceptClicked: (TeamJoinRequestUiModel) -> Unit,
    private val onRejectClicked: (TeamJoinRequestUiModel) -> Unit,
) : ListAdapter<TeamJoinRequestUiModel, TeamJoinRequestViewHolder>(
    org.ole.planet.myplanet.lite.util.DiffUtils.itemCallback({ oldItem, newItem -> oldItem.id == newItem.id }),
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamJoinRequestViewHolder {
        return TeamJoinRequestViewHolder(ItemTeamJoinRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false), avatarBinder, onAcceptClicked, onRejectClicked)
    }

    override fun onBindViewHolder(holder: TeamJoinRequestViewHolder, position: Int) { holder.bind(getItem(position)) }
}

internal class TeamJoinRequestViewHolder(
    private val binding: ItemTeamJoinRequestBinding,
    private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
    private val onAcceptClicked: (TeamJoinRequestUiModel) -> Unit,
    private val onRejectClicked: (TeamJoinRequestUiModel) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: TeamJoinRequestUiModel) {
        binding.teamJoinRequestAvatar.setImageResource(R.drawable.ic_person_placeholder_24)
        avatarBinder(binding.teamJoinRequestAvatar, item.username, item.hasAvatar)
        binding.teamJoinRequestName.text = item.fullName
        binding.teamJoinRequestUsername.text = itemView.context.getString(R.string.dashboard_team_member_profile_username_format, item.username)
        binding.teamJoinRequestRole.setText(R.string.dashboard_team_members_request_role)
        binding.teamJoinRequestAcceptButton.setOnClickListener { onAcceptClicked(item) }
        binding.teamJoinRequestRejectButton.setOnClickListener { onRejectClicked(item) }
    }
}

internal class TeamMemberViewHolder(
    private val binding: ItemTeamMemberBinding,
    private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
    private val onMemberClicked: (TeamMemberDetails) -> Unit,
    private val onRemoveMemberClicked: (TeamMemberDetails) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(member: TeamMemberDetails, showRemoveAction: Boolean, currentUsername: String?) {
        val username = member.username
        binding.teamMemberAvatar.setImageResource(R.drawable.ic_person_placeholder_24)
        avatarBinder(binding.teamMemberAvatar, username, member.hasAvatar)
        val displayName = member.fullName?.takeIf { it.isNotBlank() } ?: username?.takeIf { it.isNotBlank() } ?: itemView.context.getString(R.string.dashboard_team_members_unknown)
        binding.teamMemberName.text = displayName
        binding.teamMemberUsername.text = username?.takeIf { it.isNotBlank() }?.let {
            itemView.context.getString(R.string.dashboard_team_member_profile_username_format, it)
        } ?: itemView.context.getString(R.string.dashboard_team_members_unknown_username)
        binding.teamMemberRole.setText(if (member.isLeader) R.string.dashboard_team_members_leader_role else R.string.dashboard_team_members_member_role)
        val clickListener = View.OnClickListener { onMemberClicked(member) }
        itemView.setOnClickListener(clickListener)
        binding.teamMemberAvatar.setOnClickListener(clickListener)
        binding.teamMemberName.setOnClickListener(clickListener)
        binding.teamMemberUsername.setOnClickListener(clickListener)
        binding.teamMemberRole.setOnClickListener(clickListener)
        val isCurrentUser = currentUsername != null && username.equals(currentUsername, ignoreCase = true)
        binding.teamMemberRemoveButton.isVisible = showRemoveAction && !isCurrentUser
        binding.teamMemberRemoveButton.setOnClickListener { onRemoveMemberClicked(member) }
    }
}

internal class InviteMembersAdapter(
    private val avatarLoader: DashboardAvatarLoader?,
    private val onAddClicked: (InviteCandidate) -> Unit,
) : ListAdapter<InviteCandidateUiModel, InviteMemberViewHolder>(
    org.ole.planet.myplanet.lite.util.DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.candidate.username == newItem.candidate.username },
        getChangePayload = { oldItem, newItem -> if (oldItem.isDisabled != newItem.isDisabled) true else null },
    ),
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InviteMemberViewHolder {
        return InviteMemberViewHolder(ItemInviteMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false), avatarLoader, onAddClicked)
    }

    override fun onBindViewHolder(holder: InviteMemberViewHolder, position: Int) {
        val uiModel = getItem(position)
        holder.bind(uiModel.candidate, uiModel.isDisabled)
    }

    override fun onBindViewHolder(holder: InviteMemberViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            holder.bindPayload(getItem(position).isDisabled)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
}

internal class InviteMemberViewHolder(
    private val binding: ItemInviteMemberBinding,
    private val avatarLoader: DashboardAvatarLoader?,
    private val onAddClicked: (InviteCandidate) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(candidate: InviteCandidate, isDisabled: Boolean) {
        binding.inviteMemberName.text = candidate.name
        binding.inviteMemberUsername.text = "@${candidate.username}"
        binding.inviteMemberAvatar.setImageDrawable(null)
        binding.inviteMemberAvatar.setImageResource(R.drawable.ic_person_placeholder_24)
        binding.inviteMemberAvatar.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, candidate.colorRes))
        val shouldAttemptLoad = candidate.hasAvatar || candidate.username.isNotBlank()
        avatarLoader?.bind(binding.inviteMemberAvatar, candidate.username, shouldAttemptLoad)
        bindPayload(isDisabled)
        binding.inviteMemberAdd.setOnClickListener { if (binding.inviteMemberAdd.isEnabled) onAddClicked(candidate) }
    }

    fun bindPayload(isDisabled: Boolean) {
        binding.inviteMemberAdd.isEnabled = !isDisabled
        binding.inviteMemberAdd.alpha = if (isDisabled) 0.5f else 1f
    }
}
