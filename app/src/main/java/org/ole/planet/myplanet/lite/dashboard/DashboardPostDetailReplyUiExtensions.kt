/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-08
 */

package org.ole.planet.myplanet.lite.dashboard

import android.net.Uri
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.COLLAPSED_REPLY_MIN_LINES
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity.Companion.EXPANDED_REPLY_MIN_LINES
import org.ole.planet.myplanet.lite.util.ApplicationScope
import org.ole.planet.myplanet.lite.util.FileUtils
import kotlin.math.max

internal fun DashboardPostDetailActivity.setupBackNavigation() {
    backCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (collapseReplyComposerIfExpanded()) {
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    onBackPressedDispatcher.addCallback(this, backCallback)
}

internal fun DashboardPostDetailActivity.setupReplyComposer() {
    replyContainer = findViewById(R.id.postDetailReplyContainer)
    replyInputLayout = findViewById(R.id.dashboardReplyInputLayout)
    replyInput = findViewById(R.id.dashboardReplyInput)
    replyExpandedContent = findViewById(R.id.dashboardReplyExpandedContent)
    replyPreviewLabel = findViewById(R.id.dashboardReplyPreviewLabel)
    replyPreview = findViewById(R.id.dashboardReplyPreview)
    replyPreviewContainer = findViewById(R.id.dashboardReplyPreviewContainer)
    replyPreviewImagesRow = findViewById(R.id.dashboardReplyPreviewImagesRow)
    replyPreviewImages = findViewById(R.id.dashboardReplyPreviewImages)
    replySendButton = findViewById(R.id.dashboardReplySendButton)
    replyActionsRow = findViewById(R.id.dashboardReplyActions)
    replyMarkdownToolbar = findViewById(R.id.dashboardReplyMarkdownToolbar)
    replyingToLabel = findViewById(R.id.postDetailReplyingTo)

    setupReplyWindowInsets()
    setupReplyInputListeners()
    setupReplyMarkdownToolbar()

    updateReplyPreview(replyPreview, "")
    setMarkdownToolbarEnabled(false)
    replySendButton.isEnabled = false
}

internal fun DashboardPostDetailActivity.setupReplyWindowInsets() {
    val baseReplyContainerPaddingBottom = replyContainer.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(replyContainer) { view, insets ->
        val systemBarsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val bottomInset = max(systemBarsBottom, imeBottom)
        view.setPadding(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            baseReplyContainerPaddingBottom + bottomInset,
        )
        insets
    }
    ViewCompat.requestApplyInsets(replyContainer)
}

internal fun DashboardPostDetailActivity.setupReplyInputListeners() {
    replyInputLayout.helperText = null
    replyInput.doAfterTextChanged { text ->
        updateReplyPreview(replyPreview, text?.toString())
        updateReplyActionAvailabilityInternal(text)
    }
    replyInput.addTextChangedListener(replyListContinuationWatcher)
    replyInput.setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            expandReplyComposer()
        }
    }
    replyInput.setOnTouchListener { v, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            v.parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            v.parent?.requestDisallowInterceptTouchEvent(false)
        }
        false
    }
    replyInput.isVerticalScrollBarEnabled = true
    replyInput.setOnClickListener {
        expandReplyComposer()
    }
    replySendButton.setOnClickListener {
        val message =
            replyInput.text
                ?.toString()
                ?.trim()
                .orEmpty()
        if (isEditingComment) {
            attemptUpdateComment(message)
        } else {
            attemptReply(message)
        }
    }
}

