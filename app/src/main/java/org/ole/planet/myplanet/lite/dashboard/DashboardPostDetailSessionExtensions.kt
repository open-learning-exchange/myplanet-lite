/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite.dashboard

import android.content.Intent
import android.app.Activity
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
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.COMMENTS_LIMIT
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository.NewsDocument
import org.ole.planet.myplanet.lite.profile.AvatarUpdateNotifier
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal fun DashboardPostDetailActivity.setupAdapter() {
    adapter =
        PostDetailAdapter(
            markwon,
            avatarBinder = { imageView, user, hasAvatar ->
                val shouldAttemptLoad = hasAvatar || !user.isNullOrBlank()
                avatarLoader?.bind(imageView, user, shouldAttemptLoad)
            },
            imageBinder = { imageView, path ->
                val loader = imageLoader
                if (loader != null) {
                    loader.bind(imageView, path)
                } else {
                    imageView.isVisible = false
                    imageView.setImageDrawable(null)
                }
            },
            onImageClicked = { paths, index ->
                openImagePreview(paths, index)
            },
            onDeleteClicked = {
                attemptDeletePost()
            },
            onShareClicked = {
                shareCurrentPost()
            },
            onEditClicked = { header ->
                launchEditVoice(header)
            },
            onReplyClicked = {
                promptReply()
            },
            onCommentEditClicked = { comment ->
                startEditingComment(comment)
            },
            onCommentDeleteClicked = { comment ->
                attemptDeleteComment(comment)
            },
        )

    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.adapter = adapter
    submitItems(currentComments)
}

internal fun DashboardPostDetailActivity.loadInitialData() {
    lifecycleScope.launch {
        initializeSession()
        val profile = loadCachedProfile()
        currentUsername = profile?.username
        isUserAdmin = profile?.isUserAdmin == true
        refreshHeaderActions()
        val base = baseUrl
        if (base.isNullOrBlank()) {
            Toast
                .makeText(
                    this@loadInitialData,
                    R.string.dashboard_post_detail_comments_error,
                    Toast.LENGTH_SHORT,
                ).show()
            updateItems(emptyList())
            loadingView.isVisible = false
            return@launch
        }
        avatarLoader = DashboardAvatarLoader(base, sessionCookie, credentials, lifecycleScope)
        avatarUpdateListener =
            AvatarUpdateNotifier.register(
                AvatarUpdateNotifier.Listener { username ->
                    handleAvatarUpdated(username)
                },
            )
        imageLoader = DashboardPostImageLoader(base, sessionCookie, lifecycleScope)
        shareHelper =
            PostShareHelper(
                applicationContext,
                { baseUrl },
                { sessionCookie },
                { serverCode ?: baseUrl?.let { Uri.parse(it).host } },
            )
        loadComments(headerItem.id)
    }
}

internal fun DashboardPostDetailActivity.handleAvatarUpdated(username: String) {
    if (!hasAdapter()) {
        return
    }
    val positions =
        adapter.currentList.mapIndexedNotNull { index, item ->
            val itemUsername =
                when (item) {
                    is PostDetailItem.Header -> item.username
                    is PostDetailItem.Comment -> item.username
                }
            if (itemUsername?.equals(username, ignoreCase = true) == true) {
                index
            } else {
                null
            }
        }
    if (positions.isEmpty()) {
        return
    }
    recyclerView.post {
        positions.forEach { position ->
            adapter.notifyItemChanged(position)
        }
    }
}

internal suspend fun DashboardPostDetailActivity.initializeSession() {
    val context = applicationContext
    baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
    val session = ProfileCredentialsStore.getCurrentUserSession(context)
    credentials = session?.credentials
    cachedProfile = session?.profile
    serverCode = DashboardServerPreferences.getServerCode(context)
    serverParentCode = DashboardServerPreferences.getServerParentCode(context)
    baseUrl?.let { base ->
        val authService = AuthDependencies.provideAuthService(context, base)
        sessionCookie = authService.getStoredToken()
    }
}

internal suspend fun DashboardPostDetailActivity.loadComments(postId: String) {
    val base = baseUrl ?: return
    loadingView.isVisible = true
    val result =
        repository.fetchComments(
            base,
            sessionCookie,
            postId,
            COMMENTS_LIMIT,
            serverCode,
            serverParentCode,
            selectedTeamName,
        )
    result
        .onSuccess { docs ->
            val sorted = docs.sortedBy { it.time ?: 0L }
            val mapped = sorted.mapNotNull { mapToCommentItem(it) }
            updateItems(mapped)
        }.onFailure {
            Toast
                .makeText(
                    this,
                    R.string.dashboard_post_detail_comments_error,
                    Toast.LENGTH_SHORT,
                ).show()
            updateItems(emptyList())
        }
    loadingView.isVisible = false
}

internal fun DashboardPostDetailActivity.updateItems(comments: List<PostDetailItem.Comment>) {
    headerItem =
        headerItem.copy(
            commentCount = comments.size,
            isLoadingComments = false,
        )
    currentComments = comments
    submitItems(currentComments)
}

