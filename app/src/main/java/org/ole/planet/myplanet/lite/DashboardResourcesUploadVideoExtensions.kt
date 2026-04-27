package org.ole.planet.myplanet.lite

import android.app.AlertDialog
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore


@OptIn(UnstableApi::class)
internal fun DashboardResourcesPageFragment.showVideoMetadataPopup(uri: Uri) {
    VideoUploadPopupHelper(this, uri).show()
}




@OptIn(UnstableApi::class)
private class VideoUploadPopupHelper(
    private val fragment: DashboardResourcesPageFragment,
    private val uri: Uri
) {
    private val ctx = fragment.requireContext()
    private val resources = ctx.resources

    private var sourceWidth = 0
    private var sourceHeight = 0
    private var sourceDurationMs = 0L
    private var sourceFileSizeBytes = 0L

    private var allowedHeights = emptyList<Int>()
    private var defaultHeight = 0
    private var defaultIndex = 0

    private var selectedStartMs = 0L
    private var selectedEndMs = 0L
    private var selectedHeightForEstimate = 0
    private var selectedRotationDegrees = 0f
    private var isUploaded = false

    private lateinit var titleInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var languageSpinner: Spinner
    private lateinit var isDownloadableCheck: CheckBox

    private lateinit var segmentStartValue: TextView
    private lateinit var segmentEndValue: TextView
    private lateinit var segmentTotalValue: TextView
    private lateinit var sizeEstimateValue: TextView
    private lateinit var resolutionValue: TextView

    private lateinit var previewPlayer: ExoPlayer
    private lateinit var previewContainer: FrameLayout
    private lateinit var previewVideoView: PlayerView
    private lateinit var previewToggleButton: ImageButton
    private val previewHandler = Handler(Looper.getMainLooper())
    private lateinit var previewMonitor: Runnable

    fun show() {
        val fileName = DashboardResourcesMediaUtils.resolveFileName(ctx, uri).ifBlank { "video.mp4" }
        val defaultTitle = fileName.substringBeforeLast('.').ifBlank { fileName }
        val languageOptions = resources.getStringArray(R.array.signup_language_options).toList()
        val credentials = ProfileCredentialsStore.getStoredCredentials(ctx.applicationContext)
        val username = credentials?.username.orEmpty()
        val planetCode = DashboardServerPreferences.getServerCode(ctx).orEmpty()

        val dims = DashboardResourcesMediaUtils.resolveVideoDimensions(ctx, uri)
        sourceWidth = dims.first
        sourceHeight = dims.second
        sourceDurationMs = DashboardResourcesMediaUtils.resolveVideoDurationMs(ctx, uri)
        allowedHeights = DashboardResourcesMediaUtils.allowedVideoHeights(sourceHeight)
        defaultHeight = DashboardResourcesMediaUtils.defaultVideoHeightSelection(sourceHeight)
        defaultIndex = allowedHeights.indexOf(defaultHeight).takeIf { it >= 0 } ?: 0
        sourceFileSizeBytes = DashboardResourcesMediaUtils.resolveFileSizeBytes(ctx, uri) ?: 0L
        selectedStartMs = 0L
        selectedEndMs = sourceDurationMs
        selectedHeightForEstimate = allowedHeights.getOrElse(defaultIndex) { defaultHeight }

        val contentLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        buildInputFields(contentLayout, defaultTitle, languageOptions)
        buildResolutionSection(contentLayout)
        buildSegmentSection(contentLayout)
        buildRotationSection(contentLayout)
        buildPreviewSection(contentLayout)

        val scroll = ScrollView(ctx).apply { addView(contentLayout) }

        AlertDialog.Builder(ctx)
            .setTitle(fragment.getString(R.string.dashboard_resources_add_video))
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(fragment.getString(R.string.server_configuration_save), null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val rawTitle = titleInput.text?.toString()?.trim().orEmpty()
                        val title = DashboardResourcesMediaUtils.sanitizeResourceName(rawTitle)
                        val description = descriptionInput.text?.toString()?.trim().orEmpty()
                        if (title.isBlank()) {
                            titleInput.error = fragment.getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        if (description.isBlank()) {
                            descriptionInput.error = fragment.getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        val selectedIndex = (resolutionValue.tag as? Int) ?: defaultIndex
                        val selectedHeight = allowedHeights[selectedIndex]
                        val payload = JSONObject()
                            .put("title", title)
                            .put("description", description)
                            .put("subject", JSONArray().put(fragment.getString(R.string.server_planet_learning)))
                            .put("level", JSONArray().put(fragment.getString(R.string.server_planet_early_education)))
                            .put("language", languageSpinner.selectedItem?.toString().orEmpty())
                            .put("addedBy", username)
                            .put("sourcePlanet", planetCode)
                            .put("resideOn", planetCode)
                            .put("isDownloadable", isDownloadableCheck.isChecked)
                            .put("private", false)
                        val bytesProvider: suspend () -> ByteArray? = {
                            DashboardResourcesMediaUtils.transcodeVideoToMp4(ctx, fragment.getString(R.string.dashboard_resources_error_read_file),
                                uri = uri,
                                selectedHeight = selectedHeight,
                                sourceWidth = sourceWidth,
                                sourceHeight = sourceHeight,
                                startMs = selectedStartMs,
                                endMs = selectedEndMs,
                                sourceDurationMs = sourceDurationMs,
                                rotationDegrees = selectedRotationDegrees
                            )
                        }
                        fragment.performResourceCreateAndUpload(
                            payload = payload,
                            fileExtension = "mp4",
                            mimeType = "video/mp4",
                            credentials = credentials,
                            bytesProvider = bytesProvider,
                            onSuccess = {
                                isUploaded = true
                                Toast.makeText(ctx, fragment.getString(R.string.dashboard_resources_upload_success), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        )
                    }
                }
                dialog.show()
                dialog.setOnDismissListener {
                    previewHandler.removeCallbacks(previewMonitor)
                    previewPlayer.release()
                    if (uri.authority == "${ctx.packageName}.fileprovider" || isUploaded) {
                        runCatching {
                            File(ctx.cacheDir, "shared_images/${uri.lastPathSegment}").delete()
                        }
                    }
                }
            }
    }

    private fun buildInputFields(content: LinearLayout, defaultTitle: String, languageOptions: List<String>) {
        titleInput = EditText(ctx).apply {
            hint = fragment.getString(R.string.dashboard_resources_pdf_field_title)
            setText(defaultTitle)
        }
        descriptionInput = EditText(ctx).apply {
            hint = fragment.getString(R.string.dashboard_resources_pdf_field_description)
            setText("")
            minLines = 3
        }
        val languageLabel = TextView(ctx).apply {
            text = fragment.getString(R.string.dashboard_resources_pdf_field_language)
        }
        languageSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, languageOptions)
            setSelection(DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(ctx, resources, languageOptions))
        }
        isDownloadableCheck = CheckBox(ctx).apply {
            text = fragment.getString(R.string.dashboard_resources_pdf_field_downloadable)
            isChecked = true
        }

        content.addView(titleInput)
        content.addView(descriptionInput)
        content.addView(languageLabel)
        content.addView(languageSpinner)
        content.addView(isDownloadableCheck)
    }

    private fun buildResolutionSection(content: LinearLayout) {
        val resolutionLabel = TextView(ctx).apply {
            text = fragment.getString(R.string.dashboard_resources_video_resolution)
        }
        resolutionValue = TextView(ctx).apply {
            text = fragment.getString(
                R.string.dashboard_resources_video_resolution_value,
                allowedHeights.getOrElse(defaultIndex) { defaultHeight }
            )
            tag = defaultIndex
        }
        val resolutionSlider = Slider(ctx).apply {
            contentDescription = fragment.getString(R.string.slider_desc)
            val maxIndex = (allowedHeights.size - 1).coerceAtLeast(0)
            valueFrom = 0f
            valueTo = if (maxIndex > 0) maxIndex.toFloat() else 1f
            stepSize = 1f
            value = defaultIndex.toFloat().coerceIn(0f, if (maxIndex > 0) maxIndex.toFloat() else 1f)
            isVisible = allowedHeights.size > 1
            addOnChangeListener { _, value, _ ->
                val index = value.roundToInt().coerceIn(0, allowedHeights.lastIndex)
                val selectedHeight = allowedHeights[index]
                selectedHeightForEstimate = selectedHeight
                resolutionValue.tag = index
                resolutionValue.text = fragment.getString(R.string.dashboard_resources_video_resolution_value, selectedHeight)
                updateSizeEstimate()
            }
        }

        content.addView(resolutionLabel)
        content.addView(resolutionSlider)
        content.addView(resolutionValue)
    }

    private fun buildSegmentSection(content: LinearLayout) {
        val segmentTitle = TextView(ctx).apply {
            text = fragment.getString(R.string.dashboard_resources_video_segment_label)
            isVisible = sourceDurationMs > 1000L
        }
        segmentStartValue = TextView(ctx).apply {
            text = DashboardResourcesMediaUtils.formatDurationMs(selectedStartMs)
            isVisible = sourceDurationMs > 1000L
        }
        segmentEndValue = TextView(ctx).apply {
            text = DashboardResourcesMediaUtils.formatDurationMs(selectedEndMs)
            isVisible = sourceDurationMs > 1000L
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val segmentTimesRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            isVisible = sourceDurationMs > 1000L
            addView(segmentStartValue, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(segmentEndValue)
        }
        segmentTotalValue = TextView(ctx).apply {
            text = fragment.getString(
                R.string.dashboard_resources_video_segment_total,
                DashboardResourcesMediaUtils.formatDurationMs((selectedEndMs - selectedStartMs).coerceAtLeast(0L))
            )
            isVisible = sourceDurationMs > 1000L
        }
        sizeEstimateValue = TextView(ctx).apply {
            updateSizeEstimate()
        }

        val segmentRangeSlider = RangeSlider(ctx).apply {
            val rawTotalSeconds = max(2, kotlin.math.ceil(sourceDurationMs / 1000.0).toInt())
            val totalSeconds = (((rawTotalSeconds + 1) / 2) * 2).coerceAtLeast(2)
            contentDescription = fragment.getString(R.string.slider_desc)
            valueFrom = 0f
            valueTo = totalSeconds.toFloat()
            stepSize = 2f
            values = mutableListOf(0f, totalSeconds.toFloat())
            isVisible = sourceDurationMs > 1000L
            addOnChangeListener { slider, _, _ ->
                val currentValues = slider.values.sorted()
                val startSecond = currentValues.firstOrNull()?.roundToInt() ?: 0
                val endSecond = currentValues.lastOrNull()?.roundToInt() ?: totalSeconds
                val startMs = (startSecond * 1_000L).coerceIn(0L, sourceDurationMs)
                var endMs = (endSecond * 1_000L).coerceIn(0L, sourceDurationMs)
                if (endMs <= startMs) {
                    endMs = (startMs + 2_000L).coerceAtMost(sourceDurationMs)
                }
                selectedStartMs = startMs
                selectedEndMs = endMs
                if (::previewPlayer.isInitialized && previewPlayer.currentPosition !in selectedStartMs..<selectedEndMs) {
                    previewPlayer.seekTo(selectedStartMs)
                }
                segmentStartValue.text = DashboardResourcesMediaUtils.formatDurationMs(selectedStartMs)
                segmentEndValue.text = DashboardResourcesMediaUtils.formatDurationMs(selectedEndMs)
                segmentTotalValue.text = fragment.getString(
                    R.string.dashboard_resources_video_segment_total,
                    DashboardResourcesMediaUtils.formatDurationMs((selectedEndMs - selectedStartMs).coerceAtLeast(0L))
                )
                updateSizeEstimate()
            }
        }

        content.addView(segmentTitle)
        content.addView(segmentTimesRow)
        content.addView(segmentRangeSlider)
        content.addView(segmentTotalValue)
        content.addView(sizeEstimateValue)
    }

    private fun buildRotationSection(content: LinearLayout) {
        val rotateLeftButton = ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_rotate_left)
            contentDescription = fragment.getString(R.string.dashboard_resources_rotate_left)
            setBackgroundResource(android.R.drawable.btn_default_small)
        }
        val rotateRightButton = ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_rotate_right)
            contentDescription = fragment.getString(R.string.dashboard_resources_rotate_right)
            setBackgroundResource(android.R.drawable.btn_default_small)
        }
        val rotationButtonsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(rotateRightButton)
            addView(rotateLeftButton)
        }

        rotateLeftButton.setOnClickListener {
            selectedRotationDegrees = ((selectedRotationDegrees - 90f) % 360f + 360f) % 360f
            applyPreviewRotation()
        }
        rotateRightButton.setOnClickListener {
            selectedRotationDegrees = ((selectedRotationDegrees + 90f) % 360f + 360f) % 360f
            applyPreviewRotation()
        }

        content.addView(rotationButtonsRow)
    }

    private fun buildPreviewSection(content: LinearLayout) {
        val previewTitle = TextView(ctx).apply {
            text = fragment.getString(R.string.dashboard_resources_video_preview)
        }
        previewPlayer = ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            seekTo(selectedStartMs)
        }
        previewVideoView = PlayerView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            useController = false
            player = previewPlayer
        }

        previewContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * resources.displayMetrics.density).toInt()
            )
            clipChildren = true
            addView(previewVideoView)
        }

        previewToggleButton = ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_preview_play_stop_24)
            setBackgroundResource(R.drawable.bg_preview_overlay_button)
            val iconSize = (72 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            contentDescription = fragment.getString(R.string.dashboard_resources_video_preview_play)
            alpha = 1f
        }
        previewContainer.addView(previewToggleButton)

        applyPreviewRotation()
        previewContainer.post { updatePreviewContainerSize() }

        previewMonitor = object : Runnable {
            override fun run() {
                val isEnded = previewPlayer.playbackState == Player.STATE_ENDED
                if (previewPlayer.isPlaying || isEnded) {
                    if (isEnded || (selectedEndMs > selectedStartMs && previewPlayer.currentPosition >= selectedEndMs)) {
                        previewPlayer.seekTo(selectedStartMs)
                        if (isEnded) previewPlayer.play()
                    }
                }
                previewHandler.postDelayed(this, 100L)
            }
        }
        previewHandler.post(previewMonitor)

        val togglePreviewPlayback = {
            if (previewPlayer.isPlaying) {
                previewPlayer.pause()
                previewToggleButton.contentDescription = fragment.getString(R.string.dashboard_resources_video_preview_play)
                showPreviewOverlayButton()
            } else {
                if (previewPlayer.currentPosition !in selectedStartMs..<selectedEndMs) {
                    previewPlayer.seekTo(selectedStartMs)
                }
                previewPlayer.play()
                previewToggleButton.contentDescription = fragment.getString(R.string.dashboard_resources_video_preview_stop)
                hidePreviewOverlayButton()
            }
        }
        previewToggleButton.setOnClickListener { togglePreviewPlayback() }
        previewVideoView.setOnClickListener { togglePreviewPlayback() }

        content.addView(previewTitle)
        content.addView(previewContainer)
    }

    private fun updateSizeEstimate() {
        sizeEstimateValue.text = DashboardResourcesMediaUtils.buildVideoSizeEstimateText(ctx, fragment.getString(R.string.dashboard_resources_video_size_unknown), fragment.getString(R.string.dashboard_resources_video_size_estimate),
            sourceSizeBytes = sourceFileSizeBytes,
            sourceHeight = sourceHeight,
            selectedHeight = selectedHeightForEstimate,
            sourceDurationMs = sourceDurationMs,
            selectedStartMs = selectedStartMs,
            selectedEndMs = selectedEndMs
        )
    }

    private fun updatePreviewContainerSize() {
        if (!::previewContainer.isInitialized) return
        val normalizedRotation = ((selectedRotationDegrees % 360f) + 360f) % 360f
        val isQuarterTurn = normalizedRotation == 90f || normalizedRotation == 270f
        val rotatedWidth = if (isQuarterTurn) sourceHeight else sourceWidth
        val rotatedHeight = if (isQuarterTurn) sourceWidth else sourceHeight
        val containerWidth = previewContainer.width.takeIf { it > 0 }
            ?: (resources.displayMetrics.widthPixels - (32 * resources.displayMetrics.density).roundToInt())
        val computedHeight = if (rotatedWidth > 0 && rotatedHeight > 0) {
            (containerWidth.toFloat() * rotatedHeight.toFloat() / rotatedWidth.toFloat()).roundToInt()
        } else {
            (180 * resources.displayMetrics.density).toInt()
        }
        val minHeight = (120 * resources.displayMetrics.density).toInt()
        val maxHeight = (320 * resources.displayMetrics.density).toInt()
        val finalHeight = computedHeight.coerceIn(minHeight, maxHeight)
        previewContainer.layoutParams = (previewContainer.layoutParams as LinearLayout.LayoutParams).apply {
            height = finalHeight
        }
        previewVideoView.layoutParams = (previewVideoView.layoutParams as FrameLayout.LayoutParams).apply {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = FrameLayout.LayoutParams.MATCH_PARENT
        }
        previewContainer.requestLayout()
    }

    private fun applyPreviewRotation() {
        if (!::previewPlayer.isInitialized) return
        val rotationEffect = ScaleAndRotateTransformation.Builder()
            .setRotationDegrees(selectedRotationDegrees)
            .build()
        previewPlayer.setVideoEffects(listOf(rotationEffect))
        previewToggleButton.rotation = 0f
        updatePreviewContainerSize()
    }

    private fun showPreviewOverlayButton() {
        previewToggleButton.visibility = View.VISIBLE
        previewToggleButton.animate().alpha(1f).setDuration(180L).start()
    }

    private fun hidePreviewOverlayButton() {
        previewToggleButton.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                previewToggleButton.visibility = View.GONE
            }
            .start()
    }
}
