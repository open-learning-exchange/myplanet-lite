package org.ole.planet.myplanet.lite.dashboard

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object VoiceImageFactory {
    private const val MAX_IMAGE_DIMENSION = 1280
    private const val JPEG_QUALITY = 85

    fun createPendingVoiceImage(
        uri: Uri,
        contentResolver: ContentResolver,
        cacheDir: File,
        generatePendingImageId: (String) -> String
    ): PendingVoiceImage {
        val original = contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IllegalArgumentException("Unable to decode image stream")
        val processed = prepareBitmapForWeb(original)
        if (processed !== original) {
            original.recycle()
        }
        val jpegBytes = compressBitmapToJpeg(processed)
        if (!processed.isRecycled) {
            processed.recycle()
        }
        val fileName = generateImageFileName()
        val tempFile = File(cacheDir, fileName)
        FileOutputStream(tempFile).use { output ->
            output.write(jpegBytes)
        }
        val id = generatePendingImageId(fileName)
        return PendingVoiceImage(id, fileName, tempFile, jpegBytes)
    }

    private fun prepareBitmapForWeb(source: Bitmap): Bitmap {
        val maxSide = max(source.width, source.height)
        if (maxSide <= MAX_IMAGE_DIMENSION) {
            return source
        }
        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxSide.toFloat()
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun compressBitmapToJpeg(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        return output.toByteArray()
    }

    fun generateImageFileName(): String {
        val formatter = SimpleDateFormat("'post'yyyyMMddHHmmssSSS", Locale.US)
        return formatter.format(Date()) + ".jpg"
    }
}
