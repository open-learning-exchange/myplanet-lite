package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class DashboardTeamsRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardTeamsRepository
    private val credentials = StoredCredentials("user", "pass")

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = DashboardTeamsRepository()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchMemberCount returns correct count`() = runTest {
        val teamId = "team123"
        val responseBody = """
            {
                "docs": [
                    { "_id": "member1" },
                    { "_id": "member2" },
                    { "_id": "member3" }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = repository.fetchMemberCount(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = "cookie",
            teamId = teamId
        )

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull())

        val request = mockWebServer.takeRequest()
        assertEquals("/db/teams/_find", request.path)
        val bodyStr = request.body.readUtf8()
        assertTrue(bodyStr.contains("\"teamId\":\"$teamId\""))
    }

    @Test
    fun `fetchMemberCounts returns correct counts map`() = runTest {
        val teamIds = listOf("team1", "team2")
        val responseBody = """
            {
                "docs": [
                    { "_id": "m1", "teamId": "team1" },
                    { "_id": "m2", "teamId": "team1" },
                    { "_id": "m3", "teamId": "team2" }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = repository.fetchMemberCounts(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = "cookie",
            teamIds = teamIds
        )

        assertTrue(result.isSuccess)
        val counts = result.getOrNull()
        assertNotNull(counts)
        assertEquals(2, counts!!["team1"])
        assertEquals(1, counts["team2"])

        val request = mockWebServer.takeRequest()
        assertEquals("/db/teams/_find", request.path)
        val bodyStr = request.body.readUtf8()
        assertTrue(bodyStr.contains("\"teamId\":{\"\$in\":[\"team1\",\"team2\"]}"))
        assertTrue(bodyStr.contains("\"limit\":50000"))
    }

    @Test
    fun `fetchMemberCounts returns empty map when teamIds is empty`() = runTest {
        val result = repository.fetchMemberCounts(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = "cookie",
            teamIds = emptyList()
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `fetchMemberCount returns failure on non-200 response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = repository.fetchMemberCount(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = "cookie",
            teamId = "team123"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }
}
