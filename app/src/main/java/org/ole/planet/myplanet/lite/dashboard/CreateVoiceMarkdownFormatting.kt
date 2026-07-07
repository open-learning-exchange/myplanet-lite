/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-07
 */

package org.ole.planet.myplanet.lite.dashboard

import android.text.Editable
import com.google.android.material.button.MaterialButton
import org.ole.planet.myplanet.lite.R
import kotlin.math.max
import kotlin.math.min

internal fun CreateVoiceActivity.setupMarkdownToolbar() {
    markdownToolbar = findViewById(R.id.markdownToolbar)
    val boldButton: MaterialButton = findViewById(R.id.markdownBoldButton)
    val italicButton: MaterialButton = findViewById(R.id.markdownItalicButton)
    val headingButton: MaterialButton = findViewById(R.id.markdownHeadingButton)
    val bulletButton: MaterialButton = findViewById(R.id.markdownBulletButton)
    val numberedButton: MaterialButton = findViewById(R.id.markdownNumberedButton)
    val quoteButton: MaterialButton = findViewById(R.id.markdownQuoteButton)
    val linkButton: MaterialButton = findViewById(R.id.markdownLinkButton)
    val imageButton: MaterialButton = findViewById(R.id.markdownImageButton)

    boldButton.setOnClickListener {
        applyWrappedFormatting("**", "**", "", placeCursorInsideWhenNoSelection = true)
    }
    italicButton.setOnClickListener {
        applyWrappedFormatting("*", "*", "", placeCursorInsideWhenNoSelection = true)
    }
    headingButton.setOnClickListener {
        applyHeadingFormatting()
    }
    bulletButton.setOnClickListener {
        applyBulletFormatting()
    }
    numberedButton.setOnClickListener {
        applyNumberedListFormatting()
    }
    quoteButton.setOnClickListener {
        applyQuoteFormatting()
    }
    linkButton.setOnClickListener {
        applyLinkFormatting()
    }
    imageButton.setOnClickListener {
        handleInsertImageClick()
    }
}

internal fun CreateVoiceActivity.applyWrappedFormatting(
    prefix: String,
    suffix: String,
    placeholder: String,
    placeCursorInsideWhenNoSelection: Boolean = false,
) {
    val editText = createVoiceInput
    val editable = editText.text ?: return
    val start = max(0, editText.selectionStart)
    val end = max(0, editText.selectionEnd)
    val rangeStart = min(start, end)
    val rangeEnd = max(start, end)
    val hasSelection = rangeStart != rangeEnd
    val selected =
        if (hasSelection) {
            editable.subSequence(rangeStart, rangeEnd).toString()
        } else {
            placeholder
        }
    val replacement = "$prefix$selected$suffix"
    editable.replace(rangeStart, rangeEnd, replacement)
    val cursorPosition =
        when {
            hasSelection -> rangeStart + replacement.length
            placeCursorInsideWhenNoSelection -> rangeStart + prefix.length
            else -> rangeStart + prefix.length + selected.length
        }
    editText.setSelection(cursorPosition.coerceIn(0, editable.length))
}

internal fun CreateVoiceActivity.applyHeadingFormatting() {
    val editText = createVoiceInput
    val editable = editText.text ?: return
    val cursor = max(0, editText.selectionStart)
    val lineStart = findLineStart(editable, cursor)
    val lineLength = editable.length
    var prefixEnd = lineStart
    var currentHashes = 0
    while (prefixEnd < lineLength && editable[prefixEnd] == '#') {
        currentHashes++
        prefixEnd++
    }
    while (
        prefixEnd < lineLength &&
        editable[prefixEnd] == ' ' &&
        editable[prefixEnd] != '\n'
    ) {
        prefixEnd++
    }
    val newHeadingLevel =
        if (currentHashes == 0) {
            1
        } else {
            (currentHashes % CreateVoiceActivity.MAX_HEADING_LEVEL) + 1
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

internal fun CreateVoiceActivity.applyBulletFormatting() {
    val editText = createVoiceInput
    val editable = editText.text ?: return
    val start = max(0, editText.selectionStart)
    val end = max(0, editText.selectionEnd)
    val rangeStart = min(start, end)
    val rangeEnd = max(start, end)
    val selection = editable.subSequence(rangeStart, rangeEnd).toString()
    if (selection.isBlank()) {
        editable.replace(rangeStart, rangeEnd, "- ")
        editText.setSelection((rangeStart + 2).coerceAtMost(editable.length))
        return
    }
    val formatted =
        selection.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            val indent = line.substring(0, line.length - trimmed.length)
            "$indent- ${trimmed.ifEmpty { getString(R.string.create_voice_format_placeholder) }}"
        }
    editable.replace(rangeStart, rangeEnd, formatted)
    editText.setSelection((rangeStart + formatted.length).coerceAtMost(editable.length))
}

internal fun CreateVoiceActivity.applyNumberedListFormatting() {
    val editText = createVoiceInput
    val editable = editText.text ?: return
    val start = max(0, editText.selectionStart)
    val end = max(0, editText.selectionEnd)
    val rangeStart = min(start, end)
    val rangeEnd = max(start, end)
    val selection = editable.subSequence(rangeStart, rangeEnd).toString()
    if (selection.isBlank()) {
        editable.replace(rangeStart, rangeEnd, "1. ")
        editText.setSelection((rangeStart + 3).coerceAtMost(editable.length))
        return
    }
    val placeholder = getString(R.string.create_voice_format_placeholder)
    val formatted =
        selection
            .lines()
            .mapIndexed { index, line ->
                val trimmed = line.trimStart()
                val indent = line.substring(0, line.length - trimmed.length)
                val content = trimmed.ifEmpty { placeholder }
                "$indent${index + 1}. $content"
            }.joinToString("\n")
    editable.replace(rangeStart, rangeEnd, formatted)
    editText.setSelection((rangeStart + formatted.length).coerceAtMost(editable.length))
}

internal fun CreateVoiceActivity.applyQuoteFormatting() {
    val editText = createVoiceInput
    val editable = editText.text ?: return
    val start = max(0, editText.selectionStart)
    val end = max(0, editText.selectionEnd)
    val rangeStart = min(start, end)
    val rangeEnd = max(start, end)
    val selection = editable.subSequence(rangeStart, rangeEnd).toString()
    if (selection.isBlank()) {
        editable.replace(rangeStart, rangeEnd, "> ")
        editText.setSelection((rangeStart + 2).coerceAtMost(editable.length))
        return
    }
    val placeholder = getString(R.string.create_voice_format_placeholder)
    val formatted =
        selection.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            val indent = line.substring(0, line.length - trimmed.length)
            val content = trimmed.ifEmpty { placeholder }
            "$indent> $content"
        }
    editable.replace(rangeStart, rangeEnd, formatted)
    editText.setSelection((rangeStart + formatted.length).coerceAtMost(editable.length))
}

internal fun CreateVoiceActivity.findLineStart(editable: Editable, position: Int): Int {
    var index = position - 1
    while (index >= 0) {
        if (editable[index] == '\n') {
            return index + 1
        }
        index--
    }
    return 0
}

internal fun CreateVoiceActivity.setMarkdownToolbarEnabled(enabled: Boolean) {
    markdownToolbar.isEnabled = enabled
    for (index in 0 until markdownToolbar.childCount) {
        markdownToolbar.getChildAt(index)?.isEnabled = enabled
    }
}
