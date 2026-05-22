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

            // Create a completely uninitialized instance using reflection (or Unsafe) to avoid KeyStore initialization in onCreate/init
            // Using a mocked ProfileActivity allows us to avoid the initialization sequence and just call the method we want
            val activity = Mockito.mock(ProfileActivity::class.java)
            Mockito.`when`(activity.executeProfileUpdateRequest(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                org.mockito.kotlin.any(),
                Mockito.nullable(ByteArray::class.java),
                Mockito.nullable(ByteArray::class.java)
            )).thenCallRealMethod()

            // However, executeProfileUpdateRequest uses httpClient which is lazy.
            // When thenCallRealMethod is used, it will try to evaluate httpClient.
            // For lazy properties, it reads from a hidden delegate field
            try {
                val delegateField = ProfileActivity::class.java.getDeclaredField("httpClient\$delegate")
                delegateField.isAccessible = true
                val lazyInstance = lazy { OkHttpClient() }
                delegateField.set(activity, lazyInstance)
            } catch (e: Exception) {
                // If this fails, we can also inject an OkHttpClient via reflection or just use the mock if it works
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
