/*
Author: Walfre López Prado
Email: loppra@plataformasinformaticas.com
Creation date: 2026-08-09
 */

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.ole.planet.myplanet.lite.dashboard.DashboardAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.dashboard.AddTeamMemberRequest
import org.ole.planet.myplanet.lite.dashboard.JoinRequestDocument
import org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails
import org.ole.planet.myplanet.lite.dashboard.UserDocument
import org.ole.planet.myplanet.lite.databinding.DialogInviteMembersBinding
import org.ole.planet.myplanet.lite.databinding.ItemInviteMemberBinding
import org.ole.planet.myplanet.lite.databinding.ItemTeamJoinRequestBinding
import org.ole.planet.myplanet.lite.databinding.ItemTeamMemberBinding
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal data class TeamMemberUiModel(val member: TeamMemberDetails, val showRemoveAction: Boolean, val currentUsername: String?)
internal data class TeamJoinRequestUiModel(val id: String, val username: String, val fullName: String, val hasAvatar: Boolean, val request: JoinRequestDocument)

internal const val INVITE_PAGE_SIZE = 25
internal val INVITE_PLACEHOLDER_COLORS = listOf(R.color.login_primary, R.color.blueOle, R.color.greenOleLogo)

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
