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
}
