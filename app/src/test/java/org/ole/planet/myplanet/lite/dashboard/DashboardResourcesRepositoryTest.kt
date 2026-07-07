package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import java.io.File
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class DashboardResourcesRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DashboardResourcesRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        repository = DashboardResourcesRepository(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchCommunityResources_sendsCorrectSort() = runTest {
        server.enqueue(MockResponse().setBody("{\"docs\": []}"))

        repository.fetchCommunityResources(
            baseUrl = server.url("/").toString(),
            sessionCookie = "test-cookie",
            searchQuery = "test",
            mediaTypeFilter = "audio",
            sortBy = "createdDate",
            sortDescending = true
        )

        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        val sort = body.getJSONArray("sort").getJSONObject(0)

        assertEquals("desc", sort.getString("createdDate"))
        val selector = body.getJSONObject("selector")
        assertEquals(false, selector.has("createdDate"))
        assertEquals(true, selector.has("_id"))
        assertEquals(true, selector.getJSONObject("_id").has($$"$gt"))
    }

    @Test
    fun downloadResourceBytes_reportsProgress() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("abcd")
                .setHeader("Content-Length", "4")
        )

        val progressValues = mutableListOf<Int?>()
        val result = repository.downloadResourceBytes(
            baseUrl = server.url("/").toString(),
            sessionCookie = "test-cookie",
            resourceId = "resource-1",
            filename = "file.pdf",
            onProgress = progressValues::add
        )

        assertEquals(true, result.isSuccess)
        assertEquals("abcd", result.getOrThrow().decodeToString())
        assertEquals(0, progressValues.first())
        assertEquals(100, progressValues.last())
    }

    @Test
    fun downloadPdfToCache_success_writesFile() = runBlocking<Unit> {
        val pdfContent = "dummy pdf content"
        server.enqueue(MockResponse().setResponseCode(200).setBody(pdfContent))

        val cacheDir = File(System.getProperty("java.io.tmpdir")!!, "test-cache")
        cacheDir.mkdirs()

        val result = repository.downloadPdfToCache(server.url("/test.pdf").toString(), "Basic auth", cacheDir)

        org.junit.Assert.assertNotNull(result)
        org.junit.Assert.assertTrue(result!!.exists())
        org.junit.Assert.assertEquals(pdfContent, result.readText())

        val request = server.takeRequest()
        org.junit.Assert.assertNull(request.getHeader("Authorization"))

        result.delete()
        cacheDir.delete()
    }

    @Test
    fun downloadPdfToCache_non2xx_returnsNull() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(404))

        val cacheDir = File(System.getProperty("java.io.tmpdir")!!, "test-cache")
        cacheDir.mkdirs()

        val result = repository.downloadPdfToCache(server.url("/test.pdf").toString(), "Basic auth", cacheDir)

        org.junit.Assert.assertNull(result)

        cacheDir.delete()
    }
}