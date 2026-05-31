package org.ole.planet.myplanet.lite.util

import android.content.Intent
import org.ole.planet.myplanet.lite.dashboard.DashboardServerCatalog

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

    fun extractDeepLinkSurvey(intent: Intent?): DeepLinkSurvey? {
        if (intent?.action != Intent.ACTION_VIEW) {
            return null
        }
        val data = intent.data ?: return null
        if (data.isOpaque) return null

        val scheme = data.scheme
        val host = data.host
        val segments = data.pathSegments

        val isWebSurvey = (scheme == "https" || scheme == "http") &&
            !host.isNullOrBlank() &&
            segments.firstOrNull()?.equals("survey", ignoreCase = true) == true
        val isCustomSurvey = scheme == "myplanetlite" &&
            host?.equals("survey", ignoreCase = true) == true
        if (!isWebSurvey && !isCustomSurvey) {
            return null
        }

        val baseUrl = if (isWebSurvey) {
            "${data.scheme}://${data.encodedAuthority}"
        } else {
            data.getQueryParameter("server").orEmpty().trim()
        }.let { DashboardServerCatalog.normalizeServerUrl(it) }

        val teamId = data.getQueryParameter("teamId")
            ?: segments.getOrNull(if (isWebSurvey) 1 else 0)
        val surveyId = data.getQueryParameter("surveyId")
            ?: segments.getOrNull(if (isWebSurvey) 2 else 1)

        val cleanTeamId = teamId?.takeIf { it.isNotBlank() && it.matches(ID_PATTERN) }
        val cleanSurveyId = surveyId?.takeIf { it.isNotBlank() && it.matches(ID_PATTERN) }
        if (baseUrl.isBlank() || cleanTeamId == null || cleanSurveyId == null) {
            return null
        }

        return DeepLinkSurvey(
            baseUrl = baseUrl,
            teamId = cleanTeamId,
            surveyId = cleanSurveyId,
        )
    }

    fun sameServer(left: String?, right: String?): Boolean {
        val normalizedLeft = normalizeServerKey(left)
        val normalizedRight = normalizeServerKey(right)
        return normalizedLeft != null && normalizedLeft == normalizedRight
    }

    private fun normalizeServerKey(value: String?): String? {
        val trimmed = DashboardServerCatalog.normalizeServerUrl(value.orEmpty())
            .takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            val uri = android.net.Uri.parse(trimmed)
            val scheme = uri.scheme?.lowercase()
            val authority = uri.authority?.lowercase()
            if (scheme.isNullOrBlank() || authority.isNullOrBlank()) {
                trimmed.lowercase()
            } else {
                "$scheme://$authority"
            }
        }.getOrDefault(trimmed.lowercase())
    }

    data class DeepLinkSurvey(
        val baseUrl: String,
        val teamId: String,
        val surveyId: String,
    )
}
