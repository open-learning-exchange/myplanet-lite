package org.ole.planet.myplanet.lite.util

import android.content.Intent

object IntentUtils {
    fun extractDeepLinkPostId(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) {
            return null
        }
        val data = intent.data ?: return null
        val queryPostId = data.getQueryParameter("postId")
        if (!queryPostId.isNullOrBlank()) {
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
        return candidate.takeIf { it.isNotBlank() }
    }
}
