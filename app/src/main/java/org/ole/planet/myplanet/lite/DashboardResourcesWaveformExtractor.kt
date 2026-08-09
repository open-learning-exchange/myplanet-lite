/*
Author: Walfre López Prado
Email: loppra@plataformasinformaticas.com
Creation date: 2026-08-09
 */

package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.annotation.OptIn
import androidx.core.graphics.scale
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal object DashboardResourcesWaveformExtractor {
    private fun prepareAudioCodec(extractor: MediaExtractor): MediaCodec? {
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }
        if (audioTrackIndex == -1) return null
        extractor.selectTrack(audioTrackIndex)
        val format = extractor.getTrackFormat(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        return codec
    }
    
    suspend fun extract(context: Context, uri: Uri): FloatArray {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                if (durationMs <= 0) return@withContext floatArrayOf()
                extractor.setDataSource(context, uri, null)
                codec = prepareAudioCodec(extractor)
                if (codec == null) return@withContext floatArrayOf()
                val info = MediaCodec.BufferInfo()
                val amplitudes = mutableListOf<Float>()
                var sawOutputEOS = false
                val sampleTargetCount = 200
                while (!sawOutputEOS && coroutineContext.isActive) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                    val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                    if (outputBufferIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                        if (info.size > 0) {
                            val pcmData = ShortArray(info.size / 2)
                            outputBuffer.asShortBuffer().get(pcmData)
                            var maxAbs = 0
                            for (s in pcmData) {
                                val abs = kotlin.math.abs(s.toInt())
                                if (abs > maxAbs) maxAbs = abs
                            }
                            amplitudes.add(maxAbs.toFloat() / Short.MAX_VALUE)
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true
                    }
                }
                if (amplitudes.size > sampleTargetCount) {
                    val step = amplitudes.size / sampleTargetCount
                    FloatArray(sampleTargetCount) { i -> amplitudes.getOrNull(i * step) ?: 0f }
                } else {
                    amplitudes.toFloatArray()
                }
            } catch (_: Exception) {
                floatArrayOf()
            } finally {
                runCatching { retriever.release() }
                runCatching { extractor.release() }
                runCatching { codec?.stop(); codec?.release() }
            }
        }
    }
}

