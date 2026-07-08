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
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.KEY_DEVICE_ANDROID_ID
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.KEY_DEVICE_CUSTOM_DEVICE_NAME
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.KEY_SERVER_CODE
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.KEY_SERVER_PARENT_CODE
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

internal fun DashboardPostDetailActivity.attemptReply(message: String) {
    if (message.isBlank() && pendingReplyImages.isEmpty()) {
        Toast.makeText(this, R.string.dashboard_post_reply_empty, Toast.LENGTH_SHORT).show()
        return
    }
    val base = baseUrl
    val postId = headerItem.id
    val doc = document
    if (base.isNullOrBlank() || doc == null) {
        Toast.makeText(this, R.string.dashboard_post_detail_comments_error, Toast.LENGTH_SHORT).show()
        return
    }
    val credentials = this.credentials
    if (credentials == null) {
        Toast.makeText(this, R.string.dashboard_post_reply_missing_credentials, Toast.LENGTH_SHORT).show()
        return
    }
    val cookie = sessionCookie
    if (cookie.isNullOrBlank()) {
        Toast.makeText(this, R.string.dashboard_post_detail_comments_error, Toast.LENGTH_SHORT).show()
        return
    }
    if (isPostingReply) {
        return
    }
    setReplyPosting(true)
    lifecycleScope.launch {
        val prepared = prepareReplyImagesForPosting(base, credentials, message)
        val userPayload = buildUserPayload(credentials)
        val result =
            composerRepository.createVoice(
                VoicesComposerRepository.CreateVoiceParams(
                    baseUrl = base,
                    credentials = credentials,
                    sessionCookie = cookie,
                    message = prepared.message,
                    createdOn = doc.createdOn ?: serverCode,
                    parentCode = doc.parentCode,
                    replyTo = postId,
                    images = prepared.images,
                    labels = emptyList(),
                    userPayload = userPayload,
                    teamId = selectedTeamId,
                    teamName = selectedTeamName,
                ),
            )
        result
            .onSuccess {
                    Toast.makeText(this@attemptReply, R.string.dashboard_post_reply_success, Toast.LENGTH_SHORT).show()
                replyInput.setText("")
                clearPendingReplyImages()
                updateReplyPreview(replyPreview, "")
                hideReplyKeyboard()
                collapseReplyComposerIfExpanded()
                loadComments(postId)
            }.onFailure {
                    Toast.makeText(this@attemptReply, R.string.dashboard_post_reply_error, Toast.LENGTH_SHORT).show()
            }
        setReplyPosting(false)
    }
}

internal fun DashboardPostDetailActivity.attemptUpdateComment(message: String) {
    if (message.isBlank() && pendingReplyImages.isEmpty()) {
        Toast.makeText(this, R.string.dashboard_post_reply_empty, Toast.LENGTH_SHORT).show()
        return
    }
    val base = baseUrl
    val doc = editingCommentDocument
    if (base.isNullOrBlank() || doc == null) {
        Toast.makeText(this, R.string.dashboard_comment_edit_error, Toast.LENGTH_SHORT).show()
        exitCommentEditMode(clearFields = true)
        return
    }
    val credentials = this.credentials
    if (credentials == null) {
        Toast.makeText(this, R.string.dashboard_post_reply_missing_credentials, Toast.LENGTH_SHORT).show()
        return
    }
    val cookie = sessionCookie
    if (cookie.isNullOrBlank()) {
        Toast.makeText(this, R.string.dashboard_comment_edit_error, Toast.LENGTH_SHORT).show()
        return
    }
    if (isPostingReply) {
        return
    }
    setReplyPosting(true)
    lifecycleScope.launch {
        val prepared = prepareReplyImagesForPosting(base, credentials, message)
        val newImages =
            prepared.images.map { image ->
                DashboardNewsRepository.NewsImage(
                    resourceId = image.resourceId,
                    filename = image.filename,
                    markdown = image.markdown,
                )
            }
        val mergedImages = mergeNewsImages(doc.images, newImages)
        val result =
            actionsRepository.updateNews(
                baseUrl = base,
                sessionCookie = cookie,
                document = doc,
                message = prepared.message,
                images = mergedImages,
                teamId = selectedTeamId,
                teamName = selectedTeamName,
            )
        result
            .onSuccess {
                Toast
                        .makeText(this@attemptUpdateComment, R.string.dashboard_comment_edit_success, Toast.LENGTH_SHORT)
                    .show()
                exitCommentEditMode(clearFields = true)
                hideReplyKeyboard()
                collapseReplyComposerIfExpanded()
                loadComments(headerItem.id)
            }.onFailure {
                Toast
                        .makeText(this@attemptUpdateComment, R.string.dashboard_comment_edit_error, Toast.LENGTH_SHORT)
                    .show()
            }
        setReplyPosting(false)
    }
}

