/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-07
 */

package org.ole.planet.myplanet.lite.dashboard

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.ole.planet.myplanet.lite.R

internal val CreateVoiceActivity.listContinuationWatcher: TextWatcher
    get() = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
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
    placeCursorInsideWhenNoSelection: Boolean = false
) {
    val editText = createVoiceInput
    val editable = editText.text ?: return
    val start = max(0, editText.selectionStart)
    val end = max(0, editText.selectionEnd)
    val rangeStart = min(start, end)
    val rangeEnd = max(start, end)
    val hasSelection = rangeStart != rangeEnd
    val selected = if (hasSelection) {
        editable.subSequence(rangeStart, rangeEnd).toString()
    } else {
        placeholder
    }
    val replacement = "$prefix$selected$suffix"
    editable.replace(rangeStart, rangeEnd, replacement)
    val cursorPosition = when {
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
    val newHeadingLevel = if (currentHashes == 0) {
        1
    } else {
        (currentHashes % CreateVoiceActivity.MAX_HEADING_LEVEL) + 1
    }
    val replacement = buildString {
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
    val formatted = selection.lines().joinToString("\n") { line ->
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
    val formatted = selection.lines().mapIndexed { index, line ->
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
    val formatted = selection.lines().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        val indent = line.substring(0, line.length - trimmed.length)
        val content = trimmed.ifEmpty { placeholder }
        "$indent> $content"
    }
    editable.replace(rangeStart, rangeEnd, formatted)
    editText.setSelection((rangeStart + formatted.length).coerceAtMost(editable.length))
}

internal fun CreateVoiceActivity.applyLinkFormatting() {
    val editText = createVoiceInput
    val editable = editText.text ?: return
    val start = max(0, editText.selectionStart)
    val end = max(0, editText.selectionEnd)
    val rangeStart = min(start, end)
    val rangeEnd = max(start, end)
    val selected = editable.subSequence(rangeStart, rangeEnd).toString()
        .takeIf { it.isNotBlank() }
        ?: ""
    showInsertLinkDialog(rangeStart, rangeEnd, selected)
}

internal fun CreateVoiceActivity.showInsertLinkDialog(rangeStart: Int, rangeEnd: Int, selectedTitle: String) {
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val spacing = (12 * resources.displayMetrics.density).roundToInt()
        setPadding(spacing, spacing, spacing, spacing)
    }

    val titleInputLayout = TextInputLayout(this).apply {
        hint = getString(R.string.create_voice_link_title_hint)
    }
    val titleInput = TextInputEditText(this).apply {
        setText(selectedTitle)
        setSelection(text?.length ?: 0)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }
    titleInputLayout.addView(
        titleInput,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    val urlInputLayout = TextInputLayout(this).apply {
        hint = getString(R.string.create_voice_link_url_hint)
    }
    val urlInput = TextInputEditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    }
    urlInputLayout.addView(
        urlInput,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    container.addView(titleInputLayout)
    container.addView(urlInputLayout)

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(R.string.create_voice_link_dialog_title)
        .setView(container)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.create_voice_link_insert_button, null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val linkTitle = titleInput.text?.toString()?.trim().orEmpty()
            val linkUrl = urlInput.text?.toString()?.trim().orEmpty()
            if (linkTitle.isBlank() || linkUrl.isBlank()) {
                Toast.makeText(
                    this,
                    R.string.create_voice_link_required_fields,
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            insertLinkMarkdown(rangeStart, rangeEnd, linkTitle, linkUrl)
            dialog.dismiss()
        }
    }

    dialog.show()
}

internal fun CreateVoiceActivity.insertLinkMarkdown(rangeStart: Int, rangeEnd: Int, title: String, url: String) {
    val editText = createVoiceInput
    val editable = editText.text ?: return
    val safeStart = rangeStart.coerceIn(0, editable.length)
    val safeEnd = rangeEnd.coerceIn(safeStart, editable.length)
    val replacement = "[$title]($url)"
    editable.replace(safeStart, safeEnd, replacement)
    val cursorPosition = (safeStart + replacement.length).coerceAtMost(editable.length)
    editText.setSelection(cursorPosition)
}

internal fun CreateVoiceActivity.handleListContinuation(editable: Editable, newlineIndex: Int) {
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

internal fun CreateVoiceActivity.insertListPrefix(editable: Editable, newlineIndex: Int, prefix: String) {
    val insertPosition = (newlineIndex + 1).coerceAtMost(editable.length)
    isHandlingListContinuation = true
    editable.insert(insertPosition, prefix)
    createVoiceInput.setSelection((insertPosition + prefix.length).coerceAtMost(editable.length))
    isHandlingListContinuation = false
}

internal fun CreateVoiceActivity.removeListPrefix(editable: Editable, start: Int, markerLength: Int) {
    val end = (start + markerLength).coerceAtMost(editable.length)
    isHandlingListContinuation = true
    editable.delete(start, end)
    createVoiceInput.setSelection(start.coerceAtMost(editable.length))
    isHandlingListContinuation = false
}

internal fun CreateVoiceActivity.findIndentLength(line: String): Int {
    val index = line.indexOfFirst { !it.isWhitespace() }
    return if (index == -1) line.length else index
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