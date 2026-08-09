/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-08-09
 */

package org.ole.planet.myplanet.lite

import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.lite.dashboard.CreateVoiceActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardImagePreviewActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamMemberProfileActivity
import java.util.ArrayList

internal class DashboardVoicesNavigator(
    private val fragment: Fragment,
    private val createVoiceLauncher: ActivityResultLauncher<Intent>,
    private val postDetailLauncher: ActivityResultLauncher<Intent>,
) {
    fun openComposer(teamId: String?, teamName: String?) {
        val context = fragment.context ?: return
        val intent = Intent(context, CreateVoiceActivity::class.java)
        addTeamExtras(intent, teamId, teamName)
        createVoiceLauncher.launch(intent)
    }

    fun openImagePreview(item: DashboardNewsItem, index: Int) {
        val context = fragment.context ?: return
        if (item.imagePaths.isEmpty()) return
        val intent = Intent(context, DashboardImagePreviewActivity::class.java)
        intent.putStringArrayListExtra(DashboardImagePreviewActivity.EXTRA_IMAGE_PATHS, ArrayList(item.imagePaths))
        intent.putExtra(DashboardImagePreviewActivity.EXTRA_START_INDEX, index)
        fragment.startActivity(intent)
    }

    fun openPostDetail(item: DashboardNewsItem, teamId: String?, teamName: String?) {
        val context = fragment.context ?: return
        val intent = Intent(context, DashboardPostDetailActivity::class.java)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_POST_ID, item.id)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_AUTHOR, item.author)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_USERNAME, item.username)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_MESSAGE, item.message)
        intent.putStringArrayListExtra(DashboardPostDetailActivity.EXTRA_IMAGE_PATHS, ArrayList(item.imagePaths))
        intent.putExtra(DashboardPostDetailActivity.EXTRA_HAS_AVATAR, item.hasAvatar)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_TIMESTAMP, item.timestamp)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_COMMENT_COUNT, item.commentCount)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_DOCUMENT, item.document)
        teamId?.let { intent.putExtra(DashboardPostDetailActivity.EXTRA_TEAM_ID, it) }
        teamName?.let { intent.putExtra(DashboardPostDetailActivity.EXTRA_TEAM_NAME, it) }
        postDetailLauncher.launch(intent)
    }

    fun openTeamMemberProfile(item: DashboardNewsItem) {
        val username = item.username
        if (username.isNullOrBlank()) {
            Toast.makeText(
                fragment.requireContext(),
                R.string.dashboard_team_members_profile_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val intent =
            DashboardTeamMemberProfileActivity.buildIntent(
                fragment.requireContext(),
                username,
                item.author.ifBlank { username },
                false,
            )
        fragment.startActivity(intent)
    }

    fun openEditVoice(item: DashboardNewsItem, teamId: String?, teamName: String?) {
        val context = fragment.context ?: return
        val intent = Intent(context, CreateVoiceActivity::class.java)
        intent.putExtra(CreateVoiceActivity.EXTRA_IS_EDIT_MODE, true)
        intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_POST_ID, item.id)
        intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_INITIAL_MESSAGE, item.message)
        intent.putStringArrayListExtra(CreateVoiceActivity.EXTRA_EDIT_INITIAL_IMAGE_PATHS, ArrayList(item.imagePaths))
        intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_DOCUMENT, item.document)
        addTeamExtras(intent, teamId, teamName)
        createVoiceLauncher.launch(intent)
    }

    private fun addTeamExtras(intent: Intent, teamId: String?, teamName: String?) {
        teamId?.let { intent.putExtra(CreateVoiceActivity.EXTRA_TARGET_TEAM_ID, it) }
        teamName?.let { intent.putExtra(CreateVoiceActivity.EXTRA_TARGET_TEAM_NAME, it) }
    }
}
