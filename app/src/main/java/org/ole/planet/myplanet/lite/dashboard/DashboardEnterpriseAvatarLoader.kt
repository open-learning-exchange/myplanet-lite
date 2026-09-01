package org.ole.planet.myplanet.lite.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class DashboardEnterpriseAvatarLoader(
    private val baseUrl: String,
    private val sessionCookie: String?,
    private val credentials: StoredCredentials?,
    private val scope: CoroutineScope,
    private val localFallback: DashboardAvatarLoader,
    private val client: OkHttpClient = SharedBitmapDependencies.client,
) {
    fun bind(imageView: ImageView, userId: String?, userPlanetCode: String?, username: String?) {
        imageView.setImageResource(R.drawable.ic_person_placeholder_24)
        if (userId.isNullOrBlank() || userPlanetCode.isNullOrBlank()) {
            localFallback.bind(imageView, username, true)
            return
        }
        val cacheKey = "$userId@$userPlanetCode"
        cache.get(cacheKey)?.let { bitmap ->
            ImageViewCompat.setImageTintList(imageView, null)
            imageView.setImageBitmap(bitmap)
            return
        }
        imageView.tag = cacheKey
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { fetchSyncedAvatar(userId, userPlanetCode) }.getOrNull()
            }
            if (bitmap != null && imageView.tag == cacheKey) {
                cache.put(cacheKey, bitmap)
                ImageViewCompat.setImageTintList(imageView, null)
                imageView.setImageBitmap(bitmap)
            } else if (imageView.tag == cacheKey) {
                localFallback.bind(imageView, username, true)
            }
        }
    }

    private fun fetchSyncedAvatar(userId: String, planetCode: String): Bitmap? {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) return null
        val documentId = "$userId@$planetCode"
        val documentUrl = "$normalizedBase/db/attachments".toHttpUrl().newBuilder()
            .addPathSegment(documentId)
            .build()
        val document = execute(documentUrl.toString()) ?: return null
        val attachments = JSONObject(document).optJSONObject("_attachments") ?: return null
        val attachmentName = attachments.keys().asSequence().firstOrNull() ?: return null
        val imageUrl = "$normalizedBase/db/attachments".toHttpUrl().newBuilder()
            .addPathSegment(documentId)
            .addPathSegment(attachmentName)
            .build()
        val bytes = executeBytes(imageUrl.toString()) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun execute(url: String): String? = executeRequest(url) { response -> response.body.string() }

    private fun executeBytes(url: String): ByteArray? = executeRequest(url) { response -> response.body.bytes() }

    private fun <T> executeRequest(url: String, body: (okhttp3.Response) -> T): T? {
        val builder = Request.Builder().url(url).get()
        credentials?.let { builder.header("Authorization", Credentials.basic(it.username, it.password)) }
        sessionCookie?.takeIf(String::isNotBlank)?.let { builder.header("Cookie", it) }
        return client.newCall(builder.build()).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            body(response)
        }
    }

    private companion object {
        val cache = LruCache<String, Bitmap>(40)
    }
}
