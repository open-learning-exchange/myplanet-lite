package org.ole.planet.myplanet.lite.dashboard

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
        val original = decodeScaledBitmap(uri, contentResolver)
            ?: throw IllegalArgumentException("Unable to decode image stream")
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

    private fun decodeScaledBitmap(uri: Uri, contentResolver: ContentResolver): Bitmap? {
        val bounds = contentResolver.openInputStream(uri)?.use { input ->
            readImageBounds(input)
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = bounds?.let {
                calculateInSampleSize(it.first, it.second, MAX_IMAGE_DIMENSION)
            } ?: 1
        }
        return contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
                ?.let { bitmap -> prepareBitmapForWeb(bitmap, bounds) }
        }
    }

    private fun prepareBitmapForWeb(source: Bitmap): Bitmap {
        return prepareBitmapForWeb(source, source.width to source.height)
    }

    private fun prepareBitmapForWeb(source: Bitmap, originalBounds: Pair<Int, Int>?): Bitmap {
        val originalWidth = originalBounds?.first ?: source.width
        val originalHeight = originalBounds?.second ?: source.height
        val originalMaxSide = max(originalWidth, originalHeight)
        if (originalMaxSide <= MAX_IMAGE_DIMENSION) {
            return source
        }
        val scale = MAX_IMAGE_DIMENSION.toFloat() / originalMaxSide.toFloat()
        val width = (originalWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (originalHeight * scale).roundToInt().coerceAtLeast(1)
        if (source.width == width && source.height == height) {
            return source
        }
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun compressBitmapToJpeg(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        return output.toByteArray()
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0) {
            return 1
        }
        var inSampleSize = 1
        while (max(width / inSampleSize, height / inSampleSize) > maxDimension) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun readImageBounds(input: InputStream): Pair<Int, Int>? {
        val header = ByteArray(32)
        val count = input.read(header)
        if (count < 24) {
            return null
        }
        return readPngBounds(header) ?: readJpegBounds(header, count, input)
    }

    private fun readPngBounds(header: ByteArray): Pair<Int, Int>? {
        val isPng = header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte()
        if (!isPng) {
            return null
        }
        return readIntBigEndian(header, 16) to readIntBigEndian(header, 20)
    }

    private fun readJpegBounds(header: ByteArray, headerCount: Int, input: InputStream): Pair<Int, Int>? {
        if (headerCount < 2 || header[0] != 0xFF.toByte() || header[1] != 0xD8.toByte()) {
            return null
        }
        val reader = HeaderInputReader(header, headerCount, input, initialIndex = 2)
        while (true) {
            var markerStart = reader.read()
            while (markerStart != -1 && markerStart != 0xFF) {
                markerStart = reader.read()
            }
            if (markerStart == -1) {
                return null
            }
            val marker = reader.read()
            if (marker == -1) {
                return null
            }
            if (marker == 0xD9 || marker == 0xDA) {
                return null
            }
            val length = reader.readUnsignedShort() ?: return null
            if (length < 2) {
                return null
            }
            if (!isJpegStartOfFrame(marker)) {
                if (!reader.skip(length - 2)) {
                    return null
                }
                continue
            }
            if (reader.read() == -1) {
                return null
            }
            val height = reader.readUnsignedShort() ?: return null
            val width = reader.readUnsignedShort() ?: return null
            return width to height
        }
    }

    private fun isJpegStartOfFrame(marker: Int): Boolean {
        return marker in 0xC0..0xCF && marker !in setOf(0xC4, 0xC8, 0xCC)
    }

    private fun readIntBigEndian(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    fun generateImageFileName(): String {
        val formatter = SimpleDateFormat("'post'yyyyMMddHHmmssSSS", Locale.US)
        return formatter.format(Date()) + ".jpg"
    }

    private class HeaderInputReader(
        private val header: ByteArray,
        private val headerCount: Int,
        private val input: InputStream,
        initialIndex: Int
    ) {
        private var index = initialIndex

        fun read(): Int {
            return if (index < headerCount) {
                header[index++].toInt() and 0xFF
            } else {
                input.read()
            }
        }

        fun readUnsignedShort(): Int? {
            val first = read()
            val second = read()
            if (first == -1 || second == -1) {
                return null
            }
            return (first shl 8) or second
        }

        fun skip(byteCount: Int): Boolean {
            repeat(byteCount) {
                if (read() == -1) {
                    return false
                }
            }
            return true
        }
    }
}
