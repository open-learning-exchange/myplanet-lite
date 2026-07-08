package org.ole.planet.myplanet.lite.util

import android.content.Context
import org.ole.planet.myplanet.lite.OfflineCourseStorage

object MarkdownUtils {

    private val EXTRACT_MARKDOWN_PATTERN = Regex("""!\[[^\]]*]\(([^)]+)\)""")
    private val EXTRACT_HTML_PATTERN = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val RESOLVE_MARKDOWN_PATTERN = Regex("""!\[([^]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
    private val RESOLVE_HTML_PATTERN = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val SRC_ATTRIBUTE_PATTERN = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ALT_ATTRIBUTE_PATTERN = Regex("""\balt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

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
        val fromMarkdown = EXTRACT_MARKDOWN_PATTERN.findAll(text)
            .map { normalizeMarkdownImageSource(it.groupValues[1]) }
            .toList()
        val fromHtml = EXTRACT_HTML_PATTERN.findAll(text)
            .map { normalizeMarkdownImageSource(it.groupValues[1]) }
            .toList()
        return (fromMarkdown + fromHtml).filter { it.isNotBlank() }
    }

    fun replaceImagePlaceholder(source: String, fileName: String, replacement: String): String {
        val escapedName = Regex.escape(fileName)
        val pattern = Regex("!\\[([^\\]]*)\\]\\($escapedName\\)")
        var matched = false
        val updated = pattern.replace(source) { matchResult ->
            matched = true
            val altText = matchResult.groupValues.getOrNull(1).orEmpty()
            if (altText.isBlank()) {
                replacement
            } else {
                applyAltTextToMarkdown(replacement, altText)
            }
        }
        if (matched) {
            return updated
        }
        val builder = StringBuilder(source)
        if (builder.isNotEmpty()) {
            if (builder[builder.length - 1] != '\n') {
                builder.append('\n')
            }
            builder.append('\n')
        }
        builder.append(replacement)
        return builder.toString()
    }

    private fun applyAltTextToMarkdown(markdown: String, altText: String): String {
        val trimmedAlt = altText.trim()
        if (trimmedAlt.isEmpty()) {
            return markdown
        }
        val openBracket = markdown.indexOf('[')
        val closeBracket = markdown.indexOf(']')
        if (openBracket == -1 || closeBracket <= openBracket) {
            return markdown
        }
        return buildString {
            append(markdown.substring(0, openBracket + 1))
            append(trimmedAlt)
            append(markdown.substring(closeBracket))
        }
    }

    fun resolveOfflineMarkdownImages(
        context: Context,
        markdown: String,
        courseId: String?,
        baseUrl: String?
    ): String {
        val safeCourseId = courseId?.takeIf { it.isNotBlank() } ?: return markdown
        var resolved = markdown
        resolved = RESOLVE_MARKDOWN_PATTERN.replace(resolved) { match ->
            val alt = match.groupValues[1]
            val source = normalizeMarkdownImageSource(match.groupValues[2])
            val local = OfflineCourseStorage.localMarkdownImageUri(context, safeCourseId, source)
            val selected = local ?: resolveMarkdownSourceUrl(baseUrl, source) ?: source
            "![$alt]($selected)"
        }
        resolved = RESOLVE_HTML_PATTERN.replace(resolved) { match ->
            val imageTag = match.value
            val source = normalizeMarkdownImageSource(
                SRC_ATTRIBUTE_PATTERN.find(imageTag)?.groupValues?.get(1).orEmpty()
            )
            if (source.isBlank()) {
                return@replace imageTag
            }
            val alt = ALT_ATTRIBUTE_PATTERN.find(imageTag)?.groupValues?.get(1).orEmpty()
            val local = OfflineCourseStorage.localMarkdownImageUri(context, safeCourseId, source)
            val selected = local ?: resolveMarkdownSourceUrl(baseUrl, source) ?: source
            "![$alt]($selected)"
        }
        return resolved
    }

    fun applyAltText(markdown: String, altText: String): String {
        val trimmedAlt = altText.trim()
        if (trimmedAlt.isEmpty()) {
            return markdown
        }
        val openBracket = markdown.indexOf('[')
        val closeBracket = markdown.indexOf(']')
        if (openBracket == -1 || closeBracket <= openBracket) {
            return markdown
        }
        return buildString {
            append(markdown.substring(0, openBracket + 1))
            append(trimmedAlt)
            append(markdown.substring(closeBracket))
        }
    }
}
