package org.ole.planet.myplanet.lite.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Method
import org.robolectric.Robolectric
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ProfileActivityRobolectricTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var activity: ProfileActivity

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        activity = Robolectric.buildActivity(ProfileActivity::class.java).get()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private suspend fun invokeFetchRemoteProfileDocument(serverUrl: String): Any? {
        val fetchMethod: Method = ProfileActivity::class.java.getDeclaredMethod(
            "fetchRemoteProfileDocument",
            String::class.java,
            String::class.java,
            String::class.java,
            kotlin.coroutines.Continuation::class.java
        )
        fetchMethod.isAccessible = true

        return suspendCancellableCoroutine { continuation ->
            try {
                val invokeResult = fetchMethod.invoke(activity, serverUrl, "testuser", "testcookie", continuation)
                if (invokeResult !== COROUTINE_SUSPENDED) {
                    continuation.resume(invokeResult)
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    @Test
    fun testFetchRemoteProfileDocument_failureReturnsNull() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Server error"))
        val serverUrl = mockWebServer.url("/").toString().removeSuffix("/")

        val result = invokeFetchRemoteProfileDocument(serverUrl)

        assertNull(result)
    }

    @Test
    fun testFetchRemoteProfileDocument_emptyBodyReturnsNull() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val serverUrl = mockWebServer.url("/").toString().removeSuffix("/")

        val result = invokeFetchRemoteProfileDocument(serverUrl)

        assertNull(result)
    }

    @Test
    fun testFetchRemoteProfileDocument_exceptionReturnsNull() = runBlocking {
        // Shut down the server to simulate a network exception (IOException) when attempting to connect
        val serverUrl = mockWebServer.url("/").toString().removeSuffix("/")
        mockWebServer.shutdown()

        val result = invokeFetchRemoteProfileDocument(serverUrl)

        assertNull(result)
    }

    @Test
    fun testFetchRemoteProfileDocument_successReturnsJsonObject() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"_id\":\"org.couchdb.user:testuser\",\"name\":\"testuser\"}"))
        val serverUrl = mockWebServer.url("/").toString().removeSuffix("/")

        val result = invokeFetchRemoteProfileDocument(serverUrl)

        assertNotNull(result)
        val json = result as org.json.JSONObject
        assertEquals("testuser", json.getString("name"))
    }
}
