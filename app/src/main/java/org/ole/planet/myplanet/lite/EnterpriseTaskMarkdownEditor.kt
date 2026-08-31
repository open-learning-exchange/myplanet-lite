package org.ole.planet.myplanet.lite

import android.text.InputType
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.math.max
import kotlin.math.min
import org.ole.planet.myplanet.lite.dashboard.VoiceMarkdownFormatter
import org.ole.planet.myplanet.lite.databinding.DialogEnterpriseTaskBinding

internal class EnterpriseTaskMarkdownEditor(
    private val binding: DialogEnterpriseTaskBinding,
) {
    private val input get() = binding.enterpriseTaskDialogDescription

    fun bind() {
        binding.enterpriseTaskMarkdownBold.setOnClickListener { wrap("**", "**") }
        binding.enterpriseTaskMarkdownItalic.setOnClickListener { wrap("*", "*") }
        binding.enterpriseTaskMarkdownHeading.setOnClickListener { heading() }
        binding.enterpriseTaskMarkdownBullet.setOnClickListener { lines("- ") }
        binding.enterpriseTaskMarkdownNumbered.setOnClickListener { numberedLines() }
        binding.enterpriseTaskMarkdownQuote.setOnClickListener { lines("> ") }
        binding.enterpriseTaskMarkdownLink.setOnClickListener { link() }
        binding.enterpriseTaskMarkdownChecklist.setOnClickListener { lines("- [ ] ") }
    }

    private fun selection(): IntRange {
        val start = max(0, input.selectionStart)
        val end = max(0, input.selectionEnd)
        return min(start, end)..max(start, end)
    }

    private fun wrap(prefix: String, suffix: String) {
        val editable = input.text ?: return
        val range = selection()
        val selected = editable.subSequence(range.first, range.last).toString()
        val replacement = "$prefix$selected$suffix"
        editable.replace(range.first, range.last, replacement)
        val cursor = if (selected.isEmpty()) range.first + prefix.length else range.first + replacement.length
        input.setSelection(cursor.coerceAtMost(editable.length))
    }

    private fun heading() {
        val editable = input.text ?: return
        val cursor = max(0, input.selectionStart)
        var lineStart = cursor
        while (lineStart > 0 && editable[lineStart - 1] != '\n') lineStart--
        var prefixEnd = lineStart
        var hashes = 0
        while (prefixEnd < editable.length && editable[prefixEnd] == '#') {
            hashes++
            prefixEnd++
        }
        while (prefixEnd < editable.length && editable[prefixEnd] == ' ') prefixEnd++
        val replacement = VoiceMarkdownFormatter.getHeadingReplacement(hashes)
        editable.replace(lineStart, prefixEnd, replacement)
        input.setSelection((lineStart + replacement.length).coerceAtMost(editable.length))
    }

    private fun lines(prefix: String) {
        replaceSelectedLines { _, content -> "$prefix$content" }
    }

    private fun numberedLines() {
        replaceSelectedLines { index, content -> "${index + 1}. $content" }
    }

    private fun replaceSelectedLines(transform: (Int, String) -> String) {
        val editable = input.text ?: return
        val range = selection()
        val selected = editable.subSequence(range.first, range.last).toString()
        val replacement = if (selected.isEmpty()) transform(0, "") else selected.lines().mapIndexed(transform).joinToString("\n")
        editable.replace(range.first, range.last, replacement)
        input.setSelection((range.first + replacement.length).coerceAtMost(editable.length))
    }

    private fun link() {
        val editable = input.text ?: return
        val range = selection()
        val selected = editable.subSequence(range.first, range.last).toString()
        val context = input.context
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val spacing = (12 * resources.displayMetrics.density).toInt()
            setPadding(spacing, spacing, spacing, spacing)
        }
        val titleLayout = TextInputLayout(context).apply { hint = context.getString(R.string.create_voice_link_title_hint) }
        val titleInput = TextInputEditText(context).apply { setText(selected); setSelection(text?.length ?: 0) }
        titleLayout.addView(titleInput)
        val urlLayout = TextInputLayout(context).apply { hint = context.getString(R.string.create_voice_link_url_hint) }
        val urlInput = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        urlLayout.addView(urlInput)
        container.addView(titleLayout)
        container.addView(urlLayout)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.create_voice_link_dialog_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.create_voice_link_insert_button, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val url = urlInput.text?.toString()?.trim().orEmpty()
                if (title.isBlank() || url.isBlank()) {
                    Toast.makeText(context, R.string.create_voice_link_required_fields, Toast.LENGTH_SHORT).show()
                } else {
                    val safeStart = range.first.coerceIn(0, editable.length)
                    val safeEnd = range.last.coerceIn(safeStart, editable.length)
                    val markdown = "[$title]($url)"
                    editable.replace(safeStart, safeEnd, markdown)
                    input.setSelection((safeStart + markdown.length).coerceAtMost(editable.length))
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
