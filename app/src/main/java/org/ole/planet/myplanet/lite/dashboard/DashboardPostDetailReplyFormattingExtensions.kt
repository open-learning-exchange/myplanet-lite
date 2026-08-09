/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-08
 */

package org.ole.planet.myplanet.lite.dashboard

import android.text.Editable
import android.text.TextWatcher
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.MAX_HEADING_LEVEL
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.NUMBERED_LIST_REGEX
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.RESOURCES_MARKDOWN_REGEX
import kotlin.math.max
import kotlin.math.min

internal fun DashboardPostDetailActivity.applyWrappedFormattingInternal(
    prefix: String,
    suffix: String,
    placeholder: String,
    placeCursorInsideWhenNoSelection: Boolean = false,
) {
    if (!headerItem.canReply || isPostingReply) {
        return
    }
    val editable = replyInput.text ?: return
    val start = max(replyInput.selectionStart, 0)
    val end = max(replyInput.selectionEnd, 0)
    val selectionStart = min(start, end)
    val selectionEnd = max(start, end)
    val selected = editable.substring(selectionStart, selectionEnd)
    val replacement =
        if (selectionStart == selectionEnd) {
            val defaultText = placeholder
            "$prefix$defaultText$suffix"
        } else {
            "$prefix$selected$suffix"
        }
    editable.replace(selectionStart, selectionEnd, replacement)
    val newCursor =
        if (selectionStart == selectionEnd && placeCursorInsideWhenNoSelection) {
            selectionStart + prefix.length
        } else {
            selectionStart + replacement.length
        }
    val boundedCursor = min(max(newCursor, 0), editable.length)
    replyInput.setSelection(boundedCursor)
}

internal fun DashboardPostDetailActivity.applyReplyHeadingFormatting() {
    if (!headerItem.canReply || isPostingReply) {
        return
    }
    val editText = replyInput
    val editable = editText.text ?: return
    val cursor = max(0, editText.selectionStart)
    val lineStart = findReplyLineStart(editable, cursor)
    val lineLength = editable.length
    var prefixEnd = lineStart
    var currentHashes = 0
    while (prefixEnd < lineLength && editable[prefixEnd] == '#') {
        currentHashes++
        prefixEnd++
    }
    while (prefixEnd < lineLength && editable[prefixEnd].isWhitespace() && editable[prefixEnd] != '\n') {
        prefixEnd++
    }
    val newHeadingLevel =
        if (currentHashes == 0) {
            1
        } else {
            (currentHashes % MAX_HEADING_LEVEL) + 1
        }
    val replacement =
        buildString {
            repeat(newHeadingLevel) { append('#') }
            append(' ')
        }
    editable.replace(lineStart, prefixEnd, replacement)
    val selection = (lineStart + replacement.length).coerceAtMost(editable.length)
    editText.setSelection(selection)
}

internal fun DashboardPostDetailActivity.applyLinePrefix(prefix: String) {
    if (!headerItem.canReply || isPostingReply) {
        return
    }
    val editable = replyInput.text ?: return
    val selectionStart = max(replyInput.selectionStart, 0)
    val lastLineBreak = editable.lastIndexOf('\n', selectionStart - 1)
    val lineStart = if (lastLineBreak == -1) 0 else lastLineBreak + 1
    editable.insert(lineStart, prefix)
    val cursor = min(selectionStart + prefix.length, editable.length)
    replyInput.setSelection(cursor)
}

internal val DashboardPostDetailActivity.replyListContinuationWatcher: TextWatcher
    get() =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                if (isHandlingReplyListContinuation) {
                    return
                }
                if (count <= 0 || s == null) {
                    return
                }
                val inserted = s.subSequence(start, start + count)
                val newlineOffset = inserted.lastIndexOf('\n')
                if (newlineOffset >= 0) {
                    replyPendingNewlineIndex = start + newlineOffset
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (isHandlingReplyListContinuation) {
                    return
                }
                val newlineIndex = replyPendingNewlineIndex ?: return
                replyPendingNewlineIndex = null
                s ?: return
                handleReplyListContinuation(s, newlineIndex)
            }
        }

