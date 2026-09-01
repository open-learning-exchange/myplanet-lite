/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-07
 */

package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.MarkdownUtils
import java.util.LinkedHashMap

internal suspend fun CreateVoiceActivity.prepareImagesForPosting(
    baseUrl: String,
    credentials: StoredCredentials,
    originalMessage: String,
): PreparedVoicePost {
    val context = buildImageResourceContext(credentials)
    val (dedupedMessage, dedupedExistingImages) = deduplicateMessageImages(originalMessage)
    val uniquePendings = buildUniquePendingList()
    if (uniquePendings.isEmpty()) {
        return PreparedVoicePost(dedupedMessage, emptyList())
    }
    var updatedMessage = dedupedMessage
    val preparedImages = LinkedHashMap<String, VoicesComposerRepository.ImagePayload>()
    val normalizedMessageImages = dedupedExistingImages.toMutableSet()
    val uploadResults =
        coroutineScope {
            uniquePendings
                .map { pending ->
                    async {
                        val requiresUpload = shouldUploadPending(pending)
                        val markdown =
                            if (requiresUpload) {
                                ensureImageUpload(baseUrl, credentials, context, pending)
                            } else {
                                resolveExistingMarkdown(pending) ?: ensureImageUpload(baseUrl, credentials, context, pending)
                            }
                        pending to markdown
                    }
                }.awaitAll()
        }

    for ((pending, markdown) in uploadResults) {
        val normalizedPath = normalizeImagePath(markdown)
        val replaced = MarkdownUtils.replaceImagePlaceholder(updatedMessage, pending.fileName, markdown)
        if (!normalizedMessageImages.contains(normalizedPath)) {
            updatedMessage = ensureMarkdownPresent(replaced, markdown)
            normalizedMessageImages += normalizedPath
        } else {
            updatedMessage = replaced
        }
        val resourceId = pending.resourceId
        if (resourceId != null && normalizedPath.isNotBlank()) {
            preparedImages.putIfAbsent(
                normalizedPath,
                VoicesComposerRepository.ImagePayload(
                    resourceId = resourceId,
                    filename = pending.fileName,
                    markdown = markdown,
                ),
            )
        }
    }
    return PreparedVoicePost(updatedMessage, preparedImages.values.toList())
}

internal fun CreateVoiceActivity.shouldUploadPending(pending: PendingVoiceImage): Boolean {
    pending.uploadedMarkdown?.let { existing ->
        val path = extractPathFromMarkdown(existing)
        val normalized = path?.removePrefix("db/")?.trimStart('/') ?: ""
        if (normalized.startsWith("resources/", ignoreCase = true) ||
            path?.startsWith("http://", ignoreCase = true) == true ||
            path?.startsWith("https://", ignoreCase = true) == true
        ) {
            return false
        }
    }
    if (pending.resourceId != null && pending.resourceRevision != null) {
        return false
    }
    return true
}

internal fun CreateVoiceActivity.resolveExistingMarkdown(pending: PendingVoiceImage): String? {
    pending.uploadedMarkdown?.let { existing ->
        val path = extractPathFromMarkdown(existing)
        val normalized = path?.removePrefix("db/")?.trimStart('/') ?: ""
        val resolved =
            when {
                path.isNullOrBlank() -> {
                    existing
                }

                path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) -> {
                    "![](${path.trim()})"
                }

                normalized.startsWith("resources/", ignoreCase = true) -> {
                    "![]($normalized)"
                }

                else -> {
                    existing
                }
            }
        pending.uploadedMarkdown = resolved
        return resolved
    }
    val resourceId = pending.resourceId ?: return null
    val markdown = "![](resources/$resourceId/${pending.fileName})"
    pending.uploadedMarkdown = markdown
    return markdown
}

internal suspend fun CreateVoiceActivity.ensureImageUpload(
    baseUrl: String,
    credentials: StoredCredentials,
    context: VoiceImageResourceContext,
    pending: PendingVoiceImage,
): String {
    pending.uploadedMarkdown?.let { existing ->
        val path = extractPathFromMarkdown(existing)
        val normalized = path?.removePrefix("db/")?.trimStart('/') ?: ""
        val shouldUpload =
            normalized.startsWith("post", ignoreCase = true) ||
                normalized.isEmpty()
        if (!shouldUpload) {
            val resolved =
                when {
                    path.isNullOrBlank() -> {
                        existing
                    }

                    path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) -> {
                        "![](${path.trim()})"
                    }

                    normalized.startsWith("resources/", ignoreCase = true) -> {
                        "![]($normalized)"
                    }

                    else -> {
                        existing
                    }
                }
            pending.uploadedMarkdown = resolved
            return resolved
        }
    }
    val resourceId = pending.resourceId
    val resourceRevision = pending.resourceRevision
    val creationResponse =
        if (resourceId != null && resourceRevision != null) {
            VoicesComposerRepository.ResourceCreationResponse(
                ok = true,
                id = resourceId,
                revision = resourceRevision,
            )
        } else {
            val metadata = VoicesComposerRepository.ResourceMetadataRequest.fromContext(
                context,
                pending.fileName,
                targetTeamId,
            )
            repository.createResourceDocument(baseUrl, credentials, metadata)
        }
    pending.resourceId = creationResponse.id
    pending.resourceRevision = creationResponse.revision
    val uploadResponse =
        repository.uploadResourceBinary(
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
    val sanitizedId = resolvedResourceId.trim()
    val sanitizedName = resolvedFileName.trim()
    val normalizedMarkdown = "![](resources/$sanitizedId/$sanitizedName)"
    pending.uploadedMarkdown = normalizedMarkdown
    return normalizedMarkdown
}

internal fun CreateVoiceActivity.ensureMarkdownPresent(
    message: String,
    markdown: String,
): String {
    if (message.contains(markdown)) {
        return message
    }
    val resourcePath = extractResourcePath(markdown)
    if (resourcePath != null) {
        val pattern = Regex("!\\[[^\\]]*\\]\\((?:https?://[^)]+/)?(?:/?db/)?/?${Regex.escape(resourcePath)})\\)")
        if (pattern.containsMatchIn(message)) {
            return message
        }
    }
    val builder = StringBuilder(message.trimEnd())
    if (builder.isNotEmpty()) {
        builder.append("\n\n")
    }
    builder.append(markdown)
    return builder.toString()
}

internal fun CreateVoiceActivity.extractResourcePath(markdown: String): String? {
    val matcher = CreateVoiceActivity.MARKDOWN_IMAGE_REGEX.find(markdown) ?: return null
    val rawPath = matcher.groupValues.getOrNull(1)?.trim('/') ?: return null
    val trimmed =
        when {
            rawPath.startsWith("db/resources/", ignoreCase = true) -> rawPath.removePrefix("db/")
            rawPath.startsWith("resources/", ignoreCase = true) -> rawPath
            else -> return null
        }
    return trimmed
}


data class PreparedVoicePost(
    val message: String,
    val images: List<VoicesComposerRepository.ImagePayload>,
)
