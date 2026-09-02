package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthService
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CourseActivityLoggerTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        SecurePreferencesProvider.injectedPreferences = prefs

        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.injectedPreferences = null
        ProfileCredentialsStore.setSessionCredentials(null)
        server.shutdown()
        AuthDependencies.resetForTesting()
    }

    @Test
    fun testBuildCourseActivityUrl() {
        val url = buildCourseActivityUrl("http://example.com")
        assertEquals("http://example.com/db/course_activities", url)
    }

    @Test
    fun testBuildCourseActivityUrlWithTrailingSlash() {
        val url = buildCourseActivityUrl("http://example.com/")
        assertEquals("http://example.com/db/course_activities", url)
    }

    @Test
    fun testBuildCourseActivityUrlInvalidUrl() {
        val url = buildCourseActivityUrl("invalid_url")
        assertNull(url)
    }

    @Test
    fun testBuildCourseActivityPayload() {
        val prefs = SecurePreferencesProvider.injectedPreferences!!
        prefs.edit()
            .putString("device_android_id", "test_android_id")
            .putString("device_custom_device_name", "test_custom_name")
            .putString("server_code", "test_code")
            .putString("server_parent_code", "test_parent_code")
            .apply()

        ProfileCredentialsStore.setSessionCredentials(StoredCredentials("test_user", "password"))

        val payload = buildCourseActivityPayload(context, "course_123", "Course Title")

        assertNotNull(payload)
        assertEquals("course_123", payload?.getString("courseId"))
        assertEquals("Course Title", payload?.getString("title"))
        assertEquals("test_user", payload?.getString("user"))
        assertEquals("visit", payload?.getString("type"))
        assertEquals("test_code", payload?.getString("createdOn"))
        assertEquals("test_parent_code", payload?.getString("parentCode"))
        assertEquals("test_android_id", payload?.getString("androidId"))
        assertEquals("test_custom_name", payload?.getString("customDeviceName"))
        assertEquals("myplanet-lite", payload?.getString("app"))
        assertNotNull(payload?.getLong("time"))
        assertNotNull(payload?.getString("deviceName"))
    }

    @Test
    fun testBuildCourseActivityPayloadWithNullValues() {
        val prefs = SecurePreferencesProvider.injectedPreferences!!
        prefs.edit().clear().apply() // Ensure no preferences are set

        ProfileCredentialsStore.setSessionCredentials(StoredCredentials("test_user", "password"))

        val payload = buildCourseActivityPayload(context, "course_123", "Course Title")

        assertNotNull(payload)
        assertEquals("course_123", payload?.getString("courseId"))
        assertEquals("Course Title", payload?.getString("title"))
        assertEquals("test_user", payload?.getString("user"))
        assertEquals("visit", payload?.getString("type"))
        // Check JSONObject.NULL serialization correctly
        assertEquals(JSONObject.NULL, payload?.get("createdOn"))
        assertEquals(JSONObject.NULL, payload?.get("parentCode"))
        assertEquals(JSONObject.NULL, payload?.get("androidId"))
        assertEquals(JSONObject.NULL, payload?.get("customDeviceName"))
        assertNotNull(payload?.getLong("time"))
        assertNotNull(payload?.getString("deviceName"))
    }

    @Test
    fun testBuildCourseActivityPayloadWithBlankValues() {
        val prefs = SecurePreferencesProvider.injectedPreferences!!
        prefs.edit()
            .putString("device_android_id", "  ")
            .putString("device_custom_device_name", "")
            .putString("server_code", "")
            .putString("server_parent_code", "  ")
            .apply() // Blank strings should be treated as null

        ProfileCredentialsStore.setSessionCredentials(StoredCredentials("test_user", "password"))

        val payload = buildCourseActivityPayload(context, "course_123", "Course Title")

        assertNotNull(payload)
        assertEquals("course_123", payload?.getString("courseId"))
        assertEquals("Course Title", payload?.getString("title"))
        assertEquals(JSONObject.NULL, payload?.get("createdOn"))
        assertEquals(JSONObject.NULL, payload?.get("parentCode"))
        assertEquals(JSONObject.NULL, payload?.get("androidId"))
        assertEquals(JSONObject.NULL, payload?.get("customDeviceName"))
    }

    @Test
    fun testBuildCourseActivityPayloadNullCredentials() {
        ProfileCredentialsStore.setSessionCredentials(null)

        val payload = buildCourseActivityPayload(context, "course_123", "Course Title")
        assertNull(payload)
    }

    @Test
    fun testPostCourseActivity() = runBlocking {
        val baseUrl = server.url("/").toString()
        val requestUrl = server.url("/db/course_activities").toString()

        server.enqueue(MockResponse().setResponseCode(200).setBody("")) // Connectivity check
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // POST request

        val authService = mock<AuthService>()
        whenever(authService.getStoredToken()).thenReturn("test_cookie")
        AuthDependencies.overrideAuthService(authService)

        val payload = JSONObject().apply {
            put("test_key", "test_value")
        }

        postCourseActivity(context, baseUrl, requestUrl, payload)

        // Assert connectivity check request
        val request1 = server.takeRequest()
        assertEquals("/db/configurations/_all_docs?include_docs=true", request1.path)
        assertEquals("GET", request1.method)

        // Assert POST activity request
        val request2 = server.takeRequest()
        assertEquals("/db/course_activities", request2.path)
        assertEquals("POST", request2.method)
        assertEquals("test_cookie", request2.getHeader("Cookie"))

        val bodyString = request2.body.readUtf8()
        val receivedPayload = JSONObject(bodyString)
        assertEquals("test_value", receivedPayload.getString("test_key"))
        assertEquals("test_cookie", receivedPayload.getString("session"))
    }


    @Test
    fun testPostCourseActivityServerUnreachable() = runBlocking {
        val baseUrl = server.url("/").toString()
        val requestUrl = server.url("/db/course_activities").toString()

        server.enqueue(MockResponse().setResponseCode(500).setBody("")) // Connectivity check fails

        val payload = JSONObject().apply {
            put("test_key", "test_value")
        }

        postCourseActivity(context, baseUrl, requestUrl, payload)

        val request1 = server.takeRequest()
        assertEquals("/db/configurations/_all_docs?include_docs=true", request1.path)
        assertEquals(0, server.requestCount - 1)
    }

    @Test
    fun testPostCourseActivityNoToken() = runBlocking {
        val baseUrl = server.url("/").toString()
        val requestUrl = server.url("/db/course_activities").toString()

        server.enqueue(MockResponse().setResponseCode(200).setBody("")) // Connectivity check

        val authService = mock<AuthService>()
        whenever(authService.getStoredToken()).thenReturn(null) // Token is null
        AuthDependencies.overrideAuthService(authService)

        val payload = JSONObject().apply {
            put("test_key", "test_value")
        }

        postCourseActivity(context, baseUrl, requestUrl, payload)

        val request1 = server.takeRequest()
        assertEquals("/db/configurations/_all_docs?include_docs=true", request1.path)
        assertEquals(0, server.requestCount - 1)
    }

}
