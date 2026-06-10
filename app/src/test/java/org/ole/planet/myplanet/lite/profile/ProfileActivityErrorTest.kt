package org.ole.planet.myplanet.lite.profile

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], instrumentedPackages = ["androidx.loader.content"])
class ProfileActivityErrorTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testExecuteProfileUpdateRequest_ReturnsFalseOnHttpError() {
        runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            val baseUrl = mockWebServer.url("/").toString().removeSuffix("/")

            val activity = Mockito.mock(ProfileActivity::class.java)
            Mockito.`when`(activity.executeProfileUpdateRequest(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                org.mockito.kotlin.any(),
                Mockito.nullable(ByteArray::class.java),
                Mockito.nullable(ByteArray::class.java)
            )).thenCallRealMethod()

            try {
                val delegateField = ProfileActivity::class.java.getDeclaredField("httpClient\$delegate")
                delegateField.isAccessible = true
                val lazyInstance = lazy { OkHttpClient() }
                delegateField.set(activity, lazyInstance)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val result = activity.executeProfileUpdateRequest(
                baseUrl,
                "testuser",
                "cookie",
                JSONObject(),
                null,
                null
            )
            assertFalse(result)
        }
    }

    @Test
    fun testExecuteProfileUpdateRequest_ReturnsFalseOnException() {
        runBlocking {
            mockWebServer.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
            val baseUrl = mockWebServer.url("/").toString().removeSuffix("/")

            val activity = Mockito.mock(ProfileActivity::class.java)
            Mockito.`when`(activity.executeProfileUpdateRequest(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                org.mockito.kotlin.any(),
                Mockito.nullable(ByteArray::class.java),
                Mockito.nullable(ByteArray::class.java)
            )).thenCallRealMethod()

            try {
                val delegateField = ProfileActivity::class.java.getDeclaredField("httpClient\$delegate")
                delegateField.isAccessible = true
                val lazyInstance = lazy { OkHttpClient() }
                delegateField.set(activity, lazyInstance)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val result = activity.executeProfileUpdateRequest(
                baseUrl,
                "testuser",
                "cookie",
                JSONObject(),
                null,
                null
            )
            assertFalse(result)
        }
    }
}
