/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-19
 */
package org.ole.planet.myplanet.lite.dashboard
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.ole.planet.myplanet.lite.R

class PostShareHelper(
    private val context: Context,
    private val baseUrlProvider: () -> String?,
    private val sessionCookieProvider: () -> String?,
    private val serverNameProvider: () -> String?,
) {
    private val client = OkHttpClient()
    suspend fun sharePost(
        _postId: String?,
        author: String?,
        message: String?,
        imagePaths: List<String>,
    ) {
        val sanitizedMessage = sanitizeMessage(message)
        val htmlMessage = sanitizeForHtml(message)
        val shareText = buildShareText(author, sanitizedMessage)
        val serverName = serverNameProvider()?.takeIf { it.isNotBlank() }
        val title = buildShareTitle(serverName)
        val combinedText = listOf(title, shareText)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
        val combinedHtmlText = listOf(title, htmlMessage ?: shareText)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
        val combinedHtml = combinedHtmlText.takeIf { it.isNotBlank() }
            ?.let { toHtml(it) }
        val imageUris = downloadImages(imagePaths)
        withContext(Dispatchers.Main) {
            val hasImages = imageUris.isNotEmpty()
            val multipleImages = imageUris.size > 1
            val intent = Intent(if (multipleImages) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND)
            intent.apply {
                type = if (hasImages) "image/*" else "text/plain"
                putExtra(Intent.EXTRA_TEXT, combinedText)
                combinedHtml?.let { html -> putExtra(Intent.EXTRA_HTML_TEXT, html) }
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TITLE, title)
                if (hasImages) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (multipleImages) {
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
                        clipData = ClipData.newUri(context.contentResolver, "shared_image", imageUris.first()).apply {
                            imageUris.drop(1).forEach { addItem(ClipData.Item(it)) }
                        }
                    } else {
                        val first = imageUris.first()
                        putExtra(Intent.EXTRA_STREAM, first)
                        clipData = ClipData.newUri(context.contentResolver, "shared_image", first)
                    }
                }
            }
            val chooserTitle = context.getString(R.string.dashboard_share_post_chooser_title)
            val chooser = Intent.createChooser(intent, chooserTitle)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
    private fun buildShareText(author: String?, message: String?): String {
        val trimmedMessage = message?.trim().orEmpty()
        val combined = buildString {
            if (!author.isNullOrBlank()) {
                append(author.trim())
                if (trimmedMessage.isNotEmpty()) {
                    append("\n\n")
                }
            }
            append(trimmedMessage)
        }.trim()
        return combined.ifBlank { context.getString(R.string.dashboard_share_post_fallback) }
    }
    private fun buildShareTitle(serverName: String?): String {
        val resolvedServer = serverName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: context.getString(R.string.dashboard_share_post_server_fallback)
        return context.getString(R.string.dashboard_share_post_title, resolvedServer)
    }
    private suspend fun downloadImages(imagePaths: List<String>): List<Uri> {
        val baseUrl = baseUrlProvider()
        val cookie = sessionCookieProvider()
        val uris = mutableListOf<Uri>()
        for (path in imagePaths) {
            val url = resolveUrl(path, baseUrl) ?: continue
            val file = downloadImageToCache(url, cookie) ?: continue
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            uris.add(uri)
        }
        return uris
    }
    private suspend fun downloadImageToCache(url: String, cookie: String?): File? = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(url)
            cookie?.takeIf { it.isNotBlank() }?.let { requestBuilder.addHeader("Cookie", it) }
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                val body = response.body
                val bytes = body.bytes()
                if (bytes.isEmpty()) {
                    return@withContext null
                }
                val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
                val file = File.createTempFile("post_", ".jpg", directory)
                file.outputStream().use { stream ->
                    stream.write(bytes)
                }
                file
            }
        } catch (error: IOException) {
            null
        }
    }
    companion object {
        private val IMAGE_MARKDOWN_REGEX = Regex("!?\\[[^\\]]*\\]\\([^)]*\\.(?:jpe?g|png)\\)", RegexOption.IGNORE_CASE)
        private val IMAGE_URL_REGEX = Regex("\\b\\S+\\.(?:jpe?g|png)(?:\\?\\S*)?(?=\\s|$)", RegexOption.IGNORE_CASE)
        private val LINK_MARKDOWN_REGEX = Regex("\\[([^\\]]+)\\]\\(([^\\s)]+)\\)")
        private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__")
        private val ITALIC_REGEX = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)")
        fun sanitizeMessage(raw: String?): String? {
            if (raw.isNullOrBlank()) {
                return null
            }
            val withoutImages = IMAGE_MARKDOWN_REGEX.replace(raw, " ")
            val withoutImageUrls = IMAGE_URL_REGEX.replace(withoutImages, " ")
            val withoutLinks = LINK_MARKDOWN_REGEX.replace(withoutImageUrls) { match ->
                match.groupValues.getOrNull(1).orEmpty()
            }
            val withoutBold = BOLD_REGEX.replace(withoutLinks) { match ->
                match.groups.filterNotNull().drop(1).firstOrNull()?.value ?: ""
            }
            val withoutItalics = ITALIC_REGEX.replace(withoutBold) { match ->
                match.groups.filterNotNull().drop(1).firstOrNull()?.value ?: ""
            }
            val cleaned = withoutItalics.replace("\\s+".toRegex(), " ").trim()
            return cleaned.ifBlank { null }
        }
        fun sanitizeForHtml(raw: String?): String? {
            if (raw.isNullOrBlank()) {
                return null
            }
            val withoutImages = IMAGE_MARKDOWN_REGEX.replace(raw, " ")
            val withoutImageUrls = IMAGE_URL_REGEX.replace(withoutImages, " ")
            return withoutImageUrls.trim().ifBlank { null }
        }
        fun toHtml(text: String): String {
            val escapedSource = android.text.Html.escapeHtml(text)
            var html = LINK_MARKDOWN_REGEX.replace(escapedSource) { match ->
                val label = android.text.Html.escapeHtml(match.groupValues.getOrNull(1)?.trim().orEmpty())
                val url = android.text.Html.escapeHtml(match.groupValues.getOrNull(2)?.trim().orEmpty())
                if (label.isEmpty()) return@replace url
                "<a href=\"$url\">$label</a>"
            }
            html = BOLD_REGEX.replace(html) { matchResult ->
                val boldText = matchResult.groups.filterNotNull().drop(1).firstOrNull()?.value ?: ""
                "<b>$boldText</b>"
            }
            html = ITALIC_REGEX.replace(html) { matchResult ->
                val italicText = matchResult.groups.filterNotNull().drop(1).firstOrNull()?.value ?: ""
                "<i>$italicText</i>"
            }
            return html.replace("\n", "<br/>")
        }
        private fun resolveUrl(path: String, baseUrl: String?): String? {
            val base = baseUrl?.trim()?.trimEnd('/') ?: return null
            if (base.isEmpty()) {
                return null
            }
            val trimmedPath = path.trim()
            if (trimmedPath.isEmpty()) {
                return null
            }
            if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
                return trimmedPath
            }
            val normalizedPath = trimmedPath.trimStart('/')
            val finalPath = if (normalizedPath.startsWith("db/")) normalizedPath else "db/$normalizedPath"
            return "$base/$finalPath"
        }
    }
}
