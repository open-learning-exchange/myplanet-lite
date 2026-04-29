package org.ole.planet.myplanet.lite

import android.content.SharedPreferences
import android.os.Looper
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SignupActivityExceptionTest {

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
    fun `executeSignupRequest returns FAILED on Exception without showing redundant Toast`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        mockWebServer.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))

        val url = mockWebServer.url("/test").toString()

        val payload = org.json.JSONObject().apply { put("test", "test") }
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val method = SignupActivity::class.memberFunctions.find { it.name == "executeSignupRequest" }
        method?.isAccessible = true

        val deferred = async {
            method?.callSuspend(activity, url, payload, mediaType)
        }

        delay(100)

        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

        val result = deferred.await()

        assertEquals("FAILED", result.toString())
        // Verified that no toast is shown by executeSignupRequest itself
        // Note: the caller might show a toast, but here we are testing the internal method
        org.junit.Assert.assertNull("Expected no Toast to be shown by executeSignupRequest", ShadowToast.getLatestToast())
    }
}
