package org.ole.planet.myplanet.lite.util

import android.content.Intent

object IntentUtils {
    private val ID_PATTERN = Regex("^[a-zA-Z0-9_\\-:@.]+$")

    fun extractDeepLinkPostId(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) {
            return null
        }
        val data = intent.data ?: return null
        if (data.isOpaque) return null

        val scheme = data.scheme
        val host = data.host
        val isValidDeepLink = (scheme == "https" && host == "midominio.com" && data.path?.lowercase()?.startsWith("/post") == true) ||
                              (scheme == "myplanetlite" && host == "post")
        if (!isValidDeepLink) return null

        val queryPostId = data.getQueryParameter("postId")
        if (!queryPostId.isNullOrBlank() && queryPostId.matches(ID_PATTERN)) {
            return queryPostId
        }
        val segments = data.pathSegments
        if (segments.isEmpty()) {
            return null
        }
        val postIndex = segments.indexOfFirst { segment ->
            segment.equals("post", ignoreCase = true)
        }
        val candidate = when {
            postIndex >= 0 && postIndex + 1 < segments.size -> segments[postIndex + 1]
            else -> segments.last()
        }
        val id = candidate.takeIf { it.isNotBlank() }
        return if (id != null && id.matches(ID_PATTERN)) id else null
    }
}
