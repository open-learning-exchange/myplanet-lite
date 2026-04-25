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
        val context = requireContext()
        val fileName = DashboardResourcesMediaUtils.resolveFileName(requireContext(), uri).ifBlank { "video.mp4" }
        val defaultTitle = fileName.substringBeforeLast('.').ifBlank { fileName }
        val languageOptions = resources.getStringArray(R.array.signup_language_options).toList()
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        val username = credentials?.username.orEmpty()
        val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()
        val (sourceWidth, sourceHeight) = DashboardResourcesMediaUtils.resolveVideoDimensions(requireContext(), uri)
        val sourceDurationMs = DashboardResourcesMediaUtils.resolveVideoDurationMs(requireContext(), uri)
        val allowedHeights = DashboardResourcesMediaUtils.allowedVideoHeights(sourceHeight)
        val defaultHeight = DashboardResourcesMediaUtils.defaultVideoHeightSelection(sourceHeight)
        val defaultIndex = allowedHeights.indexOf(defaultHeight).takeIf { it >= 0 } ?: 0
        val sourceFileSizeBytes = DashboardResourcesMediaUtils.resolveFileSizeBytes(requireContext(), uri)
        var selectedStartMs = 0L
        var selectedEndMs = sourceDurationMs
        var selectedHeightForEstimate = allowedHeights.getOrElse(defaultIndex) { defaultHeight }
        var selectedRotationDegrees = 0f
        var isUploaded = false

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val titleInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_title)
            setText(defaultTitle)
        }
        val descriptionInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_description)
            setText("")
            minLines = 3
        }
        val languageLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_language)
        }
        val languageSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languageOptions)
            setSelection(DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(requireContext(), resources, languageOptions))
        }
        val isDownloadableCheck = CheckBox(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_downloadable)
            isChecked = true
        }
        val resolutionLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_video_resolution)
        }
        val resolutionValue = TextView(context).apply {
            text = getString(
                R.string.dashboard_resources_video_resolution_value,
                allowedHeights.getOrElse(defaultIndex) { defaultHeight }
            )
        }
        val segmentTitle = TextView(context).apply {
            text = getString(R.string.dashboard_resources_video_segment_label)
            isVisible = sourceDurationMs > 1000L
        }
        val segmentStartValue = TextView(context).apply {
            text = DashboardResourcesMediaUtils.formatDurationMs(selectedStartMs)
            isVisible = sourceDurationMs > 1000L
        }
        val segmentEndValue = TextView(context).apply {
            text = DashboardResourcesMediaUtils.formatDurationMs(selectedEndMs)
            isVisible = sourceDurationMs > 1000L
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val segmentTimesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isVisible = sourceDurationMs > 1000L
            addView(segmentStartValue, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(segmentEndValue)
        }
        val segmentTotalValue = TextView(context).apply {
            text = getString(
                R.string.dashboard_resources_video_segment_total,
                DashboardResourcesMediaUtils.formatDurationMs((selectedEndMs - selectedStartMs).coerceAtLeast(0L))
            )
            isVisible = sourceDurationMs > 1000L
        }
        val sizeEstimateValue = TextView(context).apply {
            text = DashboardResourcesMediaUtils.buildVideoSizeEstimateText(requireContext(), getString(R.string.dashboard_resources_video_size_unknown), getString(R.string.dashboard_resources_video_size_estimate), 
                sourceSizeBytes = sourceFileSizeBytes,
                sourceHeight = sourceHeight,
                selectedHeight = allowedHeights.getOrElse(defaultIndex) { defaultHeight },
                sourceDurationMs = sourceDurationMs,
                selectedStartMs = selectedStartMs,
                selectedEndMs = selectedEndMs
            )
        }
        val previewTitle = TextView(context).apply {
            text = getString(R.string.dashboard_resources_video_preview)
        }
        val previewPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            seekTo(selectedStartMs)
        }
        val previewVideoView = PlayerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            useController = false
            player = previewPlayer
        }
        val rotateLeftButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_rotate_left)
            contentDescription = getString(R.string.dashboard_resources_rotate_left)
            setBackgroundResource(android.R.drawable.btn_default_small)
        }
        val rotateRightButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_rotate_right)
            contentDescription = getString(R.string.dashboard_resources_rotate_right)
            setBackgroundResource(android.R.drawable.btn_default_small)
        }
        val rotationButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(rotateRightButton)
            addView(rotateLeftButton)
        }
        val previewContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * resources.displayMetrics.density).toInt()
            )
            clipChildren = true
            addView(previewVideoView)
        }
        val previewToggleButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_preview_play_stop_24)
            setBackgroundResource(R.drawable.bg_preview_overlay_button)
            val iconSize = (72 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            contentDescription = getString(R.string.dashboard_resources_video_preview_play)
            alpha = 1f
        }
        previewContainer.addView(previewToggleButton)
        val updatePreviewContainerSize = {
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
        val applyPreviewRotation = {
            val rotationEffect = ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(selectedRotationDegrees)
                .build()
            previewPlayer.setVideoEffects(listOf(rotationEffect))
            previewToggleButton.rotation = 0f
            updatePreviewContainerSize()
        }
        rotateLeftButton.setOnClickListener {
            selectedRotationDegrees = ((selectedRotationDegrees - 90f) % 360f + 360f) % 360f
            applyPreviewRotation()
        }
        rotateRightButton.setOnClickListener {
            selectedRotationDegrees = ((selectedRotationDegrees + 90f) % 360f + 360f) % 360f
            applyPreviewRotation()
        }
        applyPreviewRotation()
        previewContainer.post { updatePreviewContainerSize() }
        val previewHandler = Handler(Looper.getMainLooper())
        val showPreviewOverlayButton = {
            previewToggleButton.visibility = View.VISIBLE
            previewToggleButton.animate().alpha(1f).setDuration(180L).start()
        }
        val hidePreviewOverlayButton = {
            previewToggleButton.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction {
                    previewToggleButton.visibility = View.GONE
                }
                .start()
        }
        val previewMonitor = object : Runnable {
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
                previewToggleButton.contentDescription = getString(R.string.dashboard_resources_video_preview_play)
                showPreviewOverlayButton()
            } else {
                if (previewPlayer.currentPosition !in selectedStartMs..<selectedEndMs) {
                    previewPlayer.seekTo(selectedStartMs)
                }
                previewPlayer.play()
                previewToggleButton.contentDescription = getString(R.string.dashboard_resources_video_preview_stop)
                hidePreviewOverlayButton()
            }
        }
        previewToggleButton.setOnClickListener { togglePreviewPlayback() }
        previewVideoView.setOnClickListener { togglePreviewPlayback() }
        val segmentRangeSlider = RangeSlider(context).apply {
            val rawTotalSeconds = max(2, kotlin.math.ceil(sourceDurationMs / 1000.0).toInt())
            val totalSeconds = (((rawTotalSeconds + 1) / 2) * 2).coerceAtLeast(2)
            contentDescription = getString(R.string.slider_desc)
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
                if (previewPlayer.currentPosition !in selectedStartMs..<selectedEndMs) {
                    previewPlayer.seekTo(selectedStartMs)
                }
                segmentStartValue.text = DashboardResourcesMediaUtils.formatDurationMs(selectedStartMs)
                segmentEndValue.text = DashboardResourcesMediaUtils.formatDurationMs(selectedEndMs)
                segmentTotalValue.text = getString(
                    R.string.dashboard_resources_video_segment_total,
                    DashboardResourcesMediaUtils.formatDurationMs((selectedEndMs - selectedStartMs).coerceAtLeast(0L))
                )
                sizeEstimateValue.text = DashboardResourcesMediaUtils.buildVideoSizeEstimateText(requireContext(), getString(R.string.dashboard_resources_video_size_unknown), getString(R.string.dashboard_resources_video_size_estimate), 
                    sourceSizeBytes = sourceFileSizeBytes,
                    sourceHeight = sourceHeight,
                    selectedHeight = selectedHeightForEstimate,
                    sourceDurationMs = sourceDurationMs,
                    selectedStartMs = selectedStartMs,
                    selectedEndMs = selectedEndMs
                )
            }
        }
        val resolutionSlider = Slider(context).apply {
            contentDescription = getString(R.string.slider_desc)
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
                resolutionValue.text = getString(R.string.dashboard_resources_video_resolution_value, selectedHeight)
                sizeEstimateValue.text = DashboardResourcesMediaUtils.buildVideoSizeEstimateText(requireContext(), getString(R.string.dashboard_resources_video_size_unknown), getString(R.string.dashboard_resources_video_size_estimate), 
                    sourceSizeBytes = sourceFileSizeBytes,
                    sourceHeight = sourceHeight,
                    selectedHeight = selectedHeight,
                    sourceDurationMs = sourceDurationMs,
                    selectedStartMs = selectedStartMs,
                    selectedEndMs = selectedEndMs
                )
            }
        }

        content.addView(titleInput)
        content.addView(descriptionInput)
        content.addView(languageLabel)
        content.addView(languageSpinner)
        content.addView(isDownloadableCheck)
        content.addView(resolutionLabel)
        content.addView(resolutionSlider)
        content.addView(resolutionValue)
        content.addView(segmentTitle)
        content.addView(segmentTimesRow)
        content.addView(segmentRangeSlider)
        content.addView(segmentTotalValue)
        content.addView(sizeEstimateValue)
        content.addView(rotationButtonsRow)
        content.addView(previewTitle)
        content.addView(previewContainer)

        val scroll = ScrollView(context).apply { addView(content) }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dashboard_resources_add_video))
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.server_configuration_save), null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val rawTitle = titleInput.text?.toString()?.trim().orEmpty()
                        val title = DashboardResourcesMediaUtils.sanitizeResourceName(rawTitle)
                        val description = descriptionInput.text?.toString()?.trim().orEmpty()
                        if (title.isBlank()) {
                            titleInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        if (description.isBlank()) {
                            descriptionInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        val selectedIndex = resolutionSlider.value.roundToInt().coerceIn(0, allowedHeights.lastIndex)
                        val selectedHeight = allowedHeights[selectedIndex]
                        val payload = JSONObject()
                            .put("title", title)
                            .put("description", description)
                            .put("subject", JSONArray().put(getString(R.string.server_planet_learning)))
                            .put("level", JSONArray().put(getString(R.string.server_planet_early_education)))
                            .put("language", languageSpinner.selectedItem?.toString().orEmpty())
                            .put("addedBy", username)
                            .put("sourcePlanet", planetCode)
                            .put("resideOn", planetCode)
                            .put("isDownloadable", isDownloadableCheck.isChecked)
                            .put("private", false)
                        val bytesProvider: suspend () -> ByteArray? = {
                            DashboardResourcesMediaUtils.transcodeVideoToMp4(requireContext(), getString(R.string.dashboard_resources_error_read_file), 
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
                        performResourceCreateAndUpload(
                            payload = payload,
                            fileExtension = "mp4",
                            mimeType = "video/mp4",
                            credentials = credentials,
                            bytesProvider = bytesProvider,
                            onSuccess = {
                                isUploaded = true
                                Toast.makeText(context, getString(R.string.dashboard_resources_upload_success), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        )
                    }
                }
                dialog.show()
                dialog.setOnDismissListener {
                    previewHandler.removeCallbacks(previewMonitor)
                    previewPlayer.release()
                    showPreviewOverlayButton()
                    if (uri.authority == "${context.packageName}.fileprovider" || isUploaded) {
                        runCatching {
                            File(context.cacheDir, "shared_images/${uri.lastPathSegment}").delete()
                        }
                    }
                }
            }
    }
