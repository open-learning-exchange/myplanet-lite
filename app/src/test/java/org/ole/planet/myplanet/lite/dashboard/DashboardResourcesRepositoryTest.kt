package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DashboardResourcesRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DashboardResourcesRepository

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        repository = DashboardResourcesRepository()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchCommunityResources_sendsCorrectSort() = runBlocking {
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
    fun downloadResourceBytes_reportsProgress() = runBlocking {
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
}
