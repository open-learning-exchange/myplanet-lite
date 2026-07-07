/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-07
 */

package org.ole.planet.myplanet.lite.dashboard

import android.text.InputType
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.ole.planet.myplanet.lite.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun CreateVoiceActivity.applyLinkFormatting() {
    val editText = createVoiceInput
    val editable = editText.text ?: return
    val start = max(0, editText.selectionStart)
    val end = max(0, editText.selectionEnd)
    val rangeStart = min(start, end)
    val rangeEnd = max(start, end)
    val selected =
        editable
            .subSequence(rangeStart, rangeEnd)
            .toString()
            .takeIf { it.isNotBlank() }
            ?: ""
    showInsertLinkDialog(rangeStart, rangeEnd, selected)
}

internal fun CreateVoiceActivity.showInsertLinkDialog(
    rangeStart: Int,
    rangeEnd: Int,
    selectedTitle: String,
) {
    val container =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val spacing = (12 * resources.displayMetrics.density).roundToInt()
            setPadding(spacing, spacing, spacing, spacing)
        }

    val titleInputLayout =
        TextInputLayout(this).apply {
            hint = getString(R.string.create_voice_link_title_hint)
        }
    val titleInput =
        TextInputEditText(this).apply {
            setText(selectedTitle)
            setSelection(text?.length ?: 0)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
    titleInputLayout.addView(
        titleInput,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    val urlInputLayout =
        TextInputLayout(this).apply {
            hint = getString(R.string.create_voice_link_url_hint)
        }
    val urlInput =
        TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
    urlInputLayout.addView(
        urlInput,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    container.addView(titleInputLayout)
    container.addView(urlInputLayout)

    val dialog =
        MaterialAlertDialogBuilder(this)
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
                Toast
                    .makeText(
                        this,
                        R.string.create_voice_link_required_fields,
                        Toast.LENGTH_SHORT,
                    ).show()
                return@setOnClickListener
            }
            insertLinkMarkdown(rangeStart, rangeEnd, linkTitle, linkUrl)
            dialog.dismiss()
        }
    }

    dialog.show()
}

internal fun CreateVoiceActivity.insertLinkMarkdown(
    rangeStart: Int,
    rangeEnd: Int,
    title: String,
    url: String,
) {
    val editText = createVoiceInput
    val editable = editText.text ?: return
    val safeStart = rangeStart.coerceIn(0, editable.length)
    val safeEnd = rangeEnd.coerceIn(safeStart, editable.length)
    val replacement = "[$title]($url)"
    editable.replace(safeStart, safeEnd, replacement)
    val cursorPosition = (safeStart + replacement.length).coerceAtMost(editable.length)
    editText.setSelection(cursorPosition)
}
