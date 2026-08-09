package org.ole.planet.myplanet.lite.dashboard

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardResourcesRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DashboardResourcesRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        repository = DashboardResourcesRepository(ioDispatcher = UnconfinedTestDispatcher())
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
    fun buildResourcePayload_createsCorrectJson() {
        val request = DashboardResourcesRepository.ResourceMetadataRequest(
            title = "Test Title",
            description = "Test Description",
            language = "English",
            username = "tester",
            planetCode = "planetX",
            isDownloadable = true
        )

        val payload = repository.buildResourcePayload(
            request = request,
            subject = "Science",
            level = "Beginner"
        )

        assertEquals("Test Title", payload.getString("title"))
        assertEquals("Test Description", payload.getString("description"))
        assertEquals("Science", payload.getJSONArray("subject").getString(0))
        assertEquals("Beginner", payload.getJSONArray("level").getString(0))
        assertEquals("English", payload.getString("language"))
        assertEquals("tester", payload.getString("addedBy"))
        assertEquals("planetX", payload.getString("sourcePlanet"))
        assertEquals("planetX", payload.getString("resideOn"))
        assertEquals(true, payload.getBoolean("isDownloadable"))
        assertEquals(false, payload.getBoolean("private"))
    }

    @Test
    fun createAndUploadResourceSequence_success() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\"id\": \"res1\", \"rev\": \"1-abc\"}")) // Create
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\": \"res1\", \"rev\": \"2-def\"}")) // Update
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\"ok\": true}")) // Upload
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\"id\": \"team1\", \"rev\": \"1-ghi\"}")) // Team link

        val payload = JSONObject().put("title", "Test Resource")
        val request = DashboardResourcesRepository.CreateAndUploadResourceRequest(
            baseUrl = server.url("/").toString(),
            sessionCookie = "test-cookie",
            credentials = null,
            payload = payload,
            fileExtension = "pdf",
            mediaType = "pdf",
            mimeType = "application/pdf",
            bytes = byteArrayOf(1, 2, 3),
            teamId = "team-abc",
            planetCode = "planet-xyz"
        )

        val result = repository.createAndUploadResourceSequence(request)
        assertEquals(true, result.isSuccess)

        // Verify Create Request
        val createReq = server.takeRequest()
        assertEquals("/db/resources", createReq.path)
        assertEquals("POST", createReq.method)

        // Verify Update Request
        val updateReq = server.takeRequest()
        assertEquals("/db/resources/res1", updateReq.path)
        assertEquals("PUT", updateReq.method)
        val updateBody = JSONObject(updateReq.body.readUtf8())
        assertEquals("res1.pdf", updateBody.getString("filename"))
        assertEquals("pdf", updateBody.getString("mediaType"))

        // Verify Upload Request
        val uploadReq = server.takeRequest()
        assertEquals("/db/resources/res1/res1.pdf?rev=2-def", uploadReq.path)
        assertEquals("PUT", uploadReq.method)
        assertEquals("application/pdf", uploadReq.getHeader("Content-Type"))

        // Verify Team Link Request
        val teamReq = server.takeRequest()
        assertEquals("/db/teams", teamReq.path)
        assertEquals("POST", teamReq.method)
        val teamBody = JSONObject(teamReq.body.readUtf8())
        assertEquals("resourceLink", teamBody.getString("docType"))
        assertEquals("res1", teamBody.getString("resourceId"))
        assertEquals("team-abc", teamBody.getString("teamId"))
    }

    @Test
    fun createAndUploadResourceSequence_ignoresTeamLinkFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\"id\": \"res1\", \"rev\": \"1-abc\"}")) // Create
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\": \"res1\", \"rev\": \"2-def\"}")) // Update
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\"ok\": true}")) // Upload
        server.enqueue(MockResponse().setResponseCode(500).setBody("{\"error\": \"server_error\"}")) // Team link fails

        val payload = JSONObject().put("title", "Test Resource")
        val request = DashboardResourcesRepository.CreateAndUploadResourceRequest(
            baseUrl = server.url("/").toString(),
            sessionCookie = "test-cookie",
            credentials = null,
            payload = payload,
            fileExtension = "pdf",
            mediaType = "pdf",
            mimeType = "application/pdf",
            bytes = byteArrayOf(1, 2, 3),
            teamId = "team-abc",
            planetCode = "planet-xyz"
        )

        val result = repository.createAndUploadResourceSequence(request)
        assertEquals(true, result.isSuccess) // Should still succeed because team link is ignored

        server.takeRequest() // Create
        server.takeRequest() // Update
        server.takeRequest() // Upload
        server.takeRequest() // Team link
    }

    @Test
    fun downloadPdfToCache_success_writesFile() = runTest {
        val pdfContent = "dummy pdf content"
        server.enqueue(MockResponse().setResponseCode(200).setBody(pdfContent))

        val cacheDir = File(System.getProperty("java.io.tmpdir")!!, "test-cache")
        cacheDir.mkdirs()

        val result = repository.downloadPdfToCache(server.url("/test.pdf").toString(), "Basic auth", cacheDir)

        assertNotNull(result)
        assertTrue(result!!.exists())
        assertEquals(pdfContent, result.readText())

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))

        result.delete()
        cacheDir.delete()
    }

    @Test
    fun downloadPdfToCache_non2xx_returnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val cacheDir = File(System.getProperty("java.io.tmpdir")!!, "test-cache")
        cacheDir.mkdirs()

        val result = repository.downloadPdfToCache(server.url("/test.pdf").toString(), "Basic auth", cacheDir)

        assertNull(result)

        cacheDir.delete()
    }
}