internal fun DashboardPostDetailActivity.mergeNewsImages(
    existingImages: List<DashboardNewsRepository.NewsImage>?,
    newImages: List<DashboardNewsRepository.NewsImage>,
): List<DashboardNewsRepository.NewsImage> {
    if (existingImages.isNullOrEmpty() && newImages.isEmpty()) {
        return emptyList()
    }
    val merged = LinkedHashMap<String, DashboardNewsRepository.NewsImage>()
    existingImages?.forEach { image ->
        val key = image.markdown ?: buildResourcePath(image.resourceId, image.filename)
        if (!key.isNullOrBlank() && !merged.containsKey(key)) {
            merged[key] = image
        }
    }
    newImages.forEach { image ->
        val key = image.markdown ?: buildResourcePath(image.resourceId, image.filename)
        if (!key.isNullOrBlank() && !merged.containsKey(key)) {
            merged[key] = image
        }
    }
    return merged.values.toList()
}

internal suspend fun DashboardPostDetailActivity.handleReplyImageSelection(uri: Uri) {
    val pendingResult =
        withContext(Dispatchers.IO) {
            runCatching { VoiceImageFactory.createPendingVoiceImage(uri, contentResolver, cacheDir, ::generatePendingImageId) }
        }
    pendingResult
        .onSuccess { pending ->
            pendingReplyImages[pending.id] = pending
            updateReplyPreview(replyPreview, replyInput.text?.toString())
            updateReplyActionAvailabilityInternal(replyInput.text)
        }.onFailure {
            Toast.makeText(this, R.string.create_voice_image_processing_error, Toast.LENGTH_SHORT).show()
        }
}

internal suspend fun DashboardPostDetailActivity.prepareReplyImagesForPosting(
    baseUrl: String,
    credentials: StoredCredentials,
    originalMessage: String,
): PreparedVoicePost {
    if (pendingReplyImages.isEmpty()) {
        return PreparedVoicePost(originalMessage, emptyList())
    }
    val context = buildReplyImageResourceContext(credentials)

    val uploads = pendingReplyImages.values.filter { it.resourceId == null }
    val uploadResults =
        coroutineScope {
            uploads
                .map { pending ->
                    async {
                        val markdown = ensureReplyImageUpload(baseUrl, credentials, context, pending)
                        pending to markdown
                    }
                }.awaitAll()
        }

    val preparedImages = mutableListOf<VoicesComposerRepository.ImagePayload>()

    for ((pending, markdown) in uploadResults) {
        val resourceId = pending.resourceId
        if (resourceId != null) {
            preparedImages +=
                VoicesComposerRepository.ImagePayload(
                    resourceId = resourceId,
                    filename = pending.fileName,
                    markdown = markdown,
                )
        }
    }
    return PreparedVoicePost(originalMessage, preparedImages)
}

