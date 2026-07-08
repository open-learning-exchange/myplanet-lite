/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-08
 */

package org.ole.planet.myplanet.lite.dashboard

import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.IMAGE_MARKDOWN_REGEX
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.RESOURCES_PATH_REGEX
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository.NewsDocument
import org.ole.planet.myplanet.lite.profile.AvatarUpdateNotifier
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal fun DashboardPostDetailActivity.submitItems(comments: List<PostDetailItem.Comment>) {
    val newItems = ArrayList<PostDetailItem>(1 + comments.size)
    newItems.add(headerItem)
    newItems.addAll(comments)
    adapter.submitList(newItems)
}

internal fun DashboardPostDetailActivity.mapToCommentItem(document: NewsDocument): PostDetailItem.Comment? {
    val id = document.id ?: return null
    val username = document.user?.name?.takeIf { it.isNotBlank() }
    val displayName =
        document.user?.let { user ->
            val parts =
                listOfNotNull(user.firstName, user.middleName, user.lastName)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            when {
                parts.isNotEmpty() -> TextUtils.join(" ", parts)
                !username.isNullOrEmpty() -> username
                else -> getString(R.string.dashboard_profile_name_placeholder)
            }
        } ?: (username ?: getString(R.string.dashboard_profile_name_placeholder))
    val message = document.message?.takeUnless { it.isNullOrBlank() }
    val imagePaths = mapCommentImages(document)
    val hasAvatar = document.user?.attachments?.isNullOrEmpty() == false
    val timestamp = document.time ?: 0L
    val hasSession = !sessionCookie.isNullOrBlank()
    val isAuthor = hasSession && !username.isNullOrBlank() && currentUsername?.equals(username, ignoreCase = true) == true
    val canDelete = hasSession && (isAuthor || isUserAdmin)
    return PostDetailItem.Comment(
        id = id,
        author = displayName,
        username = username,
        hasAvatar = hasAvatar,
        message = message,
        imagePaths = imagePaths,
        timestamp = timestamp,
        canEdit = isAuthor,
        canDelete = canDelete,
        document = document,
    )
}

internal fun DashboardPostDetailActivity.mapCommentImages(document: NewsDocument): List<String> {
    val fromImages =
        document.images
            ?.mapNotNull { image ->
                extractImagePath(image.markdown)
                    ?: buildResourcePath(image.resourceId, image.filename)
            }?.filter { it.isNotBlank() }
            .orEmpty()
    val fromMessage = collectImagePaths(document.message)
    return mergeImagePaths(fromImages + fromMessage)
}

internal fun DashboardPostDetailActivity.collectImagePaths(markdown: String?): List<String> {
    if (markdown.isNullOrBlank()) {
        return emptyList()
    }
    return IMAGE_MARKDOWN_REGEX
        .findAll(markdown)
        .mapNotNull { match ->
            match.groupValues
                .getOrNull(2)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.toList()
}

internal fun DashboardPostDetailActivity.mergeImagePaths(paths: List<String>): List<String> {
    if (paths.isEmpty()) {
        return emptyList()
    }
    val seen = LinkedHashSet<String>()
    val merged = mutableListOf<String>()
    for (path in paths) {
        val normalized = normalizeImagePath(path)
        if (seen.add(normalized)) {
            merged += path
        }
    }
    return merged
}

internal fun DashboardPostDetailActivity.normalizeImagePath(path: String): String {
    val extracted = extractImagePath(path) ?: path
    val trimmed = extracted.trim()
    val resourcesMatch = RESOURCES_PATH_REGEX.find(trimmed)
    val reduced =
        if (resourcesMatch != null) {
            resourcesMatch.value
        } else {
            trimmed
                .trimStart('/')
                .removePrefix("db/")
                .trimStart('/')
        }
    return reduced.lowercase(Locale.US)
}

internal fun DashboardPostDetailActivity.extractImagePath(markdown: String?): String? {
    if (markdown.isNullOrBlank()) {
        return null
    }
    val match = IMAGE_MARKDOWN_REGEX.find(markdown)
    return match
        ?.groupValues
        ?.getOrNull(2)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

internal fun DashboardPostDetailActivity.buildResourcePath(
    resourceId: String?,
    filename: String?,
): String? {
    val id = resourceId?.trim().takeUnless { it.isNullOrEmpty() }
    val name = filename?.trim().takeUnless { it.isNullOrEmpty() }
    if (id == null || name == null) {
        return null
    }
    return "resources/$id/$name"
}

internal fun DashboardPostDetailActivity.openImagePreview(
    imagePaths: List<String>,
    startIndex: Int,
) {
    if (imagePaths.isEmpty()) {
        return
    }
    val intent = Intent(this, DashboardImagePreviewActivity::class.java)
    intent.putStringArrayListExtra(
        DashboardImagePreviewActivity.EXTRA_IMAGE_PATHS,
        ArrayList(imagePaths),
    )
    intent.putExtra(DashboardImagePreviewActivity.EXTRA_START_INDEX, startIndex)
    startActivity(intent)
}

internal fun DashboardPostDetailActivity.launchEditVoice(item: PostDetailItem.Header) {
    val intent = Intent(this, CreateVoiceActivity::class.java)
    intent.putExtra(CreateVoiceActivity.EXTRA_IS_EDIT_MODE, true)
    intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_POST_ID, item.id)
    intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_INITIAL_MESSAGE, item.message)
    intent.putStringArrayListExtra(
        CreateVoiceActivity.EXTRA_EDIT_INITIAL_IMAGE_PATHS,
        ArrayList(item.imagePaths),
    )
    document?.let { intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_DOCUMENT, it) }
    selectedTeamId?.let { intent.putExtra(CreateVoiceActivity.EXTRA_TARGET_TEAM_ID, it) }
    selectedTeamName?.let { intent.putExtra(CreateVoiceActivity.EXTRA_TARGET_TEAM_NAME, it) }
    editVoiceLauncher.launch(intent)
}
