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

object DashboardResourcesMediaUtils {
    const val MAX_IN_MEMORY_SIZE_BYTES = 50L * 1024 * 1024

    data class ResourceUploadMetadata(
        val fileName: String,
        val defaultTitle: String,
        val languageOptions: List<String>,
        val credentials: StoredCredentials?,
        val username: String,
        val planetCode: String
    )

    fun extractResourceMetadata(fragment: Fragment, uri: Uri, defaultFileName: String, isPdf: Boolean = false): ResourceUploadMetadata {
        val context = fragment.requireContext()
        val fileName = resolveFileName(context, uri).ifBlank { defaultFileName }
        val defaultTitle = if (isPdf) {
            fileName.replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "").ifBlank { fileName }
        } else {
            fileName.substringBeforeLast('.').ifBlank { fileName }
        }
        val languageOptions = fragment.resources.getStringArray(R.array.signup_language_options).toList()
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        val username = credentials?.username.orEmpty()
        val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()

        return ResourceUploadMetadata(
            fileName = fileName,
            defaultTitle = defaultTitle,
            languageOptions = languageOptions,
            credentials = credentials,
            username = username,
            planetCode = planetCode
        )
    }

    fun sanitizeResourceName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9\\-_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "resource_" + System.currentTimeMillis() }
    }

    fun applyWebCompatibleResourceDefaults(payload: JSONObject) {
        if (!payload.has("author")) payload.put("author", "")
        if (!payload.has("year")) payload.put("year", "")
        if (!payload.has("publisher")) payload.put("publisher", "")
        if (!payload.has("linkToLicense")) payload.put("linkToLicense", "")
        if (!payload.has("openWith")) payload.put("openWith", "")
        if (!payload.has("resourceFor")) payload.put("resourceFor", JSONArray())
        if (!payload.has("medium")) payload.put("medium", "")
        if (!payload.has("resourceType")) payload.put("resourceType", "")
    }

    fun normalizeResourceMediaType(mimeType: String): String {
        val normalized = mimeType.lowercase(Locale.ROOT)
        return when {
            normalized.contains("image") -> "image"
            normalized.contains("video") -> "video"
            normalized.contains("audio") -> "audio"
            normalized.contains("pdf") -> "pdf"
            else -> normalized
        }
    }

    fun resolveFileName(context: Context, uri: Uri): String {
        var name = ""
        val cursor: Cursor? = runCatching {
            context.contentResolver.query(uri, null, null, null, null)
        }.getOrNull()
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) {
                name = it.getString(nameIndex).orEmpty()
            }
        }
        return name.ifBlank { uri.lastPathSegment.orEmpty().substringAfterLast('/') }
    }

    fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
    }

    fun resolveImageSize(context: Context, uri: Uri): Pair<Int, Int>? {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
        }.getOrNull()
    }

    fun buildResizedImageBytes(context: Context, uri: Uri, percent: Float, mimeType: String): ByteArray? {
        return runCatching {
            val sourceBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@runCatching null
            val factor = (percent / 100f).coerceIn(0.4f, 1f)
            val targetWidth = max(1, (sourceBitmap.width * factor).roundToInt())
            val targetHeight = max(1, (sourceBitmap.height * factor).roundToInt())
            val scaled = if (targetWidth == sourceBitmap.width && targetHeight == sourceBitmap.height) {
                sourceBitmap
            } else {
                sourceBitmap.scale(targetWidth, targetHeight, true)
            }
            val output = ByteArrayOutputStream()
            val (format, quality) = if (mimeType.contains("png", ignoreCase = true)) {
                Bitmap.CompressFormat.PNG to 100
            } else {
                Bitmap.CompressFormat.JPEG to 90
            }
            scaled.compress(format, quality, output)
            if (scaled !== sourceBitmap) scaled.recycle()
            sourceBitmap.recycle()
            output.toByteArray()
        }.getOrNull()
    }

    fun extensionForImageMimeType(mimeType: String): String {
        val lower = mimeType.lowercase(Locale.ROOT)
        return when {
            lower.contains("png") -> "png"
            lower.contains("webp") -> "webp"
            else -> "jpg"
        }
    }

    fun resolveVideoDimensions(context: Context, uri: Uri): Pair<Int, Int> {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            retriever.release()
            if (rotation == 90 || rotation == 270) height to width else width to height
        }.getOrDefault(1280 to 720)
    }

    fun resolveAudioBitrate(context: Context, uri: Uri): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { it / 1000 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

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

    suspend fun extractWaveform(context: Context, uri: Uri): FloatArray {
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

    fun resolveVideoDurationMs(context: Context, uri: Uri): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            raw?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    fun allowedVideoHeights(sourceHeight: Int): List<Int> = when {
        sourceHeight >= 1080 -> listOf(480, 576, 720, 1080)
        sourceHeight >= 720 -> listOf(480, 576, 720)
        sourceHeight >= 576 -> listOf(480, 576)
        else -> listOf(480)
    }

    fun defaultVideoHeightSelection(sourceHeight: Int): Int = when {
        sourceHeight >= 1080 -> 720
        sourceHeight >= 720 -> 720
        sourceHeight >= 576 -> 576
        else -> 480
    }

    fun resolveFileSizeBytes(context: Context, uri: Uri): Long? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst()) cursor.getLong(sizeIndex) else null
            }
        }.getOrNull()
    }

    data class VideoEstimateParameters(
        val sourceSizeBytes: Long,
        val sourceHeight: Int,
        val selectedHeight: Int,
        val sourceDurationMs: Long,
        val selectedStartMs: Long,
        val selectedEndMs: Long
    )

    fun buildVideoSizeEstimateText(
        context: Context,
        unknownText: String,
        estimateFormat: String,
        params: VideoEstimateParameters
    ): String {
        if (params.sourceSizeBytes <= 0L) return unknownText
        val estimatedBytes = estimateVideoUploadSizeBytes(params)
        val formattedSize = Formatter.formatShortFileSize(context, estimatedBytes)
        return estimateFormat.format(formattedSize)
    }

    fun formatDurationMs(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun estimateAudioUploadSizeBytes(bitrateKbps: Int, durationMs: Long): Long {
        val bytes = (bitrateKbps.toDouble() * durationMs.toDouble()) / 8.0
        return (bytes * 1.05).toLong().coerceAtLeast(1024L)
    }

    fun estimateVideoUploadSizeBytes(params: VideoEstimateParameters): Long {
        val sourceSizeBytes = params.sourceSizeBytes
        val sourceHeight = params.sourceHeight
        val selectedHeight = params.selectedHeight
        val sourceDurationMs = params.sourceDurationMs
        val selectedStartMs = params.selectedStartMs
        val selectedEndMs = params.selectedEndMs

        val durationFactor = if (sourceDurationMs <= 0L) 1.0 else {
            ((selectedEndMs - selectedStartMs).toDouble() / sourceDurationMs.toDouble()).coerceIn(0.0, 1.0)
        }
        val resolutionFactor = if (sourceHeight > 0 && selectedHeight < sourceHeight) {
            val ratio = selectedHeight.toDouble() / sourceHeight.toDouble()
            ratio * ratio
        } else 1.0
        val estimated = (sourceSizeBytes * resolutionFactor * durationFactor * 0.95).toLong()
        return estimated.coerceAtLeast(64L * 1024L)
    }

    @OptIn(UnstableApi::class)
    suspend fun transcodeAudio(
        context: Context,
        readFileErrorMessage: String,
        uri: Uri,
        bitrateKbps: Int,
        startMs: Long,
        endMs: Long,
        sourceDurationMs: Long
    ): ByteArray? {
        val inputFile = withContext(Dispatchers.IO) { copyUriToTempFile(context, readFileErrorMessage, uri) }
        val outputFile = withContext(Dispatchers.IO) { File.createTempFile("resource_output_audio", ".mp3", context.cacheDir) }
        return try {
            val exportSucceeded = suspendCancellableCoroutine { continuation ->
                val clippingBuilder = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs.coerceAtLeast(0L))
                if (endMs in 1 until sourceDurationMs) clippingBuilder.setEndPositionMs(endMs)
                val mediaItem = MediaItem.Builder().setUri(Uri.fromFile(inputFile)).setClippingConfiguration(clippingBuilder.build()).build()
                val audioEncoderSettings = AudioEncoderSettings.Builder().setBitrate(bitrateKbps * 1000).build()
                val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
                val transformer = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(DefaultEncoderFactory.Builder(context).setRequestedAudioEncoderSettings(audioEncoderSettings).build())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (continuation.isActive) continuation.resume(true)
                        }
                        override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    })
                    .build()
                continuation.invokeOnCancellation { transformer.cancel() }
                transformer.start(editedMediaItem, outputFile.absolutePath)
            }
            if (!exportSucceeded) null else withContext(Dispatchers.IO) { readBytesIfUnderSize(outputFile) }
        } catch (_: Throwable) {
            null
        } finally {
            withContext(Dispatchers.IO) { inputFile.delete(); outputFile.delete() }
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun transcodeVideoToMp4(
        context: Context,
        readFileErrorMessage: String,
        uri: Uri,
        selectedHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        startMs: Long,
        endMs: Long,
        sourceDurationMs: Long,
        rotationDegrees: Float
    ): ByteArray? {
        val inputFile = withContext(Dispatchers.IO) { copyUriToTempFile(context, readFileErrorMessage, uri) }
        val outputFile = withContext(Dispatchers.IO) { File.createTempFile("resource_output_video", ".mp4", context.cacheDir) }
        val shouldScale = selectedHeight < sourceHeight
        return try {
            val exportSucceeded = suspendCancellableCoroutine { continuation ->
                val clippingBuilder = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs.coerceAtLeast(0L))
                if (endMs in 1 until sourceDurationMs) clippingBuilder.setEndPositionMs(endMs)
                val mediaItem = MediaItem.Builder().setUri(Uri.fromFile(inputFile)).setClippingConfiguration(clippingBuilder.build()).build()
                val editedMediaBuilder = EditedMediaItem.Builder(mediaItem)
                if (shouldScale || rotationDegrees != 0f) {
                    val videoEffects = mutableListOf<Effect>()
                    if (rotationDegrees != 0f) {
                        videoEffects += ScaleAndRotateTransformation.Builder().setRotationDegrees(rotationDegrees).build()
                    }
                    if (shouldScale) {
                        val normalizedRotation = ((rotationDegrees % 360f) + 360f) % 360f
                        val isQuarterTurn = normalizedRotation == 90f || normalizedRotation == 270f
                        val targetHeight = if (isQuarterTurn && sourceHeight > 0 && sourceWidth > 0) {
                            (selectedHeight.toFloat() * sourceWidth.toFloat() / sourceHeight.toFloat()).roundToInt().coerceAtLeast(1)
                        } else selectedHeight
                        videoEffects += Presentation.createForHeight(targetHeight)
                    }
                    editedMediaBuilder.setEffects(Effects(emptyList(), videoEffects))
                }
                val editedMediaItem = editedMediaBuilder.build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (continuation.isActive) continuation.resume(true)
                        }
                        override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }).build()
                continuation.invokeOnCancellation { transformer.cancel() }
                transformer.start(editedMediaItem, outputFile.absolutePath)
            }
            if (!exportSucceeded) null else withContext(Dispatchers.IO) { readBytesIfUnderSize(outputFile) }
        } catch (_: Throwable) {
            null
        } finally {
            withContext(Dispatchers.IO) { inputFile.delete(); outputFile.delete() }
        }
    }

    fun readBytesIfUnderSize(file: File, maxBytes: Long = MAX_IN_MEMORY_SIZE_BYTES): ByteArray {
        if (file.length() > maxBytes) {
            throw IllegalStateException("Transformed file too large to process in memory")
        }
        return file.readBytes()
    }

    fun copyUriToTempFile(context: Context, readFileErrorMessage: String, uri: Uri): File {
        val tempFile = File.createTempFile("resource_input_video", ".tmp", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException(readFileErrorMessage)
        return tempFile
    }

    fun resolveDefaultLanguageIndex(context: Context, resources: Resources, options: List<String>): Int {
        val languageCode = runCatching { resources.configuration.locales[0]?.language?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
        val label = when (languageCode) {
            "ne" -> context.getString(R.string.language_name_nepali)
            "fr" -> context.getString(R.string.language_name_french)
            "es" -> context.getString(R.string.language_name_spanish)
            "ar" -> context.getString(R.string.language_name_arabic)
            "so" -> context.getString(R.string.language_name_somali)
            "hi" -> context.getString(R.string.language_name_hindi)
            "pt" -> context.getString(R.string.language_name_portuguese)
            else -> context.getString(R.string.language_name_english)
        }
        return options.indexOf(label).takeIf { it >= 0 }
            ?: options.indexOf(context.getString(R.string.language_name_english)).takeIf { it >= 0 }
            ?: 0
    }
}
