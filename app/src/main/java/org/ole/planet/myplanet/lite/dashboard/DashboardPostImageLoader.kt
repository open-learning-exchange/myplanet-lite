/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-15
 */

package org.ole.planet.myplanet.lite.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class DashboardPostImageLoader(
    private val baseUrl: String,
    private val sessionCookie: String?,
    private val scope: CoroutineScope,
    private val client: OkHttpClient = SharedBitmapDependencies.client,
    private val authorizationHeader: String? = null,
) {
    private val cache = sharedCache
    private val inFlightRequests = mutableMapOf<String, Deferred<Bitmap?>>()
    private var courseImageGeneration = 0

    fun bind(
        imageView: ImageView,
        imagePath: String,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        imageView.setImageDrawable(null)
        val requestKey = if (imagePath.trimStart('/').startsWith("courses/")) {
            "$imagePath#courseGeneration=$courseImageGeneration"
        } else {
            imagePath
        }
        val cached = cache.get(requestKey)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            onResult?.invoke(true)
            return
        }
        imageView.tag = requestKey
        scope.launch {
            val deferred =
                synchronized(inFlightRequests) {
                    inFlightRequests.getOrPut(requestKey) {
                        scope.async(Dispatchers.IO) {
                            fetchImageBitmapWithRetry(imagePath)
                        }
                    }
                }
            val bitmap =
                try {
                    deferred.await()
                } finally {
                    synchronized(inFlightRequests) {
                        if (inFlightRequests[requestKey] == deferred) {
                            inFlightRequests.remove(requestKey)
                        }
                    }
                }
            if (imageView.tag != requestKey) {
                onResult?.invoke(bitmap != null)
                return@launch
            }
            if (bitmap != null) {
                cache.put(requestKey, bitmap)
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

    fun invalidateCourseImages() {
        courseImageGeneration++
        val courseRequests = synchronized(inFlightRequests) {
            inFlightRequests
                .filterKeys { it.substringBefore("#courseGeneration=").trimStart('/').startsWith("courses/") }
                .also { requests -> requests.keys.forEach(inFlightRequests::remove) }
                .values
        }
        courseRequests.forEach { it.cancel() }
        evictCourseImages()
    }

    private suspend fun fetchImageBitmapWithRetry(imagePath: String): Bitmap? {
        val isCourseImage = imagePath.trimStart('/').startsWith("courses/")
        val attempts = if (isCourseImage) COURSE_IMAGE_FETCH_ATTEMPTS else 1
        repeat(attempts) { attempt ->
            val bitmap = runCatching { fetchImageBitmap(imagePath) }.getOrNull()
            if (bitmap != null) return bitmap
            if (attempt < attempts - 1) delay(COURSE_IMAGE_RETRY_DELAY_MS)
        }
        return null
    }

    private fun fetchImageBitmap(imagePath: String): Bitmap? {
        val requestUrl = resolveUrl(imagePath) ?: return null
        if (requestUrl.toUri().scheme.equals("file", ignoreCase = true)) {
            val path = requestUrl.toUri().path ?: return null
            val file = File(path)
            if (!file.exists()) return null
            return BitmapFactory.decodeFile(file.absolutePath)
        }
        val requestBuilder =
            Request
                .Builder()
                .url(requestUrl)
                .get()
                .header("Cache-Control", "no-cache")
        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
            requestBuilder.addHeader("Cookie", cookie)
        }
        authorizationHeader?.takeIf { it.isNotBlank() }?.let { authorization ->
            requestBuilder.header("Authorization", authorization)
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
        val uriScheme = trimmedPath.toUri().scheme?.lowercase()
        if (uriScheme == "http" || uriScheme == "https" || uriScheme == "file") {
            return trimmedPath
        }
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) {
            return null
        }
        val normalizedPath = trimmedPath.trimStart('/')
        val finalPath =
            when {
                normalizedPath.startsWith("db/") -> normalizedPath
                else -> "db/$normalizedPath"
            }
        return "$normalizedBase/$finalPath"
    }

    companion object {
        private const val CACHE_SIZE_BYTES = 6 * 1024 * 1024 // 6MB cache for post images
        private const val COURSE_IMAGE_FETCH_ATTEMPTS = 3
        private const val COURSE_IMAGE_RETRY_DELAY_MS = 500L
        private val sharedCache =
            object : LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
                override fun sizeOf(
                    key: String,
                    value: Bitmap,
                ): Int = value.byteCount
            }

        fun evictCourseImages() {
            sharedCache.snapshot().keys
                .filter { it.substringBefore("#courseGeneration=").trimStart('/').startsWith("courses/") }
                .forEach(sharedCache::remove)
        }
    }
}
