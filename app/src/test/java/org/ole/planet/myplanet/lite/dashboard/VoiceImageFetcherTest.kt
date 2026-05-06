package org.ole.planet.myplanet.lite.dashboard

import java.io.File
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceImageFetcherTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var tempCacheDir: File

    @Before
    fun `set up`() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        okHttpClient = OkHttpClient()
        tempCacheDir = File.createTempFile("test_cache", "").apply {
            delete()
            mkdir()
        }
    }

    @After
    fun `tear down`() {
        mockWebServer.shutdown()
        tempCacheDir.deleteRecursively()
    }

    @Test
    fun `fetchExistingImage success`() {
        val dummyBytes = "dummy_image_data".toByteArray()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(String(dummyBytes)))

        val baseUrl = mockWebServer.url("/").toString()
        val imagePath = "db/resources/12345/image.jpg"

        val result = VoiceImageFetcher.fetchExistingImage(
            httpClient = okHttpClient,
            cacheDir = tempCacheDir,
            sessionCookie = "dummy_cookie",
            baseUrl = baseUrl,
            imagePath = imagePath,
            generateImageFileName = { "generated.jpg" },
            generatePendingImageId = { "pending-$it" }
        )

        assertNotNull(result)
        assertEquals("pending-image.jpg", result?.id)
        assertEquals("image.jpg", result?.fileName)
        assertArrayEquals(dummyBytes, result?.jpegBytes)
        assertEquals("12345", result?.resourceId)
        assertTrue(result?.file?.exists() == true)
        assertEquals("!(resources/12345/image.jpg)", result?.uploadedMarkdown?.replace("[]", ""))

        val request = mockWebServer.takeRequest()
        assertEquals("dummy_cookie", request.getHeader("Cookie"))
        assertEquals("/db/resources/12345/image.jpg", request.path)
    }

    @Test
    fun `fetchExistingImage with HTTP error returns null`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val baseUrl = mockWebServer.url("/").toString()

        val result = VoiceImageFetcher.fetchExistingImage(
            httpClient = okHttpClient,
            cacheDir = tempCacheDir,
            sessionCookie = null,
            baseUrl = baseUrl,
            imagePath = "invalid_path.jpg",
            generateImageFileName = { "generated.jpg" },
            generatePendingImageId = { "pending-$it" }
        )

        assertNull(result)
    }

    @Test
    fun `fetchExistingImage with invalid base url returns null`() {
        val result = VoiceImageFetcher.fetchExistingImage(
            httpClient = okHttpClient,
            cacheDir = tempCacheDir,
            sessionCookie = null,
            baseUrl = "   ",
            imagePath = "image.jpg",
            generateImageFileName = { "generated.jpg" },
            generatePendingImageId = { "pending-$it" }
        )

        assertNull(result)
    }

    @Test
    fun `fetchExistingImage with full http path`() {
        val dummyBytes = "dummy_image_data".toByteArray()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(String(dummyBytes)))
        val fullUrl = mockWebServer.url("/some/external/image.jpg").toString()

        val result = VoiceImageFetcher.fetchExistingImage(
            httpClient = okHttpClient,
            cacheDir = tempCacheDir,
            sessionCookie = null,
            baseUrl = "http://dummy",
            imagePath = fullUrl,
            generateImageFileName = { "generated.jpg" },
            generatePendingImageId = { "pending-$it" }
        )

        assertNotNull(result)
        assertEquals("image.jpg", result?.fileName)
        assertArrayEquals(dummyBytes, result?.jpegBytes)
        assertEquals("!(${fullUrl})", result?.uploadedMarkdown?.replace("[]", ""))
    }

    @Test
    fun `fetchExistingImage generates file name if path has no file name`() {
        val dummyBytes = "dummy_image_data".toByteArray()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(String(dummyBytes)))
        val baseUrl = mockWebServer.url("/").toString()

        val result = VoiceImageFetcher.fetchExistingImage(
            httpClient = okHttpClient,
            cacheDir = tempCacheDir,
            sessionCookie = null,
            baseUrl = baseUrl,
            imagePath = "db/resources/12345/",
            generateImageFileName = { "generated.jpg" },
            generatePendingImageId = { "pending-$it" }
        )

        assertNotNull(result)
        assertEquals("pending-12345", result?.id)
        assertEquals("12345", result?.fileName)
        assertEquals("!(resources/12345/)", result?.uploadedMarkdown?.replace("[]", ""))
    }

    @Test
    fun `fetchExistingImage with directory traversal path returns null`() {
        val dummyBytes = "dummy_image_data".toByteArray()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(String(dummyBytes)))
        val baseUrl = mockWebServer.url("/").toString()

        val result = VoiceImageFetcher.fetchExistingImage(
            httpClient = okHttpClient,
            cacheDir = tempCacheDir,
            sessionCookie = null,
            baseUrl = baseUrl,
            imagePath = "   ", // Blank path makes extractFileName return null, falling back to generateImageFileName
            generateImageFileName = { "../traversal.jpg" },
            generatePendingImageId = { "pending-$it" }
        )

        assertNull("Result should be null due to path traversal blocking from generated name", result)
    }

    @Test
    fun `fetchExistingImage with directory traversal path returns null blocked`() {
        val dummyBytes = "dummy_image_data".toByteArray()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(String(dummyBytes)))
        val baseUrl = mockWebServer.url("/").toString()

        val result = VoiceImageFetcher.fetchExistingImage(
            httpClient = okHttpClient,
            cacheDir = tempCacheDir,
            sessionCookie = null,
            baseUrl = baseUrl,
            imagePath = "db/resources/123/..",
            generateImageFileName = { "generated.jpg" },
            generatePendingImageId = { "pending-$it" }
        )

        assertNull("Result should be null due to path traversal blocking", result)
    }

    @Test
    fun `fetchExistingImage with blank image path returns null`() {
        val baseUrl = mockWebServer.url("/").toString()

        val result = VoiceImageFetcher.fetchExistingImage(
            httpClient = okHttpClient,
            cacheDir = tempCacheDir,
            sessionCookie = null,
            baseUrl = baseUrl,
            imagePath = "   ",
            generateImageFileName = { "generated.jpg" },
            generatePendingImageId = { "pending-$it" }
        )

        assertNull(result)
    }

    @Test
    fun `fetchExistingImage with network exception returns null`() {
        val baseUrl = mockWebServer.url("/").toString()
        mockWebServer.shutdown() // shut down mock server so okHttpClient throws an exception

        val result = VoiceImageFetcher.fetchExistingImage(
            httpClient = okHttpClient,
            cacheDir = tempCacheDir,
            sessionCookie = null,
            baseUrl = baseUrl,
            imagePath = "image.jpg",
            generateImageFileName = { "generated.jpg" },
            generatePendingImageId = { "pending-$it" }
        )

        assertNull(result)
    }

    @Test
    fun `fetchExistingImage with blank cookie omits cookie header`() {
        val dummyBytes = "dummy_image_data".toByteArray()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(String(dummyBytes)))

        val baseUrl = mockWebServer.url("/").toString()

        val result = VoiceImageFetcher.fetchExistingImage(
            httpClient = okHttpClient,
            cacheDir = tempCacheDir,
            sessionCookie = "   ",
            baseUrl = baseUrl,
            imagePath = "db/resources/12345/image.jpg",
            generateImageFileName = { "generated.jpg" },
            generatePendingImageId = { "pending-$it" }
        )

        assertNotNull(result)
        val request = mockWebServer.takeRequest()
        assertNull(request.getHeader("Cookie"))
    }
}
