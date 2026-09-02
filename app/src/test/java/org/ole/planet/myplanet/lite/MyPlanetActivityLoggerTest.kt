package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.ServerConnectivityRepository
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MyPlanetActivityLoggerTest {

    private lateinit var context: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockPrefs = context.getSharedPreferences("myplanet_activity_prefs", Context.MODE_PRIVATE)
        mockPrefs.edit().clear().commit()
        SecurePreferencesProvider.injectedPreferences = mockPrefs
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.resetForTesting()
        mockWebServer.shutdown()
    }

    @Test
    fun `test sync activity url targets myplanet_activities database`() {
        val url = MyPlanetActivityLogger.buildSyncActivityUrl("https://planet.example.com/")

        assertEquals("https://planet.example.com/db/myplanet_activities", url)
    }

    @Test
    fun `test sync activity url is null for blank or invalid base url`() {
        assertNull(MyPlanetActivityLogger.buildSyncActivityUrl("   "))
        assertNull(MyPlanetActivityLogger.buildSyncActivityUrl("not a url"))
    }

    @Test
    fun `test sync activity payload carries device identity and app field`() {
        mockPrefs
            .edit()
            .putString("device_android_id", "android-1")
            .putString("device_unique_android_id", "unique-1")
            .putString("device_custom_device_name", "Classroom Tablet")
            .putString("server_code", "earth")
            .putString("server_parent_code", "solar")
            .commit()

        val payload = MyPlanetActivityLogger.buildSyncActivityPayload(context)

        assertNotNull(payload)
        val document = requireNotNull(payload)
        assertEquals("sync", document.getString("type"))
        assertEquals("myplanet-lite", document.getString("app"))
        assertEquals(BuildConfig.VERSION_NAME, document.getString("versionName"))
        assertEquals(BuildConfig.VERSION_CODE, document.getInt("version"))
        assertEquals("android-1", document.getString("androidId"))
        assertEquals("unique-1", document.getString("uniqueAndroidId"))
        assertEquals("Classroom Tablet", document.getString("customDeviceName"))
        assertEquals("earth", document.getString("createdOn"))
        assertEquals("solar", document.getString("parentCode"))
        assertTrue(document.getString("deviceName").isNotBlank())
        assertTrue(document.get("time") is Number)
    }

    @Test
    fun `test sync activity payload nulls out missing device identifiers`() {
        val document = requireNotNull(MyPlanetActivityLogger.buildSyncActivityPayload(context))

        assertTrue(document.isNull("androidId"))
        assertTrue(document.isNull("uniqueAndroidId"))
        assertTrue(document.isNull("customDeviceName"))
        assertTrue(document.isNull("createdOn"))
        assertTrue(document.isNull("parentCode"))
        assertEquals("myplanet-lite", document.getString("app"))
    }

    @Test
    fun `test record sync activity posts document with session cookie`() = runTest {
        mockPrefs.edit().putString("device_android_id", "android-1").commit()
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("{\"ok\":true}"))
        val repository = ServerConnectivityRepository(OkHttpClient(), Moshi.Builder().build())

        MyPlanetActivityLogger.recordSyncActivity(
            context,
            mockWebServer.url("/").toString(),
            repository,
            "AuthSession=test-cookie",
        )

        val request = mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("POST", request?.method)
        assertEquals("/db/myplanet_activities", request?.requestUrl?.encodedPath)
        assertEquals("AuthSession=test-cookie", request?.getHeader("Cookie"))

        val body = JSONObject(request?.body?.readUtf8().orEmpty())
        assertEquals("sync", body.getString("type"))
        assertEquals("myplanet-lite", body.getString("app"))
        assertEquals("android-1", body.getString("androidId"))
    }
}
