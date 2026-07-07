/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-07
 */

package org.ole.planet.myplanet.lite.dashboard

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.R
import java.io.File
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale

internal fun CreateVoiceActivity.updatePreview(text: String) {
    val trimmed = text.trim()
    val content =
        if (trimmed.isEmpty()) {
            createVoicePreviewText.alpha = 0.6f
            getString(R.string.create_voice_preview_placeholder)
        } else {
            createVoicePreviewText.alpha = 1f
            trimmed
        }
    val previewSource = transformMarkdownForPreviewContent(content)
    markwon.setMarkdown(createVoicePreviewText, previewSource)
}

internal fun CreateVoiceActivity.transformMarkdownForPreviewContent(markdown: String): String {
    var processed = markdown.replace("\n", "  \n")
    if (pendingImages.isNotEmpty()) {
        val pendingByFileName = pendingImages.values.associateBy { it.fileName }
        if (pendingByFileName.isNotEmpty()) {
            val globalPattern = Regex("(!\\[[^\\]]*\\]\\()(.*?)(\\))")
            processed =
                globalPattern.replace(processed) { matchResult ->
                    val path = matchResult.groupValues.getOrNull(2).orEmpty()
                    val pending = pendingByFileName[path]
                    if (pending != null) {
                        val prefix = matchResult.groupValues.getOrNull(1).orEmpty()
                        val suffix = matchResult.groupValues.getOrNull(3).orEmpty()
                        "$prefix${pending.file.toURI()}$suffix"
                    } else {
                        matchResult.value
                    }
                }
        }
    }
    val base = baseUrl?.trim()?.trimEnd('/')
    if (base.isNullOrEmpty()) {
        return processed
    }
    val resourcesPattern = Regex("!\\[[^\\]]*\\]\\((?:/?db/)?/?resources/([^)]+)\\)")
    return resourcesPattern.replace(processed) { matchResult ->
        val path = matchResult.groupValues.getOrNull(1).orEmpty()
        "![]($base/db/resources/$path)"
    }
}

internal fun CreateVoiceActivity.renderPreviewImages() {
    val wrapper = createVoicePreviewImages
    wrapper.removeAllViews()
    if (pendingImages.isEmpty()) {
        wrapper.isVisible = false
        return
    }

    val displayPendings = buildUniquePendingList(includeUploaded = true)

    if (displayPendings.isEmpty()) {
        wrapper.isVisible = false
        return
    }

    wrapper.isVisible = true
    val spacing = resources.getDimensionPixelSize(R.dimen.create_voice_preview_image_spacing)
    val thumbnailSize = resources.getDimensionPixelSize(R.dimen.create_voice_preview_image_thumbnail_size)
    displayPendings.forEachIndexed { index, pending ->
        val preview =
            ImageView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        thumbnailSize,
                        thumbnailSize,
                    )
                        .apply {
                            setMargins(0, if (index == 0) 0 else spacing, 0, 0)
                        }
                adjustViewBounds = false
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = pending.fileName
                setOnClickListener {
                    showImageOptionsDialog(pending)
                }
            }
        wrapper.addView(preview)
        lifecycleScope.launch(Dispatchers.Default) {
            val bitmap =
                decodedBitmaps.getOrPut(pending.id) {
                    BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
                }
            withContext(Dispatchers.Main) {
                preview.setImageBitmap(bitmap)
            }
        }
    }
}

internal fun CreateVoiceActivity.showImageOptionsDialog(pending: PendingVoiceImage) {
    val optionItems =
        arrayOf(
            getString(R.string.create_voice_image_option_view),
            getString(R.string.create_voice_image_option_delete),
        )
    val optionIcons =
        intArrayOf(
            R.drawable.icon_image_view,
            R.drawable.ic_dashboard_delete_24,
        )
    MaterialAlertDialogBuilder(this).setTitle(R.string.create_voice_image_options_title)
        .setAdapter(ImageOptionAdapter(this, optionItems, optionIcons)) { dialog, which ->
            when (which) {
                0 -> showImagePreviewDialog(pending)
                1 -> deletePendingImage(pending)
            }
            dialog.dismiss()
        }.setNegativeButton(android.R.string.cancel, null)
        .show()
}

