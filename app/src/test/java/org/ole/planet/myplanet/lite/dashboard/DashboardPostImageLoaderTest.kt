package org.ole.planet.myplanet.lite.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardPostImageLoaderTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var testScope: TestScope
    private lateinit var imageLoader: DashboardPostImageLoader
    private lateinit var imageView: ImageView

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockWebServer = MockWebServer()
        mockWebServer.start()
        testScope = TestScope(UnconfinedTestDispatcher())
        imageLoader = DashboardPostImageLoader(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "session=123",
            scope = testScope
        )
        imageView = ImageView(ApplicationProvider.getApplicationContext())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        mockWebServer.shutdown()
    }

    @Test
    fun `bind with successful network request sets bitmap`() = runTest {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val bytes = outputStream.toByteArray()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bytes)))

        val resultDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        imageLoader.bind(imageView, "image.png") {
            resultDeferred.complete(it)
        }

        val result = resultDeferred.await()

        assertEquals(true, result)
        assertNotNull(imageView.drawable)
        assertEquals("image.png", imageView.tag)
        assertEquals(View.VISIBLE, imageView.visibility)
        // verify(imageView).setImageBitmap(any()) is harder because it's a new Bitmap object.

        val request = mockWebServer.takeRequest()
        assertEquals("/db/image.png", request.path)
        assertEquals("session=123", request.getHeader("Cookie"))
    }

    @Test
    fun `bind with failed network request hides imageView`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val resultDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        imageLoader.bind(imageView, "image.png") {
            resultDeferred.complete(it)
        }

        val result = resultDeferred.await()

        assertEquals(false, result)
        assertNull(imageView.drawable)
        assertEquals("image.png", imageView.tag)
        assertEquals(View.GONE, imageView.visibility)
    }

    @Test
    fun `bind with cached image uses cache and skips network`() = runTest {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val bytes = outputStream.toByteArray()

        // First call caches the image
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bytes)))

        val resultDeferred1 = kotlinx.coroutines.CompletableDeferred<Boolean>()
        imageLoader.bind(imageView, "cached.png") {
            resultDeferred1.complete(it)
        }
        val result1 = resultDeferred1.await()
        assertEquals(true, result1)

        // Second call should hit the cache
        val resultDeferred2 = kotlinx.coroutines.CompletableDeferred<Boolean>()
        imageLoader.bind(imageView, "cached.png") {
            resultDeferred2.complete(it)
        }
        val result2 = resultDeferred2.await()
        assertEquals(true, result2)

        // Only one request should have been made
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `resolveUrl handles various paths via reflection`() {
        val resolveUrlMethod = DashboardPostImageLoader::class.java.getDeclaredMethod("resolveUrl", String::class.java)
        resolveUrlMethod.isAccessible = true

        val loader = DashboardPostImageLoader("http://test.com/", "cookie", testScope)

        assertEquals("http://test.com/db/image.jpg", resolveUrlMethod.invoke(loader, "image.jpg"))
        assertEquals("http://test.com/db/image.jpg", resolveUrlMethod.invoke(loader, "db/image.jpg"))
        assertEquals("http://test.com/db/image.jpg", resolveUrlMethod.invoke(loader, "/db/image.jpg"))
        assertEquals("https://external.com/img.png", resolveUrlMethod.invoke(loader, "https://external.com/img.png"))
        assertEquals("http://external.com/img.png", resolveUrlMethod.invoke(loader, "http://external.com/img.png"))
        assertNull(resolveUrlMethod.invoke(loader, "   "))

        val loaderEmptyUrl = DashboardPostImageLoader("  ", "cookie", testScope)
        assertNull(resolveUrlMethod.invoke(loaderEmptyUrl, "image.jpg"))
    }
}
