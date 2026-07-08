package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.ServerConnectivityRepository
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.util.DeviceUtils
import org.ole.planet.myplanet.lite.util.NetworkUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

internal fun DashboardCourseDetailsBottomSheet.recordCourseDescriptionVisit(
    courseId: String,
    title: String,
) {
    if ((activity as? DashboardActivity)?.isOfflineModeActive() == true || !NetworkUtils.isDeviceOnline(requireContext())) {
        return
    }
    val context = requireContext().applicationContext
    val safeCourseId = courseId.trim().takeIf { it.isNotEmpty() } ?: return
    val safeTitle = title.trim().takeIf { it.isNotEmpty() } ?: safeCourseId
    val baseUrl = DashboardServerPreferences.getServerBaseUrl(context) ?: return
    val requestUrl = buildCourseActivityUrl(baseUrl) ?: return
    val payload = buildCourseActivityPayload(context, safeCourseId, safeTitle) ?: return
    viewLifecycleOwner.lifecycleScope.launch {
        postCourseActivity(context, baseUrl, requestUrl, payload)
    }
}

private suspend fun postCourseActivity(
    context: Context,
    baseUrl: String,
    requestUrl: String,
    payload: JSONObject,
) {
    val repository = ServerConnectivityRepository(AuthDependencies.client, AuthDependencies.moshi)
    val serverReachable =
        withContext(Dispatchers.IO) {
            repository.checkServerConnectivity(baseUrl).reachable
        }
    if (!serverReachable) {
        return
    }
    val authService = AuthDependencies.provideAuthService(context, baseUrl)
    val sessionCookie = withContext(Dispatchers.IO) { authService.getStoredToken() } ?: return
    payload.put("session", sessionCookie)
    repository.recordCourseActivity(requestUrl, payload, sessionCookie)
}

private fun buildCourseActivityUrl(baseUrl: String): String? =
    baseUrl
        .toHttpUrlOrNull()
        ?.newBuilder()
        ?.addPathSegments("db/course_activities")
        ?.build()
        ?.toString()

private fun buildCourseActivityPayload(
    context: Context,
    courseId: String,
    title: String,
): JSONObject? {
    val credentials = ProfileCredentialsStore.getStoredCredentials(context) ?: return null
    val preferences = SecurePreferencesProvider.getServerPreferences(context)
    val androidId = preferences.getString(KEY_COURSE_ACTIVITY_DEVICE_ANDROID_ID, null)
    val customDeviceName = preferences.getString(KEY_COURSE_ACTIVITY_DEVICE_CUSTOM_NAME, null)

    return runCatching {
        JSONObject().apply {
            put("courseId", courseId)
            put("title", title)
            put("user", credentials.username)
            put("type", "visit")
            put("time", System.currentTimeMillis())
            put("createdOn", DashboardServerPreferences.getServerCode(context) ?: JSONObject.NULL)
            put("parentCode", DashboardServerPreferences.getServerParentCode(context) ?: JSONObject.NULL)
            put("androidId", androidId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("deviceName", DeviceUtils.getDeviceName())
            put("customDeviceName", customDeviceName?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        }
    }.getOrNull()
}

private const val KEY_COURSE_ACTIVITY_DEVICE_ANDROID_ID = "device_android_id"
private const val KEY_COURSE_ACTIVITY_DEVICE_CUSTOM_NAME = "device_custom_device_name"
