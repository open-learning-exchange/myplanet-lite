package org.ole.planet.myplanet.lite
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import org.ole.planet.myplanet.lite.util.RecordingWaveformView

@SuppressLint("SetTextI18n")
internal fun DashboardResourcesPageFragment.showRecordAudioPopup() {
    val context = requireContext()
    var isRecording = false
    currentAudioFile?.delete()
    currentAudioFile = null

    val dialogView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val padding = (24 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }

    val timerText = TextView(context).apply {
        text = "00:00"
        textSize = 32f
        setTextColor(Color.BLACK)
        setPadding(0, 0, 0, (24 * resources.displayMetrics.density).toInt())
    }

    val waveformView = RecordingWaveformView(context).apply {
        val size = (120 * resources.displayMetrics.density).toInt()
        layoutParams = FrameLayout.LayoutParams(size, size)
    }

    val recordButton = ImageButton(context).apply {
        setImageResource(R.drawable.ic_add_record_24)
        setColorFilter(Color.RED)
        background = null
        val size = (80 * resources.displayMetrics.density).toInt()
        layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    val buttonContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(waveformView)
        addView(recordButton)
    }

    dialogView.addView(timerText)
    dialogView.addView(buttonContainer)

    val dialog = AlertDialog.Builder(context)
        .setTitle(getString(R.string.dashboard_resources_record_audio))
        .setView(dialogView)
        .setNegativeButton(R.string.dashboard_resources_record_audio_cancel, null)
        .setPositiveButton(R.string.dashboard_resources_record_audio_accept, null)
        .setCancelable(false)
        .create()

    val updateTimerTask = object : Runnable {
        override fun run() {
            if (isRecording) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                timerText.text = DashboardResourcesMediaUtils.formatDurationMs(elapsed)
                val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                val normalized = (maxAmplitude.toFloat() / 32768f).coerceIn(0f, 1f)
                waveformView.addAmplitude(normalized)
                recordingTimerHandler.postDelayed(this, 100)
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        recordingTimerHandler.removeCallbacks(updateTimerTask)
        waveformView.clear()
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("DashboardResources", "Error stopping recorder", e)
        }
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null
        recordButton.setImageResource(R.drawable.ic_add_record_24)
        recordButton.setColorFilter(Color.RED)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
    }

    fun startRecording() {
        currentAudioFile?.delete()
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(dir, "recorded_audio_${System.currentTimeMillis()}.m4a")
        currentAudioFile = file

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder = recorder
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            recordingTimerHandler.post(updateTimerTask)
            recordButton.setImageResource(R.drawable.ic_stop)
            recordButton.setColorFilter(Color.BLACK)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        } catch (_: Exception) {
            Toast.makeText(context, "Recording failed to start", Toast.LENGTH_SHORT).show()
            recorder.release()
            mediaRecorder = null
            isRecording = false
        }
    }

    recordButton.setOnClickListener {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (isRecording) stopRecording()
            val file = currentAudioFile
            if (file != null && file.exists()) {
                val authority = "${context.packageName}.fileprovider"
                val uri: Uri = FileProvider.getUriForFile(context, authority, file)
                showAudioMetadataPopup(uri)
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            if (isRecording) stopRecording()
            currentAudioFile?.delete()
            currentAudioFile = null
            dialog.dismiss()
        }
    }
    dialog.show()
}
