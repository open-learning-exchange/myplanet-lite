package org.ole.planet.myplanet.lite

import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import java.util.ArrayList
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardImagePreviewActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.ServerConnectivityRepository
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.util.DeviceUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

internal fun DashboardResourcesPageFragment.openResource(item: ResourceUi) {
    val mediaType = item.type.lowercase(Locale.ROOT)
    val resourceUri = downloadService.resolveResourceUri(
        baseUrl = DashboardServerPreferences.getServerBaseUrl(requireContext()),
        item = item
    )
    if (resourceUri.isNullOrBlank()) {
        Toast.makeText(requireContext(), getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
        return
    }
    recordResourceVisit(item)
    when {
        mediaType.contains("image") -> openImageResource(resourceUri)
        mediaType.contains("video") || mediaType.contains("audio") -> openMediaResource(resourceUri)
        else -> openPdfResource(resourceUri)
    }
}

private fun DashboardResourcesPageFragment.openImageResource(resourceUri: String) {
    val intent = Intent(requireContext(), DashboardImagePreviewActivity::class.java).apply {
        putStringArrayListExtra(
            DashboardImagePreviewActivity.EXTRA_IMAGE_PATHS,
            ArrayList(listOf(resourceUri))
        )
        putExtra(DashboardImagePreviewActivity.EXTRA_START_INDEX, 0)
    }
    startActivity(intent)
}

private fun DashboardResourcesPageFragment.openMediaResource(resourceUri: String) {
    val intent = FullscreenPlayerActivity.createIntent(
        context = requireContext(),
        mediaUrls = arrayListOf(resourceUri),
        startIndex = 0,
        startPositionMs = 0L,
        authorizationHeader = resolveAuthHeader()
    )
    startActivity(intent)
}

private fun DashboardResourcesPageFragment.openPdfResource(resourceUri: String) {
    val intent = FullscreenPdfActivity.createIntent(
        requireContext(),
        resourceUri,
        resolveAuthHeader()
    )
    startActivity(intent)
}

private fun DashboardResourcesPageFragment.recordResourceVisit(item: ResourceUi) {
    if ((activity as? DashboardActivity)?.isOfflineModeActive() == true) {
        return
    }
    val currentBaseUrl = DashboardServerPreferences.getServerBaseUrl(requireContext()) ?: return
    val requestUrl = buildResourceActivityUrl(currentBaseUrl) ?: return
    val payload = buildResourceActivityPayload(item) ?: return
    val authHeader = resolveAuthHeader() ?: return
    val repository = ServerConnectivityRepository(AuthDependencies.client, AuthDependencies.moshi)
    lifecycleScope.launch {
        val serverReachable =
            withContext(Dispatchers.IO) {
                repository.checkServerConnectivity(currentBaseUrl).reachable
            }
        if (!serverReachable) {
            return@launch
        }
        repository.recordResourceActivity(requestUrl, payload, authHeader)
    }
}

private fun buildResourceActivityUrl(baseUrl: String): String? =
    baseUrl
        .toHttpUrlOrNull()
        ?.newBuilder()
        ?.addPathSegments("db/resource_activities")
        ?.build()
        ?.toString()

private fun DashboardResourcesPageFragment.buildResourceActivityPayload(item: ResourceUi): JSONObject? {
    val context = requireContext().applicationContext
    val credentials = ProfileCredentialsStore.getStoredCredentials(context) ?: return null
    val resourceId = item.id.trim().takeIf { it.isNotEmpty() } ?: return null
    val preferences = SecurePreferencesProvider.getServerPreferences(context)
    val androidId = preferences.getString(KEY_RESOURCE_ACTIVITY_DEVICE_ANDROID_ID, null)
    val customDeviceName = preferences.getString(KEY_RESOURCE_ACTIVITY_DEVICE_CUSTOM_NAME, null)

    return runCatching {
        JSONObject().apply {
            put("resourceId", resourceId)
            put("title", item.name.ifBlank { item.filename })
            put("user", credentials.username)
            put("type", "visit")
            put("time", System.currentTimeMillis())
            put("createdOn", DashboardServerPreferences.getServerCode(context) ?: JSONObject.NULL)
            put("parentCode", DashboardServerPreferences.getServerParentCode(context) ?: JSONObject.NULL)
            put("url", "/resources/view/$resourceId")
            put("private", false)
            put("androidId", androidId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("deviceName", DeviceUtils.getDeviceName())
            put("customDeviceName", customDeviceName?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        }
    }.getOrNull()
}

private fun DashboardResourcesPageFragment.resolveAuthHeader(): String? {
    val credentials = ProfileCredentialsStore.getStoredCredentials(requireContext().applicationContext)
    return credentials?.let { Credentials.basic(it.username, it.password) }
}

private const val KEY_RESOURCE_ACTIVITY_DEVICE_ANDROID_ID = "device_android_id"
private const val KEY_RESOURCE_ACTIVITY_DEVICE_CUSTOM_NAME = "device_custom_device_name"
