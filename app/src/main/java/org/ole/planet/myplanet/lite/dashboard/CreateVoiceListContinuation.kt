/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-07
 */

package org.ole.planet.myplanet.lite.dashboard

import android.text.Editable
import android.text.TextWatcher

internal val CreateVoiceActivity.listContinuationWatcher: TextWatcher
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
                if (isHandlingListContinuation) {
                    return
                }
                if (count <= 0 || s == null) {
                    return
                }
                val inserted = s.subSequence(start, start + count)
                val newlineOffset = inserted.lastIndexOf('\n')
                if (newlineOffset >= 0) {
                    pendingNewlineIndex = start + newlineOffset
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (isHandlingListContinuation) {
                    return
                }
                val newlineIndex = pendingNewlineIndex ?: return
                pendingNewlineIndex = null
                s ?: return
                handleListContinuation(s, newlineIndex)
            }
        }

internal fun CreateVoiceActivity.handleListContinuation(
    editable: Editable,
    newlineIndex: Int,
) {
    if (newlineIndex <= 0 || newlineIndex > editable.length) {
        return
    }
    val previousLineStart = findLineStart(editable, newlineIndex)
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
            insertListPrefix(editable, newlineIndex, "$indent$marker")
        } else {
            removeListPrefix(editable, previousLineStart + indentLength, marker.length)
        }
        return
    }

    val numberMatch = CreateVoiceActivity.NUMBERED_LIST_REGEX.matchEntire(contentAfterIndent)
    if (numberMatch != null) {
        val number = numberMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: return
        val remainder = numberMatch.groupValues.getOrNull(2).orEmpty()
        val markerLength = numberMatch.groupValues[1].length + 2
        if (remainder.isBlank()) {
            removeListPrefix(editable, previousLineStart + indentLength, markerLength)
        } else {
            val nextMarker = "$indent${number + 1}. "
            insertListPrefix(editable, newlineIndex, nextMarker)
        }
    }
}

internal fun CreateVoiceActivity.insertListPrefix(
    editable: Editable,
    newlineIndex: Int,
    prefix: String,
) {
    val insertPosition = (newlineIndex + 1).coerceAtMost(editable.length)
    isHandlingListContinuation = true
    editable.insert(insertPosition, prefix)
    createVoiceInput.setSelection((insertPosition + prefix.length).coerceAtMost(editable.length))
    isHandlingListContinuation = false
}

internal fun CreateVoiceActivity.removeListPrefix(
    editable: Editable,
    start: Int,
    markerLength: Int,
) {
    val end = (start + markerLength).coerceAtMost(editable.length)
    isHandlingListContinuation = true
    editable.delete(start, end)
    createVoiceInput.setSelection(start.coerceAtMost(editable.length))
    isHandlingListContinuation = false
}

internal fun findIndentLength(line: String): Int {
    val index = line.indexOfFirst { !it.isWhitespace() }
    return if (index == -1) line.length else index
}
