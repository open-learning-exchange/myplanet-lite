/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */
package org.ole.planet.myplanet.lite.dashboard
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.profile.AvatarUpdateNotifier
import org.ole.planet.myplanet.lite.profile.StoredCredentials
class DashboardAvatarLoader(
    private val baseUrl: String,
    private val sessionCookie: String?,
    private val credentials: StoredCredentials?,
    private val scope: CoroutineScope
) {
    private val client: OkHttpClient = OkHttpClient.Builder().build()
    private val cache = sharedCache
    private val avatarUpdateListener = AvatarUpdateNotifier.register(
        AvatarUpdateNotifier.Listener { username ->
            val cacheKey = username.lowercase(Locale.ROOT)
            cache.remove(cacheKey)
            synchronized(missingAvatars) { missingAvatars.remove(cacheKey) }
        }
    )
    init {
        // Clear "missing" skips whenever a loader is created so fresh base URLs or fixed
        // endpoints can attempt avatar fetches again.
        synchronized(missingAvatars) { missingAvatars.clear() }
    }
    fun bind(imageView: ImageView, username: String?, hasAvatar: Boolean) {
        imageView.setImageResource(R.drawable.ic_person_placeholder_24)
        if (username.isNullOrBlank()) {
            return
        }
        val cacheKey = username.lowercase(Locale.ROOT)
        synchronized(missingAvatars) {
            if (!hasAvatar && missingAvatars.contains(cacheKey)) {
                return
            }
        }
        val cached = cache.get(cacheKey)
        if (cached != null) {
            ImageViewCompat.setImageTintList(imageView, null)
            imageView.setImageBitmap(cached)
            return
        }
        imageView.tag = cacheKey
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { fetchAvatarBitmap(username) }.getOrNull()
            }
            if (bitmap != null && imageView.tag == cacheKey) {
                cache.put(cacheKey, bitmap)
                ImageViewCompat.setImageTintList(imageView, null)
                imageView.setImageBitmap(bitmap)
            } else if (!hasAvatar) {
                synchronized(missingAvatars) { missingAvatars.add(cacheKey) }
            }
        }
    }
    fun destroy() {
        AvatarUpdateNotifier.unregister(avatarUpdateListener)
    }
    private fun fetchAvatarBitmap(username: String): Bitmap? {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) {
            return null
        }
        val requestUrl = "$normalizedBase/db/_users/org.couchdb.user:$username/img"
        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .get()
        credentials?.let {
            requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
        }
        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
            requestBuilder.addHeader("Cookie", cookie)
        }
        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                val bytes = response.body.bytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bitmap
            }
        } catch (error: IOException) {
            null
        }
    }
    private companion object {
        private const val CACHE_SIZE_BYTES = 2 * 1024 * 1024 // 2MB cache for avatars
        private val missingAvatars = mutableSetOf<String>()
        private val sharedCache = object : LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount
            }
        }
    }
}
