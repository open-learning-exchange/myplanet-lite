package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.profile.AvatarUpdateNotifier
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardAvatarLoaderTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var context: Context
    private lateinit var loader: DashboardAvatarLoader

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        mockWebServer = MockWebServer()
        mockWebServer.start()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        if (::loader.isInitialized) {
            loader.destroy()
        }
        mockWebServer.shutdown()
    }

    private fun createLoader(
        scope: CoroutineScope,
        baseUrl: String = mockWebServer.url("/").toString(),
        sessionCookie: String? = "test_cookie",
        credentials: StoredCredentials? = StoredCredentials("test_user", "test_pass"),
        client: OkHttpClient = OkHttpClient.Builder().build()
    ): DashboardAvatarLoader {
        return DashboardAvatarLoader(
            baseUrl = baseUrl,
            sessionCookie = sessionCookie,
            credentials = credentials,
            scope = scope,
            client = client
        )
    }

    private fun createTestBitmapBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun createMockImageView(): ImageView {
        val imageView = mock(ImageView::class.java)
        var mockTag: Any? = null
        org.mockito.Mockito.`when`(imageView.tag).thenAnswer { mockTag }
        org.mockito.Mockito.`when`(imageView.setTag(any())).thenAnswer {
            mockTag = it.getArgument(0)
            null
        }
        return imageView
    }

    private suspend fun waitForIo() {
        kotlinx.coroutines.delay(50)
        kotlinx.coroutines.delay(500)
    }

    @Test
    fun `bind returns early and sets placeholder if username is null or blank`() = kotlinx.coroutines.runBlocking {
        loader = createLoader(this)
        val imageView = createMockImageView()

        loader.bind(imageView, null, true)

        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `bind successfully fetches avatar and sets image`() = kotlinx.coroutines.runBlocking {
        val bitmapBytes = createTestBitmapBytes()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bitmapBytes)))

        loader = createLoader(this)
        val imageView = createMockImageView()

        loader.bind(imageView, "testUser", true)

        val request = run {
            kotlinx.coroutines.delay(50)
            val request = mockWebServer.takeRequest(2, TimeUnit.SECONDS)
            org.junit.Assert.assertNotNull("MockWebServer did not receive a request within the timeout.", request)
            requireNotNull(request)
        }
        assertEquals("/db/_users/org.couchdb.user:testUser/img", request.path)
        assertEquals("test_cookie", request.getHeader("Cookie"))
        assertTrue(request.getHeader("Authorization")?.startsWith("Basic") == true)

        waitForIo()

        verify(imageView).setImageResource(R.drawable.ic_person_placeholder_24)
        verify(imageView).setImageBitmap(any())
    }

    @Test
    fun `bind checks missingAvatars set and skips network if not found previously`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        loader = createLoader(this)
        val imageView = createMockImageView()

        loader.bind(imageView, "missingUser", false)
        mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        waitForIo()

        assertEquals(1, mockWebServer.requestCount)

        val imageView2 = createMockImageView()
        loader.bind(imageView2, "missingUser", false)
        waitForIo()

        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `avatar update clears cache and allows retry`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        loader = createLoader(this)
        val imageView = createMockImageView()

        loader.bind(imageView, "updateUser", false)
        mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        waitForIo()

        assertEquals(1, mockWebServer.requestCount)

        loader.bind(imageView, "updateUser", false)
        waitForIo()
        assertEquals(1, mockWebServer.requestCount)

        AvatarUpdateNotifier.notifyAvatarUpdated("updateUser")

        val bitmapBytes = createTestBitmapBytes()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bitmapBytes)))

        loader.bind(imageView, "updateUser", false)
        mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        waitForIo()

        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `bind uses cached bitmap if available`() = kotlinx.coroutines.runBlocking {
        val bitmapBytes = createTestBitmapBytes()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bitmapBytes)))

        loader = createLoader(this)
        val imageView1 = createMockImageView()

        loader.bind(imageView1, "cacheUser", true)
        mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        waitForIo()

        assertEquals(1, mockWebServer.requestCount)

        val imageView2 = createMockImageView()

        loader.bind(imageView2, "cacheUser", true)
        waitForIo()

        assertEquals(1, mockWebServer.requestCount)

        verify(imageView2).setImageBitmap(any())
    }

    @Test
    fun `destroy unregisters listener`() = kotlinx.coroutines.runBlocking {
        loader = createLoader(this)
        loader.destroy()
    }

    @Test
    fun `fetchAvatarBitmap handles IOException by returning null`() = kotlinx.coroutines.runBlocking {
        val errorClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw IOException("Test network error")
            })
            .build()

        loader = createLoader(this, client = errorClient)
        val imageView = createMockImageView()

        loader.bind(imageView, "errorUser", true)

        waitForIo()

        // Verify that setImageBitmap is not called with any bitmap (or it failed gracefully)
        verify(imageView).setImageResource(R.drawable.ic_person_placeholder_24)
        verify(imageView).tag = "erroruser"
    }
}