internal fun CreateVoiceActivity.showImagePreviewDialog(pending: PendingVoiceImage) {
    val imageView =
        ImageView(this).apply {
            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = pending.fileName
            val padding = resources.getDimensionPixelSize(R.dimen.create_voice_image_preview_dialog_padding)
            setPadding(padding, padding, padding, padding)
        }
    lifecycleScope.launch(Dispatchers.Default) {
        val bitmap =
            decodedBitmaps.getOrPut(pending.id) {
                BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
            }
        withContext(Dispatchers.Main) {
            imageView.setImageBitmap(bitmap)
        }
    }
    MaterialAlertDialogBuilder(this)
        .setTitle(pending.fileName)
        .setView(imageView)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

internal fun CreateVoiceActivity.deletePendingImage(pending: PendingVoiceImage) {
    val normalizedKey = derivePendingNormalizedKey(pending)
    val idsToRemove =
        pendingImages.values
            .filter { derivePendingNormalizedKey(it) == normalizedKey }
            .map { it.id }

    val filesToDelete = mutableListOf<File>()

    idsToRemove.forEach { id ->
        val removed = pendingImages.remove(id) ?: return@forEach
        decodedBitmaps.remove(id)?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        removeImageMarkdownReferences(removed)
        filesToDelete.add(removed.file)
    }

    if (filesToDelete.isNotEmpty()) {
        lifecycleScope.launch(Dispatchers.IO) {
            filesToDelete.forEach {
                if (it.exists()) {
                    it.delete()
                }
            }
        }
    }

    updatePreview(createVoiceInput.text?.toString().orEmpty())
    renderPreviewImages()
}

internal fun CreateVoiceActivity.removeImageMarkdownReferences(pending: PendingVoiceImage) {
    val editable = createVoiceInput.text ?: return
    val current = editable.toString()
    var updated = current
    val candidates =
        listOfNotNull(
            pending.fileName.takeIf { it.isNotBlank() },
            extractPathFromMarkdown(pending.uploadedMarkdown).takeIf { !it.isNullOrBlank() },
        ).distinct()

    if (candidates.isNotEmpty()) {
        val combinedEscaped = candidates.joinToString("|") { Regex.escape(it.trim()) }
        val pattern = Regex("(?:^|\\n)!\\[[^\\]]*\\]\\((?:https?://[^)]+/)?(?:/?db/)?/?(?:$combinedEscaped)\\)\\n?")
        var previous: String
        do {
            previous = updated
            updated = pattern.replace(updated, "\n")
        } while (updated != previous)
    }

    updated =
        updated
            .replace(Regex("\\n{3,}"), "\n\n")
            .trimEnd()

    if (updated != current) {
        editable.replace(0, editable.length, updated)
        createVoiceInput.setSelection(updated.length.coerceAtLeast(0))
    }
}

private class ImageOptionAdapter(
    context: CreateVoiceActivity,
    private val items: Array<String>,
    private val icons: IntArray,
) : android.widget.ArrayAdapter<String>(
        context,
        android.R.layout.select_dialog_item,
        items,
    ) {
    override fun getView(
        position: Int,
        convertView: android.view.View?,
        parent: ViewGroup,
    ): android.view.View {
        val view = super.getView(position, convertView, parent)
        val text = view.findViewById<TextView>(android.R.id.text1)
        text.setCompoundDrawablesRelativeWithIntrinsicBounds(icons[position], 0, 0, 0)
        text.compoundDrawablePadding =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                context.resources.displayMetrics,
            )
                .toInt()
        return view
    }
}

internal fun CreateVoiceActivity.buildUniquePendingList(includeUploaded: Boolean = false): List<PendingVoiceImage> {
    val uniquePendingMap = LinkedHashMap<String, PendingVoiceImage>()
    pendingImages.values.forEach { pending ->
        if (!includeUploaded && !pending.resourceId.isNullOrBlank()) {
            return@forEach
        }
        val key = derivePendingNormalizedKey(pending)
        if (key.isNotBlank() && !uniquePendingMap.containsKey(key)) {
            uniquePendingMap[key] = pending
        }
    }
    return uniquePendingMap.values.toList()
}

internal suspend fun CreateVoiceActivity.handleImageSelection(uri: Uri) {
    val pendingResult =
        withContext(Dispatchers.IO) {
            runCatching {
                VoiceImageFactory.createPendingVoiceImage(
                    uri,
                    contentResolver,
                    cacheDir,
                    ::generatePendingImageId,
                )
            }
        }
    pendingResult
        .onSuccess { pending ->
            pendingImages[pending.id] = pending
            if (isEditMode) {
                insertTemporaryImagePlaceholder(pending.fileName)
            }
            updatePreview(createVoiceInput.text?.toString().orEmpty())
            renderPreviewImages()
        }.onFailure {
            Toast.makeText(this, R.string.create_voice_image_processing_error, Toast.LENGTH_SHORT).show()
        }
}