internal suspend fun DashboardPostDetailActivity.ensureReplyImageUpload(
    baseUrl: String,
    credentials: StoredCredentials,
    context: VoiceImageResourceContext,
    pending: PendingVoiceImage,
): String {
    pending.uploadedMarkdown?.let { return it }
    val existingResourceId = pending.resourceId
    if (existingResourceId != null) {
        val markdown = "![](resources/${existingResourceId.trim()}/${pending.fileName.trim()})"
        pending.uploadedMarkdown = markdown
        return markdown
    }

    val metadata = VoicesComposerRepository.ResourceMetadataRequest.fromContext(context, pending.fileName)
    val creationResponse = composerRepository.createResourceDocument(baseUrl, credentials, metadata)
    pending.resourceId = creationResponse.id
    pending.resourceRevision = creationResponse.revision
    val uploadResponse =
        composerRepository.uploadResourceBinary(
            baseUrl,
            credentials,
            creationResponse.id,
            pending.fileName,
            creationResponse.revision,
            pending.jpegBytes,
        )
    val resolvedResourceId = uploadResponse.resourceId ?: creationResponse.id
    val resolvedFileName = uploadResponse.filename ?: pending.fileName
    pending.resourceId = resolvedResourceId
    pending.resourceRevision = uploadResponse.revision ?: creationResponse.revision
    val relativeMarkdown =
        uploadResponse.markdown
            ?: "![](resources/${resolvedResourceId.trim()}/${resolvedFileName.trim()})"
    pending.uploadedMarkdown = relativeMarkdown
    return relativeMarkdown
}

internal suspend fun DashboardPostDetailActivity.loadExistingCommentImages(comment: PostDetailItem.Comment) {
    val base = baseUrl ?: return
    if (comment.imagePaths.isEmpty()) {
        return
    }
    val loaded =
        coroutineScope {
            comment.imagePaths
                .map { path ->
                    async(Dispatchers.IO) {
                        VoiceImageFetcher.fetchExistingImage(httpClient, cacheDir, sessionCookie, base, path, {
                            VoiceImageFactory.generateImageFileName()
                        }, { generatePendingImageId(it) })
                    }
                }.awaitAll()
                .filterNotNull()
                .toMutableList()
        }
    if (loaded.isEmpty()) {
        return
    }
    loaded.forEach { pending ->
        pendingReplyImages[pending.id] = pending
    }
    updateReplyPreview(replyPreview, replyInput.text?.toString())
}

internal suspend fun DashboardPostDetailActivity.buildReplyImageResourceContext(credentials: StoredCredentials): VoiceImageResourceContext {
    val preferences = SecurePreferencesProvider.getServerPreferences(applicationContext)
    val androidId = preferences.getString(KEY_DEVICE_ANDROID_ID, null)?.takeIf { it.isNotBlank() }
    val customDeviceName = preferences.getString(KEY_DEVICE_CUSTOM_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }
    val storedServerCode = preferences.getString(KEY_SERVER_CODE, null)?.takeIf { it.isNotBlank() }
    val storedParentCode = preferences.getString(KEY_SERVER_PARENT_CODE, null)?.takeIf { it.isNotBlank() }
    val profile = withContext(Dispatchers.IO) { loadCachedProfile() }
    val parsedCodes = parseCodesFromProfile(profile?.rawDocument)
    val resolvedResideOn =
        serverCode?.takeIf { it.isNotBlank() }
            ?: storedServerCode
            ?: parsedCodes?.planetCode
    val resolvedParent = storedParentCode ?: parsedCodes?.parentCode
    return VoiceImageResourceContext(
        username = credentials.username,
        resideOn = resolvedResideOn,
        sourcePlanet = resolvedParent,
        androidId = androidId,
        deviceName =
            org.ole.planet.myplanet.lite.util.DeviceUtils
                .getDeviceName(),
        customDeviceName = customDeviceName,
    )
}

internal fun DashboardPostDetailActivity.generatePendingImageId(baseName: String): String {
    var candidate = baseName
    var counter = 1
    while (pendingReplyImages.containsKey(candidate)) {
        candidate = "${baseName}_$counter"
        counter++
    }
    return candidate
}