internal fun DashboardPostDetailActivity.refreshHeaderActions() {
    val username = headerItem.username
    val hasSession = !sessionCookie.isNullOrBlank()
    val isAuthor = hasSession && !username.isNullOrBlank() && currentUsername?.equals(username, ignoreCase = true) == true
    val canShare = hasSession
    val canDelete = (isAuthor || isUserAdmin) && document != null
    headerItem =
        headerItem.copy(
            canReply = hasSession,
            canEdit = isAuthor,
            canDelete = canDelete,
            canShare = canShare,
        )
    updateReplyComposerVisibility()
    submitItems(currentComments)
}

internal fun DashboardPostDetailActivity.shareCurrentPost() {
    val helper = shareHelper ?: return
    val header = headerItem
    lifecycleScope.launch {
        helper.sharePost(header.id, header.author, header.message, header.imagePaths)
    }
}

internal fun DashboardPostDetailActivity.attemptDeletePost() {
    val base = baseUrl
    val doc = document
    if (base.isNullOrBlank() || doc == null) {
        Toast.makeText(this, R.string.dashboard_post_delete_error, Toast.LENGTH_SHORT).show()
        return
    }
    val cookie = sessionCookie
    if (cookie.isNullOrBlank()) {
        Toast.makeText(this, R.string.dashboard_post_delete_error, Toast.LENGTH_SHORT).show()
        return
    }
    lifecycleScope.launch {
        val result =
            actionsRepository.deleteNews(
                base,
                cookie,
                doc,
                teamId = selectedTeamId,
                teamName = selectedTeamName,
            )
        result
            .onSuccess {
                Toast
                    .makeText(this@attemptDeletePost, R.string.dashboard_post_delete_success, Toast.LENGTH_SHORT)
                    .show()
                val deletedIntent = Intent().putExtra(DashboardPostDetailActivity.EXTRA_DELETED_POST_ID, doc.id)
                setResult(Activity.RESULT_OK, deletedIntent)
                finish()
            }.onFailure {
                Toast
                    .makeText(this@attemptDeletePost, R.string.dashboard_post_delete_error, Toast.LENGTH_SHORT)
                    .show()
            }
    }
}

internal fun DashboardPostDetailActivity.attemptDeleteComment(comment: PostDetailItem.Comment) {
    val base = baseUrl
    val doc = comment.document
    if (base.isNullOrBlank() || doc == null) {
        Toast.makeText(this, R.string.dashboard_comment_delete_error, Toast.LENGTH_SHORT).show()
        return
    }
    val cookie = sessionCookie
    if (cookie.isNullOrBlank()) {
        Toast.makeText(this, R.string.dashboard_comment_delete_error, Toast.LENGTH_SHORT).show()
        return
    }
    lifecycleScope.launch {
        val result =
            actionsRepository.deleteNews(
                base,
                cookie,
                doc,
                teamId = selectedTeamId,
                teamName = selectedTeamName,
            )
        result
            .onSuccess {
                Toast
                    .makeText(this@attemptDeleteComment, R.string.dashboard_comment_delete_success, Toast.LENGTH_SHORT)
                    .show()
                currentComments = currentComments.filterNot { it.id == comment.id }
                updateItems(currentComments)
            }.onFailure {
                Toast
                    .makeText(this@attemptDeleteComment, R.string.dashboard_comment_delete_error, Toast.LENGTH_SHORT)
                    .show()
            }
    }
}

internal suspend fun DashboardPostDetailActivity.loadCachedProfile(): UserProfile? {
    val existing = cachedProfile
    if (existing != null) {
        return existing
    }
    val profile =
        withContext(Dispatchers.IO) {
            UserProfileDatabase.getInstance(applicationContext).getProfile()
    }
    cachedProfile = profile
    return profile
}

internal suspend fun DashboardPostDetailActivity.buildUserPayload(credentials: StoredCredentials): VoicesComposerRepository.UserPayload {
    val profile = loadCachedProfile()
    val planetCode = serverCode?.takeIf { it.isNotBlank() } ?: document?.createdOn
    return VoicesComposerRepository.UserPayload(
        id = "org.couchdb.user:${credentials.username}",
        name = profile?.username ?: credentials.username,
        firstName = profile?.firstName,
        middleName = profile?.middleName,
        lastName = profile?.lastName,
        email = profile?.email,
        language = profile?.language,
        phoneNumber = profile?.phoneNumber,
        planetCode = planetCode,
        parentCode = document?.parentCode,
        roles = null,
        joinDate = null,
        attachments = null,
    )
}

internal fun DashboardPostDetailActivity.parseCodesFromProfile(rawDocument: String?): ProfileCodes? {
    if (rawDocument.isNullOrBlank()) {
        return null
    }
    return runCatching {
        val json = org.json.JSONObject(rawDocument)
        val planetCode = json.optString("planetCode").takeIf { it.isNotBlank() }
        val parentCode = json.optString("parentCode").takeIf { it.isNotBlank() }
        ProfileCodes(planetCode, parentCode)
    }.getOrNull()
}