internal suspend fun CreateVoiceActivity.loadEditInitialImages() {
    if (!isEditMode || editInitialImagePaths.isEmpty() || editImagesLoaded) {
        return
    }
    val base = baseUrl ?: return
    val loaded =
        coroutineScope {
            val semaphore = Semaphore(10)
            editInitialImagePaths
                .map { path ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            runCatching {
                                VoiceImageFetcher.fetchExistingImage(
                                    httpClient,
                                    cacheDir,
                                    sessionCookie,
                                    base,
                                    path,
                                    { VoiceImageFactory.generateImageFileName() },
                                    { generatePendingImageId(it) },
                                )
                            }.getOrNull()
                        }
                    }
                }.awaitAll()
                .filterNotNull()
        }
    if (loaded.isEmpty()) {
        return
    }
    editImagesLoaded = true
    loaded.forEach { pending ->
        pendingImages[pending.id] = pending
    }
    renderPreviewImages()
}

internal fun CreateVoiceActivity.extractPathFromMarkdown(markdown: String?): String? {
    if (markdown.isNullOrBlank()) {
        return null
    }
    val match = CreateVoiceActivity.MARKDOWN_IMAGE_REGEX.find(markdown)
    return match
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

internal fun CreateVoiceActivity.buildResourcePath(
    resourceId: String?,
    fileName: String?,
): String? {
    if (resourceId.isNullOrBlank() || fileName.isNullOrBlank()) {
        return null
    }
    return "resources/${resourceId.trim()}/${fileName.trim()}"
}

internal fun CreateVoiceActivity.derivePendingNormalizedKey(pending: PendingVoiceImage): String {
    val candidates =
        listOfNotNull(
            pending.uploadedMarkdown?.let { extractPathFromMarkdown(it) },
            buildResourcePath(pending.resourceId, pending.fileName),
            pending.fileName,
        )
    val normalized =
        candidates
            .map { candidate -> normalizeImagePath(candidate) }
            .firstOrNull { candidate -> candidate.isNotBlank() }
    return (normalized ?: normalizeImagePath(pending.fileName)).trim()
}

internal fun CreateVoiceActivity.mergeImagePaths(paths: List<String>): List<String> =
    paths.distinctBy { normalizeImagePath(it) }

internal fun CreateVoiceActivity.normalizeImagePath(path: String): String {
    val extracted = extractPathFromMarkdown(path) ?: path
    val trimmed = extracted.trim()
    val resourcesMatch =
        Regex("resources/[^/]+/[^/]+", RegexOption.IGNORE_CASE)
            .find(trimmed)
    val reduced =
        if (resourcesMatch != null) {
            resourcesMatch.value
        } else {
            trimmed.trimStart('/').removePrefix("db/").trimStart('/')
        }
    return reduced.lowercase(Locale.US)
}

internal fun CreateVoiceActivity.deduplicateMessageImages(markdown: String): Pair<String, Set<String>> {
    if (markdown.isBlank()) {
        return "" to emptySet()
    }
    val builder = StringBuilder()
    var lastIndex = 0
    val seen = LinkedHashSet<String>()
    CreateVoiceActivity.MARKDOWN_IMAGE_REGEX.findAll(markdown).forEach { match ->
        val path = match.groupValues.getOrNull(1)
        val normalized = path?.let { normalizeImagePath(it) }.orEmpty()
        val keep = normalized.isNotBlank() && seen.add(normalized)
        builder.append(markdown.substring(lastIndex, match.range.first))
        if (keep) {
            builder.append(match.value)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < markdown.length) {
        builder.append(markdown.substring(lastIndex))
    }
    return builder.toString() to seen
}

internal fun CreateVoiceActivity.insertTemporaryImagePlaceholder(fileName: String) {
    val editable = createVoiceInput.text ?: return
    val placeholder = "![]($fileName)"
    val current = editable.toString()
    if (current.contains(placeholder)) {
        return
    }
    val builder = StringBuilder(current)
    if (builder.isNotEmpty() && builder.last() != '\n') {
        builder.append('\n')
    }
    builder.append('\n').append(placeholder)
    val updated = builder.toString()
    editable.replace(0, editable.length, updated)
    createVoiceInput.setSelection(updated.length)
}

internal fun CreateVoiceActivity.generatePendingImageId(baseName: String): String {
    var candidate = baseName
    var counter = 1
    while (pendingImages.containsKey(candidate)) {
        candidate = "${baseName}_$counter"
        counter++
    }
    return candidate
}
