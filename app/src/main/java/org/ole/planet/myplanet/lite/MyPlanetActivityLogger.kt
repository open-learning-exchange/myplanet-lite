package org.ole.planet.myplanet.lite

import android.content.Context
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.ole.planet.myplanet.lite.dashboard.ServerConnectivityRepository
import org.ole.planet.myplanet.lite.util.ApplicationScope
import org.ole.planet.myplanet.lite.util.DeviceUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.ole.planet.myplanet.lite.util.putPlanetAppId

/*
 * Registers this device in Planet's `myplanet_activities` database so it appears on
 * the manager dashboard's myPlanet page. Mirrors the `type: "sync"` document myPlanet
 * posts, tagged with this app's identity so reports attribute the device correctly.
 */
object MyPlanetActivityLogger {
    private const val KEY_DEVICE_ANDROID_ID = "device_android_id"
    private const val KEY_DEVICE_UNIQUE_ANDROID_ID = "device_unique_android_id"
    private const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"

    /*
     * Posts the sync document without blocking the caller. The activities that record a
     * sync finish themselves right afterwards, so the work runs on the application scope
     * rather than a lifecycle scope that would cancel it mid-flight.
     */
    fun postSyncActivity(
        context: Context,
        baseUrl: String,
        repository: ServerConnectivityRepository,
        sessionCookie: String?,
    ) {
        val applicationContext = context.applicationContext
        ApplicationScope.io.launch {
            recordSyncActivity(applicationContext, baseUrl, repository, sessionCookie)
        }
    }

    internal suspend fun recordSyncActivity(
        context: Context,
        baseUrl: String,
        repository: ServerConnectivityRepository,
        sessionCookie: String?,
    ) {
        val requestUrl = buildSyncActivityUrl(baseUrl) ?: return
        val payload = buildSyncActivityPayload(context) ?: return
        repository.recordMyPlanetActivity(requestUrl, payload, sessionCookie)
    }

    internal fun buildSyncActivityUrl(baseUrl: String): String? =
        baseUrl
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("db/myplanet_activities")
            ?.build()
            ?.toString()

    internal fun buildSyncActivityPayload(context: Context): JSONObject? {
        return runCatching {
            val preferences = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
            val androidId = preferences.getString(KEY_DEVICE_ANDROID_ID, null)
            val uniqueAndroidId = preferences.getString(KEY_DEVICE_UNIQUE_ANDROID_ID, null)
            val customDeviceName = preferences.getString(KEY_DEVICE_CUSTOM_DEVICE_NAME, null)
            val code = preferences.getString(KEY_SERVER_CODE, null)
            val parentCode = preferences.getString(KEY_SERVER_PARENT_CODE, null)

            JSONObject().apply {
                put("type", "sync")
                put("versionName", BuildConfig.VERSION_NAME)
                put("version", BuildConfig.VERSION_CODE)
                put("androidId", androidId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("uniqueAndroidId", uniqueAndroidId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("deviceName", DeviceUtils.getDeviceName())
                put("customDeviceName", customDeviceName?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("createdOn", code?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("parentCode", parentCode?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("time", System.currentTimeMillis())
                putPlanetAppId()
            }
        }.getOrNull()
    }
}
