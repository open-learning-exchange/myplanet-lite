package org.ole.planet.myplanet.lite.util

import android.content.Context
import org.ole.planet.myplanet.lite.OfflineCourseStorage

object MarkdownUtils {

    fun normalizeMarkdownImageSource(rawSource: String): String {
        var value = rawSource.trim()
        if (value.startsWith("<") && value.endsWith(">")) {
            value = value.removePrefix("<").removeSuffix(">")
        }
        if (value.contains(" ")) {
            value = value.substringBefore(" ")
        }
        return value.trim()
    }

    fun resolveMarkdownSourceUrl(base: String?, source: String): String? {
        val trimmed = source.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("file://")) return trimmed
        val normalizedBase = base?.trim()?.trimEnd('/').orEmpty()
        if (normalizedBase.isBlank()) return null
        val normalizedPath = trimmed.trimStart('/')
        val finalPath = if (normalizedPath.startsWith("db/")) normalizedPath else "db/$normalizedPath"
        return "$normalizedBase/$finalPath"
    }

    fun extractImageSourcesFromText(text: String): List<String> {
        val markdownPattern = Regex("""!\[[^\]]*]\(([^)]+)\)""")
        val htmlPattern = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val fromMarkdown = markdownPattern.findAll(text)
            .map { normalizeMarkdownImageSource(it.groupValues[1]) }
            .toList()
        val fromHtml = htmlPattern.findAll(text)
            .map { normalizeMarkdownImageSource(it.groupValues[1]) }
            .toList()
        return (fromMarkdown + fromHtml).filter { it.isNotBlank() }
    }

    fun resolveOfflineMarkdownImages(
        context: Context,
        markdown: String,
        courseId: String?,
        baseUrl: String?
    ): String {
        val safeCourseId = courseId?.takeIf { it.isNotBlank() } ?: return markdown
        var resolved = markdown
        val markdownPattern = Regex("""!\[([^]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
        resolved = markdownPattern.replace(resolved) { match ->
            val alt = match.groupValues[1]
            val source = normalizeMarkdownImageSource(match.groupValues[2])
            val local = OfflineCourseStorage.localMarkdownImageUri(context, safeCourseId, source)
            val selected = local ?: resolveMarkdownSourceUrl(baseUrl, source) ?: source
            "![$alt]($selected)"
        }
        val htmlPattern = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
        val srcAttributePattern = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val altAttributePattern = Regex("""\balt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        resolved = htmlPattern.replace(resolved) { match ->
            val imageTag = match.value
            val source = normalizeMarkdownImageSource(
                srcAttributePattern.find(imageTag)?.groupValues?.get(1).orEmpty()
            )
            if (source.isBlank()) {
                return@replace imageTag
            }
            val alt = altAttributePattern.find(imageTag)?.groupValues?.get(1).orEmpty()
            val local = OfflineCourseStorage.localMarkdownImageUri(context, safeCourseId, source)
            val selected = local ?: resolveMarkdownSourceUrl(baseUrl, source) ?: source
            "![$alt]($selected)"
        }
        return resolved
    }
}
