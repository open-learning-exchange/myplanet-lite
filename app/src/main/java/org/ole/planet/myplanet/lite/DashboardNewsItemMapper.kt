/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-08-09
 */

package org.ole.planet.myplanet.lite

import android.content.Context
import android.text.TextUtils
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository.NewsDocument
import org.ole.planet.myplanet.lite.util.DateUtils

internal data class DashboardNewsItem(
    val id: String,
    val author: String,
    val username: String?,
    val metadata: String,
    val message: String?,
    val hasAvatar: Boolean,
    val imagePaths: List<String>,
    val commentCount: Int,
    val timestamp: Long,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val canShare: Boolean,
    val document: NewsDocument,
)

internal class DashboardNewsItemMapper(
    private val context: Context,
) {
    fun map(
        document: NewsDocument,
        commentCount: Int,
        sessionCookie: String?,
        currentUsername: String?,
        isUserAdmin: Boolean,
    ): DashboardNewsItem? {
        val id = document.id ?: return null
        val username = document.user?.name?.takeIf { it.isNotBlank() }
        val displayName = buildDisplayName(document, username)
        val timeMillis = document.time ?: 0L
        val relativeTime = DateUtils.formatRelativeTime(context, timeMillis)
        val metadata = DateUtils.buildMetadata(username, relativeTime)
        val rawMessage = document.message
        val imagePaths = (extractImagePaths(rawMessage) + mapDocumentImages(document)).distinct()
        val hasSession = !sessionCookie.isNullOrBlank()
        val isAuthor =
            hasSession &&
                !username.isNullOrBlank() &&
                currentUsername?.equals(username, ignoreCase = true) == true
        return DashboardNewsItem(
            id = id,
            author = displayName,
            username = username,
            metadata = metadata,
            message = rawMessage?.trim()?.takeIf { it.isNotEmpty() },
            hasAvatar = document.user?.attachments?.containsKey("img") == true,
            imagePaths = imagePaths,
            commentCount = commentCount,
            timestamp = timeMillis,
            canEdit = isAuthor,
            canDelete = isAuthor || isUserAdmin,
            canShare = hasSession,
            document = document,
        )
    }

    private fun buildDisplayName(document: NewsDocument, username: String?): String {
        val parts =
            document.user
                ?.let { listOfNotNull(it.firstName, it.middleName, it.lastName) }
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        return when {
            parts.isNotEmpty() -> TextUtils.join(" ", parts)
            !username.isNullOrEmpty() -> username
            else -> context.getString(R.string.dashboard_profile_name_placeholder)
        }
    }

    private fun extractImagePaths(raw: String?): List<String> =
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            IMAGE_MARKDOWN_CAPTURE_REGEX
                .findAll(raw)
                .mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf(String::isNotBlank) }
                .toList()
        }

    private fun mapDocumentImages(document: NewsDocument): List<String> =
        document.images
            ?.mapNotNull { image ->
                extractImagePath(image.markdown) ?: buildResourcePath(image.resourceId, image.filename)
            }?.filter(String::isNotBlank)
            .orEmpty()

    private fun extractImagePath(markdown: String?): String? =
        markdown
            ?.takeIf(String::isNotBlank)
            ?.let(IMAGE_MARKDOWN_CAPTURE_REGEX::find)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun buildResourcePath(resourceId: String?, filename: String?): String? {
        val id = resourceId?.trim().takeUnless { it.isNullOrEmpty() }
        val name = filename?.trim().takeUnless { it.isNullOrEmpty() }
        return if (id == null || name == null) null else "resources/$id/$name"
    }

    companion object {
        private val IMAGE_MARKDOWN_CAPTURE_REGEX = Regex("!\\[[^]]*]\\(([^)]+)\\)")
    }
}
