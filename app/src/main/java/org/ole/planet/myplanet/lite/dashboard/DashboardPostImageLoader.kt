/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-15
 */

package org.ole.planet.myplanet.lite.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class DashboardPostImageLoader(
    private val baseUrl: String,
    private val sessionCookie: String?,
    private val scope: CoroutineScope
) {

    private val client: OkHttpClient = OkHttpClient.Builder().build()
    private val cache = object : LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun bind(imageView: ImageView, imagePath: String, onResult: ((Boolean) -> Unit)? = null) {
        imageView.setImageDrawable(null)
        val cacheKey = imagePath
        val cached = cache.get(cacheKey)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            onResult?.invoke(true)
            return
        }
        imageView.tag = cacheKey
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { fetchImageBitmap(imagePath) }.getOrNull()
            }
            if (imageView.tag != cacheKey) {
                onResult?.invoke(bitmap != null)
                return@launch
            }
            if (bitmap != null) {
                cache.put(cacheKey, bitmap)
                imageView.visibility = View.VISIBLE
                imageView.setImageBitmap(bitmap)
                onResult?.invoke(true)
            } else {
                imageView.setImageDrawable(null)
                imageView.visibility = View.GONE
                onResult?.invoke(false)
            }
        }
    }

    private fun fetchImageBitmap(imagePath: String): Bitmap? {
        val requestUrl = resolveUrl(imagePath) ?: return null
        if (Uri.parse(requestUrl).scheme.equals("file", ignoreCase = true)) {
            val path = Uri.parse(requestUrl).path ?: return null
            val file = File(path)
            if (!file.exists()) return null
            return BitmapFactory.decodeFile(file.absolutePath)
        }
        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .get()
        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
            requestBuilder.addHeader("Cookie", cookie)
        }
        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                val bytes = response.body.bytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (error: IOException) {
            null
        }
    }

    private fun resolveUrl(path: String): String? {
        val trimmedPath = path.trim()
        if (trimmedPath.isEmpty()) {
            return null
        }
        val uriScheme = Uri.parse(trimmedPath).scheme?.lowercase()
        if (uriScheme == "http" || uriScheme == "https" || uriScheme == "file") {
            return trimmedPath
        }
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) {
            return null
        }
        val normalizedPath = trimmedPath.trimStart('/')
        val finalPath = when {
            normalizedPath.startsWith("db/") -> normalizedPath
            else -> "db/$normalizedPath"
        }
        return "$normalizedBase/$finalPath"
    }

    private companion object {
        private const val CACHE_SIZE_BYTES = 6 * 1024 * 1024 // 6MB cache for post images
    }
}