internal fun DashboardPostDetailActivity.handleReplyListContinuation(
    editable: Editable,
    newlineIndex: Int,
) {
    if (newlineIndex <= 0 || newlineIndex > editable.length) {
        return
    }
    val previousLineStart = findReplyLineStart(editable, newlineIndex)
    val previousLine = editable.subSequence(previousLineStart, newlineIndex).toString()
    if (previousLine.isEmpty()) {
        return
    }
    val indentLength = findIndentLength(previousLine)
    val indent = previousLine.substring(0, indentLength)
    val contentAfterIndent = previousLine.substring(indentLength)
    if (contentAfterIndent.isBlank()) {
        return
    }

    if (contentAfterIndent.startsWith("- ") || contentAfterIndent.startsWith("* ")) {
        val marker = contentAfterIndent.substring(0, 2)
        val hasText = contentAfterIndent.substring(2).isNotBlank()
        if (hasText) {
            insertReplyListPrefix(editable, newlineIndex, "$indent$marker")
        } else {
            removeReplyListPrefix(editable, previousLineStart + indentLength, marker.length)
        }
        return
    }

    val numberMatch = NUMBERED_LIST_REGEX.matchEntire(contentAfterIndent)
    if (numberMatch != null) {
        val number = numberMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: return
        val remainder = numberMatch.groupValues.getOrNull(2).orEmpty()
        val markerLength = numberMatch.groupValues[1].length + 2
        if (remainder.isBlank()) {
            removeReplyListPrefix(editable, previousLineStart + indentLength, markerLength)
        } else {
            val nextMarker = "$indent${number + 1}. "
            insertReplyListPrefix(editable, newlineIndex, nextMarker)
        }
    }
}

internal fun DashboardPostDetailActivity.insertReplyListPrefix(
    editable: Editable,
    newlineIndex: Int,
    prefix: String,
) {
    val insertPosition = (newlineIndex + 1).coerceAtMost(editable.length)
    isHandlingReplyListContinuation = true
    editable.insert(insertPosition, prefix)
    replyInput.setSelection((insertPosition + prefix.length).coerceAtMost(editable.length))
    isHandlingReplyListContinuation = false
}

internal fun DashboardPostDetailActivity.removeReplyListPrefix(
    editable: Editable,
    start: Int,
    markerLength: Int,
) {
    val end = (start + markerLength).coerceAtMost(editable.length)
    isHandlingReplyListContinuation = true
    editable.delete(start, end)
    replyInput.setSelection(start.coerceAtMost(editable.length))
    isHandlingReplyListContinuation = false
}

internal fun DashboardPostDetailActivity.findReplyLineStart(
    editable: Editable,
    index: Int,
): Int {
    val boundedIndex = index.coerceIn(0, editable.length)
    for (i in boundedIndex - 1 downTo 0) {
        if (editable[i] == '\n') {
            return i + 1
        }
    }
    return 0
}

internal fun DashboardPostDetailActivity.findIndentLength(line: String): Int {
    for (i in line.indices) {
        if (!line[i].isWhitespace()) {
            return i
        }
    }
    return line.length
}

internal fun DashboardPostDetailActivity.transformReplyMarkdownForPreview(markdown: String): String {
    var processed = markdown.replace("\n", "  \n")
    val base = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

    if (!base.isNullOrEmpty()) {
        processed =
            RESOURCES_MARKDOWN_REGEX.replace(processed) { matchResult ->
                val path =
                    matchResult.groupValues
                        .getOrNull(1)
                        ?.trim()
                        .orEmpty()
                val absolute = "$base/db/$path"
                "![]($absolute)"
            }
    }

    val pendingByFileName = pendingReplyImages.values.associateBy { it.fileName }
    if (pendingByFileName.isNotEmpty()) {
        processed =
            DashboardPostDetailActivity.GLOBAL_PATTERN.replace(processed) { matchResult ->
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
    return processed
}