internal fun DashboardPostDetailActivity.setupReplyMarkdownToolbar() {
    val replyBold: MaterialButton = findViewById(R.id.dashboardReplyMarkdownBold)
    val replyItalic: MaterialButton = findViewById(R.id.dashboardReplyMarkdownItalic)
    val replyHeading: MaterialButton = findViewById(R.id.dashboardReplyMarkdownHeading)
    val replyBullet: MaterialButton = findViewById(R.id.dashboardReplyMarkdownBullet)
    val replyNumbered: MaterialButton = findViewById(R.id.dashboardReplyMarkdownNumbered)
    val replyQuote: MaterialButton = findViewById(R.id.dashboardReplyMarkdownQuote)
    val replyLink: MaterialButton = findViewById(R.id.dashboardReplyMarkdownLink)
    val replyImage: MaterialButton = findViewById(R.id.dashboardReplyMarkdownImage)

    replyBold.setOnClickListener {
        applyWrappedFormattingInternal("**", "**", "", placeCursorInsideWhenNoSelection = true)
    }
    replyItalic.setOnClickListener {
        applyWrappedFormattingInternal("*", "*", "", placeCursorInsideWhenNoSelection = true)
    }
    replyHeading.setOnClickListener { applyReplyHeadingFormatting() }
    replyBullet.setOnClickListener { applyLinePrefix("- ") }
    replyNumbered.setOnClickListener { applyLinePrefix("1. ") }
    replyQuote.setOnClickListener { applyLinePrefix("> ") }
    replyLink.setOnClickListener {
        applyWrappedFormattingInternal("[", "](https://)", "", true)
    }
    replyImage.setOnClickListener {
        handleReplyInsertImageClick()
    }
}

internal fun DashboardPostDetailActivity.promptReply() {
    if (!headerItem.canReply || isPostingReply) {
        return
    }
    exitCommentEditMode()
    expandReplyComposer()
    replyContainer.isVisible = true
    replyInput.requestFocus()
    replyInput.post {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(replyInput, 0)
    }
}

internal fun DashboardPostDetailActivity.startEditingComment(comment: PostDetailItem.Comment) {
    if (isPostingReply) {
        return
    }
    val doc = comment.document
    if (doc == null) {
        Toast.makeText(this, R.string.dashboard_comment_edit_error, Toast.LENGTH_SHORT).show()
        return
    }
    isEditingComment = true
    editingCommentDocument = doc
    replyInput.hint = getString(R.string.dashboard_comment_edit_hint)
    replyInputLayout.helperText = getString(R.string.dashboard_comment_edit_helper)
    replySendButton.setText(R.string.dashboard_comment_edit_send)
    replyInput.setText(comment.message.orEmpty())
    replyInput.setSelection(replyInput.text?.length ?: 0)
    clearPendingReplyImages()
    lifecycleScope.launch {
        loadExistingCommentImages(comment)
    }
    expandReplyComposer()
    replyContainer.isVisible = true
    replyInput.requestFocus()
    replyInput.post {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(replyInput, 0)
    }
    updateReplyPreview(replyPreview, replyInput.text?.toString())
}

internal fun DashboardPostDetailActivity.exitCommentEditMode(clearFields: Boolean = false) {
    if (!isEditingComment) {
        if (clearFields) {
            replyInput.setText("")
            clearPendingReplyImages()
            updateReplyPreview(replyPreview, "")
        }
        return
    }
    isEditingComment = false
    editingCommentDocument = null
    replyInput.hint = getString(R.string.dashboard_post_reply_hint)
    replyInputLayout.helperText = null
    replySendButton.setText(R.string.dashboard_post_reply_send)
    if (clearFields) {
        replyInput.setText("")
        clearPendingReplyImages()
        updateReplyPreview(replyPreview, "")
    }
}

internal fun DashboardPostDetailActivity.updateReplyPreview(
    preview: TextView,
    message: String?,
) {
    val content = message?.takeIf { it.isNotBlank() }
    val previewText = content ?: getString(R.string.dashboard_post_reply_preview_placeholder)
    val transformed = transformReplyMarkdownForPreview(previewText)
    markwon.setMarkdown(preview, transformed)
    updateReplyPreviewImages()
}

internal fun DashboardPostDetailActivity.updateReplyPreviewImages() {
    val images = pendingReplyImages.values.toList()
    if (!isReplyComposerExpanded || images.isEmpty()) {
        replyPreviewImages.removeAllViews()
        replyPreviewImagesRow.isVisible = false
        return
    }

    replyPreviewImages.removeAllViews()
    val size = resources.getDimensionPixelSize(R.dimen.dashboard_reply_preview_image_size)
    val spacing = resources.getDimensionPixelSize(R.dimen.dashboard_reply_preview_image_spacing)
    images.forEachIndexed { index, pending ->
        val imageView = AppCompatImageView(this)
        val params = LinearLayout.LayoutParams(size, size)
        if (index < images.lastIndex) {
            params.marginEnd = spacing
        }
        imageView.layoutParams = params
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.clipToOutline = true
        imageView.setImageURI(Uri.fromFile(pending.file))
        replyPreviewImages.addView(imageView)
    }
    replyPreviewImagesRow.isVisible = true
}

