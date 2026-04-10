package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardTeamsRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardTeamsRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val dispatcher = UnconfinedTestDispatcher()
        repository = DashboardTeamsRepository(
            dispatcher = dispatcher
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun fetchTeams_success() = runTest {
        val jsonResponse = "{\"docs\": [{\"_id\": \"team1\", \"name\": \"Team One\"}, {\"_id\": \"team2\", \"name\": \"Team Two\"}]}"
        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchTeams(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass"),
            sessionCookie = "cookie",
            teamIds = listOf("team1", "team2")
        )

        if (!result.isSuccess) throw result.exceptionOrNull()!!
        val teams = result.getOrNull()
        assertEquals(2, teams?.size)
        assertEquals("team1", teams?.get(0)?.id)
        assertEquals("Team One", teams?.get(0)?.name)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/teams/_find", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Authorization")?.startsWith("Basic") == true)
        assertEquals("cookie", request.getHeader("Cookie"))
    }

    @Test
    fun fetchTeams_missingBaseUrl() = runTest {
        val result = repository.fetchTeams(
            baseUrl = "  ",
            credentials = StoredCredentials("testuser", "pass"),
            sessionCookie = "cookie",
            teamIds = listOf("team1", "team2")
        )
        assertTrue(result.isFailure)
        assertEquals("Missing server base URL", result.exceptionOrNull()?.message)
    }

    @Test
    fun fetchTeams_httpError() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchTeams(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass"),
            sessionCookie = "cookie",
            teamIds = listOf("team1")
        )

        assertTrue(result.isFailure)
        assertEquals("Unexpected response 500", result.exceptionOrNull()?.message)
    }

    @Test
    fun fetchAvailableTeams_success() = runTest {
        val jsonResponse = "{\"docs\": [{\"_id\": \"team3\", \"name\": \"Available Team\"}]}"
        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchAvailableTeams(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass"),
            sessionCookie = "cookie",
            excludedTeamIds = listOf("team1"),
            limit = 10,
            skip = 0
        )

        if (!result.isSuccess) throw result.exceptionOrNull()!!
        val teams = result.getOrNull()
        assertEquals(1, teams?.size)
        assertEquals("team3", teams?.get(0)?.id)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/teams/_find", request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun addTeamMember_success() = runTest {
        val putResponse = "{\"ok\": true, \"id\": \"doc1\", \"rev\": \"1-abc\"}"
        mockWebServer.enqueue(MockResponse().setBody(putResponse).setResponseCode(201))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.addTeamMember(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass"),
            sessionCookie = "cookie",
            teamId = "team1",
            teamPlanetCode = "planet1",
            userId = "user1",
            userPlanetCode = "planet2"
        )

        if (!result.isSuccess) throw result.exceptionOrNull()!!

        val request = mockWebServer.takeRequest()
        assertTrue(request.path?.startsWith("/db/teams") == true)
        assertEquals("POST", request.method)
    }

    @Test
    fun fetchJoinRequests_success() = runTest {
        val jsonResponse = "{\"docs\": [{\"_id\": \"req1\", \"teamId\": \"team1\", \"userId\": \"user1\"}]}"
        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchJoinRequests(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass"),
            sessionCookie = null,
            userId = "user1"
        )

        if (!result.isSuccess) throw result.exceptionOrNull()!!
        val reqs = result.getOrNull()
        assertEquals(1, reqs?.size)
        assertEquals("req1", reqs?.get(0)?.id)
        assertEquals("team1", reqs?.get(0)?.teamId)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/teams/_find", request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun requestTeamMembership_success() = runTest {
        val putResponse = "{\"ok\": true, \"id\": \"req1\", \"rev\": \"1-abc\"}"
        mockWebServer.enqueue(MockResponse().setBody(putResponse).setResponseCode(201))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.requestTeamMembership(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass"),
            sessionCookie = null,
            request = DashboardTeamsRepository.JoinTeamRequest(
                teamId = "team1",
                teamPlanetCode = "planet1",
                userId = "user1",
                userPlanetCode = "planet2"
            )
        )

        if (!result.isSuccess) throw result.exceptionOrNull()!!

        val request = mockWebServer.takeRequest()
        assertTrue(request.path?.startsWith("/db/teams") == true)
        assertEquals("POST", request.method)
    }
}
