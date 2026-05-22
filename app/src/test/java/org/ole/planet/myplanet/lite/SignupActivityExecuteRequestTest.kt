package org.ole.planet.myplanet.lite

import android.content.SharedPreferences
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SignupActivityExecuteRequestTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val mockPrefs = mock(SharedPreferences::class.java)
        SecurePreferencesProvider.injectedPreferences = mockPrefs

        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SecurePreferencesProvider.injectedPreferences = null
        mockWebServer.shutdown()
    }

    @Test
    fun `executeSignupRequest returns SUCCESS for 200 code`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val url = mockWebServer.url("/test").toString()
        val payload = org.json.JSONObject().apply { put("test", "test") }
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val deferred = async {
            activity.executeSignupRequest(url, payload, mediaType)
        }

        delay(10)
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

        val result = deferred.await()
        assertEquals("SUCCESS", result.toString())
    }

    @Test
    fun `executeSignupRequest returns USERNAME_TAKEN for 409 code`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        mockWebServer.enqueue(MockResponse().setResponseCode(409))

        val url = mockWebServer.url("/test").toString()
        val payload = org.json.JSONObject().apply { put("test", "test") }
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val deferred = async {
            activity.executeSignupRequest(url, payload, mediaType)
        }

        delay(10)
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

        val result = deferred.await()
        assertEquals("USERNAME_TAKEN", result.toString())
    }

    @Test
    fun `executeSignupRequest returns FAILED for 500 code`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val url = mockWebServer.url("/test").toString()
        val payload = org.json.JSONObject().apply { put("test", "test") }
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val deferred = async {
            activity.executeSignupRequest(url, payload, mediaType)
        }

        delay(10)
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

        val result = deferred.await()
        assertEquals("FAILED", result.toString())
    }

    @Test
    fun `executeSignupRequest rethrows CancellationException`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        mockWebServer.enqueue(MockResponse().setHeadersDelay(1, java.util.concurrent.TimeUnit.SECONDS))

        val url = mockWebServer.url("/test").toString()
        val payload = org.json.JSONObject().apply { put("test", "test") }
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val deferred = async {
            try {
                activity.executeSignupRequest(url, payload, mediaType)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                } else {
                    fail("Expected CancellationException but got ${e::class.java.simpleName}")
                }
            }
        }

        // Wait briefly for execution to begin
        delay(50)

        // Cancel the coroutine to trigger CancellationException
        deferred.cancel()

        try {
            deferred.await()
            fail("Expected CancellationException")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Passed - CancellationException was properly rethrown and caught
        }
    }
}