internal fun DashboardPostDetailActivity.updateReplyComposerVisibility() {
    val canReply = headerItem.canReply
    replyInputLayout.isEnabled = canReply && !isPostingReply
    applyReplyExpansionState()
    updateReplyActionAvailabilityInternal(replyInput.text)
}

internal fun DashboardPostDetailActivity.updateReplyActionAvailabilityInternal(text: CharSequence?) {
    val hasContent = !text.isNullOrBlank() || pendingReplyImages.isNotEmpty()
    val canSend = (headerItem.canReply || isEditingComment) && !isPostingReply && hasContent && isReplyComposerExpanded
    replySendButton.isEnabled = canSend
}

internal fun DashboardPostDetailActivity.clearPendingReplyImages() {
    val filesToDelete = pendingReplyImages.values.map { it.file }.toList()
    pendingReplyImages.clear()

    ApplicationScope.io.launch {
        FileUtils.deleteFiles(filesToDelete)
    }
    updateReplyPreviewImages()
    updateReplyActionAvailabilityInternal(replyInput.text)
}

internal fun DashboardPostDetailActivity.setReplyPosting(posting: Boolean) {
    isPostingReply = posting
    updateReplyComposerVisibility()
}

internal fun DashboardPostDetailActivity.setMarkdownToolbarEnabled(enabled: Boolean) {
    replyMarkdownToolbar.isEnabled = enabled
    for (index in 0 until replyMarkdownToolbar.childCount) {
        replyMarkdownToolbar.getChildAt(index)?.isEnabled = enabled
    }
}

internal fun DashboardPostDetailActivity.handleReplyInsertImageClick() {
    launchReplyImagePicker()
}

internal fun DashboardPostDetailActivity.launchReplyImagePicker() {
    replyImagePickerLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )
}

internal fun DashboardPostDetailActivity.collapseReplyComposerIfExpanded(): Boolean {
    if (!isReplyComposerExpanded) {
        return false
    }
    isReplyComposerExpanded = false
    exitCommentEditMode(clearFields = true)
    hideReplyKeyboard()
    replyInput.clearFocus()
    applyReplyExpansionState()
    updateReplyActionAvailabilityInternal(replyInput.text)
    return true
}

internal fun DashboardPostDetailActivity.expandReplyComposer() {
    if (isReplyComposerExpanded) {
        return
    }
    isReplyComposerExpanded = true
    applyReplyExpansionState()
}

internal fun DashboardPostDetailActivity.applyReplyExpansionState() {
    val canReply = headerItem.canReply
    val expanded = canReply && isReplyComposerExpanded
    replyContainer.isVisible = canReply
    val minLines = if (expanded) EXPANDED_REPLY_MIN_LINES else COLLAPSED_REPLY_MIN_LINES
    replyInput.setMinLines(minLines)
    replyExpandedContent.isVisible = expanded
    replyMarkdownToolbar.isVisible = expanded
    replyPreviewContainer.isVisible = expanded
    replyPreviewLabel.isVisible = expanded
    replyPreview.isVisible = expanded
    replyActionsRow.isVisible = expanded
    updateReplyPreviewImages()
    replyInputLayout.helperText = null
    setMarkdownToolbarEnabled(expanded && !isPostingReply)
    updateReplyingToVisibility(expanded)
}

internal fun DashboardPostDetailActivity.hideReplyKeyboard() {
    val imm = getSystemService(InputMethodManager::class.java)
    imm?.hideSoftInputFromWindow(replyInput.windowToken, 0)
}

internal fun DashboardPostDetailActivity.updateReplyingToLabel(username: String?) {
    replyContextHandle = username?.takeIf { it.isNotBlank() }
    updateReplyingToVisibility(isReplyComposerExpanded && headerItem.canReply)
}

internal fun DashboardPostDetailActivity.updateReplyingToVisibility(expanded: Boolean) {
    val handle = replyContextHandle
    if (expanded && handle != null) {
        replyingToLabel.text = getString(R.string.dashboard_post_replying_to, handle)
        replyingToLabel.isVisible = true
    } else {
        replyingToLabel.isVisible = false
    }
}
