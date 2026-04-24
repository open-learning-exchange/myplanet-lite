package org.ole.planet.myplanet.lite

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.util.AudioWaveformView

@OptIn(UnstableApi::class)
internal fun DashboardResourcesPageFragment.showAudioMetadataPopup(uri: Uri) {
        val context = requireContext()
        val fileName = DashboardResourcesMediaUtils.resolveFileName(requireContext(), uri).ifBlank { "audio.mp3" }
        val defaultTitle = fileName.substringBeforeLast('.').ifBlank { fileName }
        val languageOptions = resources.getStringArray(R.array.signup_language_options).toList()
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        val username = credentials?.username.orEmpty()
        val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()
        val sourceDurationMs = DashboardResourcesMediaUtils.resolveVideoDurationMs(requireContext(), uri)
        val bitrates = listOf(96, 128, 192, 320)
        val sourceBitrate = DashboardResourcesMediaUtils.resolveAudioBitrate(requireContext(), uri) ?: 320
        val allowedBitrates = bitrates.filter { it <= sourceBitrate || it == 96 }.sorted()
        var selectedBitrate = allowedBitrates.lastOrNull() ?: bitrates[0]
        var selectedStartMs = 0L
        var selectedEndMs = sourceDurationMs

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
        val qualityLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_audio_quality)
        }
        val qualityValueLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_audio_quality_value, selectedBitrate)
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
        }
        val segmentTimesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isVisible = sourceDurationMs > 1000L
            addView(segmentStartValue, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(segmentEndValue, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val segmentTotalValue = TextView(context).apply {
            text = getString(
                R.string.dashboard_resources_video_segment_total,
                DashboardResourcesMediaUtils.formatDurationMs((selectedEndMs - selectedStartMs).coerceAtLeast(0L))
            )
            isVisible = sourceDurationMs > 1000L
        }
        val sizeEstimateValue = TextView(context).apply {
            val estimated = DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(selectedBitrate, (selectedEndMs - selectedStartMs).coerceAtLeast(0L))
            text = getString(R.string.dashboard_resources_audio_size_estimate, Formatter.formatShortFileSize(context, estimated))
        }
        val waveformView = AudioWaveformView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (100 * resources.displayMetrics.density).toInt()
            )
        }
        val previewPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            setSeekParameters(SeekParameters.EXACT)
            prepare()
            seekTo(selectedStartMs)
        }
        val updateSizeEstimate = {
            val estimated = DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(selectedBitrate, (selectedEndMs - selectedStartMs).coerceAtLeast(0L))
            sizeEstimateValue.text = getString(R.string.dashboard_resources_audio_size_estimate, Formatter.formatShortFileSize(context, estimated))
        }
        val qualitySlider = Slider(context).apply {
            contentDescription = getString(R.string.slider_desc)
            val maxIndex = (allowedBitrates.size - 1).coerceAtLeast(0)
            valueFrom = 0f
            valueTo = if (maxIndex > 0) maxIndex.toFloat() else 1f
            stepSize = 1f
            value = if (maxIndex > 0) maxIndex.toFloat() else 0f
            isVisible = allowedBitrates.size > 1
            addOnChangeListener { _, value, _ ->
                val index = value.roundToInt().coerceIn(0, allowedBitrates.lastIndex)
                selectedBitrate = allowedBitrates[index]
                qualityValueLabel.text = getString(R.string.dashboard_resources_audio_quality_value, selectedBitrate)
                updateSizeEstimate()
            }
        }
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
                selectedStartMs = (startSecond * 1_000L).coerceIn(0L, sourceDurationMs)
                selectedEndMs = (endSecond * 1_000L).coerceIn(0L, sourceDurationMs)
                if (selectedEndMs <= selectedStartMs) {
                    selectedEndMs = (selectedStartMs + 2_000L).coerceAtMost(sourceDurationMs)
                }
                segmentStartValue.text = DashboardResourcesMediaUtils.formatDurationMs(selectedStartMs)
                segmentEndValue.text = DashboardResourcesMediaUtils.formatDurationMs(selectedEndMs)
                segmentTotalValue.text = getString(
                    R.string.dashboard_resources_video_segment_total,
                    DashboardResourcesMediaUtils.formatDurationMs((selectedEndMs - selectedStartMs).coerceAtLeast(0L))
                )
                if (previewPlayer.currentPosition !in selectedStartMs..<selectedEndMs) {
                    previewPlayer.seekTo(selectedStartMs)
                }
                if (sourceDurationMs > 0) {
                    waveformView.setSelection(selectedStartMs.toFloat() / sourceDurationMs, selectedEndMs.toFloat() / sourceDurationMs)
                }
                updateSizeEstimate()
            }
        }
        lifecycleScope.launch {
            val waveform = DashboardResourcesMediaUtils.extractWaveform(requireContext(), uri)
            waveformView.setWaveform(waveform)
        }

        val previewPlayerView = PlayerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            player = previewPlayer
            useController = true
            showController()
            setBackgroundColor(Color.TRANSPARENT)
            findViewById<View>(androidx.media3.ui.R.id.exo_artwork)?.isVisible = false
            findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)?.isVisible = false
        }

        val previewContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (140 * resources.displayMetrics.density).toInt()
            ).apply {
                topMargin = (16 * resources.displayMetrics.density).toInt()
            }
            addView(waveformView)
            addView(previewPlayerView)
        }

        val progressHandler = Handler(Looper.getMainLooper())
        val updateProgressTask = object : Runnable {
            override fun run() {
                val isEnded = previewPlayer.playbackState == Player.STATE_ENDED
                if (previewPlayer.isPlaying || isEnded) {
                    val pos = previewPlayer.currentPosition
                    if (isEnded || (selectedEndMs in (selectedStartMs + 1)..pos)) {
                        previewPlayer.seekTo(selectedStartMs)
                        if (isEnded) previewPlayer.play()
                    }
                    val dur = previewPlayer.duration.toFloat()
                    if (dur > 0) {
                        waveformView.setProgress(previewPlayer.currentPosition.toFloat() / dur)
                    }
                }
                progressHandler.postDelayed(this, 100)
            }
        }
        progressHandler.post(updateProgressTask)

        val previewTitle = TextView(context).apply {
            text = getString(R.string.dashboard_resources_audio_preview)
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        content.addView(titleInput)
        content.addView(descriptionInput)
        content.addView(languageLabel)
        content.addView(languageSpinner)
        content.addView(isDownloadableCheck)
        content.addView(qualityLabel)
        content.addView(qualityValueLabel)
        content.addView(qualitySlider)
        content.addView(segmentTitle)
        content.addView(segmentTimesRow)
        content.addView(segmentRangeSlider)
        content.addView(segmentTotalValue)
        content.addView(sizeEstimateValue)
        content.addView(previewTitle)
        content.addView(previewContainer)

        val scroll = ScrollView(context).apply { addView(content) }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dashboard_resources_add_audio))
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
                            DashboardResourcesMediaUtils.transcodeAudio(requireContext(), getString(R.string.dashboard_resources_error_read_file), 
                                uri = uri,
                                bitrateKbps = selectedBitrate,
                                startMs = selectedStartMs,
                                endMs = selectedEndMs,
                                sourceDurationMs = sourceDurationMs
                            )
                        }
                        performResourceCreateAndUpload(
                            payload = payload,
                            fileExtension = "mp3",
                            mimeType = "audio/mpeg",
                            credentials = credentials,
                            bytesProvider = bytesProvider,
                            onSuccess = {
                                Toast.makeText(context, getString(R.string.dashboard_resources_upload_success), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        )
                    }
                }
                dialog.show()
                dialog.setOnDismissListener {
                    progressHandler.removeCallbacks(updateProgressTask)
                    previewPlayer.release()
                }
            }
    }
