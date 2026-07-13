/*
 * Author: Walfre Lopez Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-08
 */

package org.ole.planet.myplanet.lite.profile

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.R
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

internal fun ProfileActivity.launchAvatarPickerImpl() {
    selectAvatarLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )
}

internal fun ProfileActivity.processAvatarSelectionImpl(uri: Uri) {
    val destinationFile = runCatching { File.createTempFile("avatar_crop", ".jpg", cacheDir) }.getOrNull()
    if (destinationFile == null) {
        Toast.makeText(this, R.string.profile_avatar_picker_error, Toast.LENGTH_SHORT).show()
        return
    }
    val destinationUri = Uri.fromFile(destinationFile)
    val options =
        UCrop.Options().apply {
            setToolbarTitle(getString(R.string.profile_avatar_crop_title))
            setHideBottomControls(true)
            setFreeStyleCropEnabled(false)
            val white = resources.getColor(R.color.white, theme)
            val blue = resources.getColor(R.color.blueOle, theme)
            setToolbarColor(white)
            setStatusBarColor(white)
            setToolbarWidgetColor(blue)
            setActiveControlsWidgetColor(blue)
        }
    val targetSize = resources.getDimensionPixelSize(R.dimen.profile_avatar_max_image_size).takeIf { it > 0 } ?: 512
    val intent =
        UCrop
            .of(uri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(targetSize, targetSize)
            .withOptions(options)
            .getIntent(this)
    val canHandleCrop =
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    if (!canHandleCrop) {
        destinationFile.delete()
        Toast.makeText(this, R.string.profile_avatar_picker_error, Toast.LENGTH_SHORT).show()
        return
    }
    cropAvatarLauncher.launch(intent)
}

internal fun ProfileActivity.handleCroppedAvatarImpl(uri: Uri) {
    val activity = this
    lifecycleScope.launch {
        val bitmap =
            withContext(Dispatchers.IO) {
                loadScaledAvatar(uri)
            }
        if (bitmap != null) {
            applyAvatarBitmapImpl(bitmap, markForUpload = true)
        } else {
            Toast
                .makeText(
                    activity,
                    R.string.profile_avatar_picker_error,
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }
}

private fun ProfileActivity.loadScaledAvatar(uri: Uri): Bitmap? {
    return try {
        val resolver = contentResolver
        val targetSize =
            resources.getDimensionPixelSize(R.dimen.profile_avatar_max_image_size).takeIf { it > 0 }
                ?: 512
        val boundsOptions =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        val decoded =
            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
                val decodeOptions =
                    BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(boundsOptions, targetSize, targetSize)
                    }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            } ?: return null
        val square = cropToSquare(decoded)
        if (square !== decoded) {
            decoded.recycle()
        }
        if (square.width > targetSize) {
            Bitmap.createScaledBitmap(square, targetSize, targetSize, true).also { scaled ->
                if (scaled !== square) {
                    square.recycle()
                }
            }
        } else {
            square
        }
    } catch (ex: Exception) {
        null
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    var inSampleSize = 1
    val height = options.outHeight
    val width = options.outWidth
    if (height > reqHeight || width > reqWidth) {
        var halfHeight = height / 2
        var halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun cropToSquare(bitmap: Bitmap): Bitmap {
    val size = min(bitmap.width, bitmap.height)
    val xOffset = (bitmap.width - size) / 2
    val yOffset = (bitmap.height - size) / 2
    return Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
}

internal fun ProfileActivity.applyAvatarBitmapImpl(
    bitmap: Bitmap?,
    markForUpload: Boolean = false,
) {
    if (bitmap != null) {
        if (markForUpload) {
            pendingAvatarUpload = compressAvatar(bitmap)
        }
        avatarCircleView.setImageBitmap(bitmap)
        avatarSquareView.setImageBitmap(bitmap)
    } else {
        if (markForUpload) {
            pendingAvatarUpload = null
        }
        avatarCircleView.setImageDrawable(null)
        avatarSquareView.setImageDrawable(null)
    }
}

internal fun decodeAvatarBytesImpl(bytes: ByteArray?): Bitmap? {
    if (bytes == null || bytes.isEmpty()) {
        return null
    }
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (ex: Exception) {
        null
    }
}

private fun compressAvatar(bitmap: Bitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    return stream.toByteArray()
}
