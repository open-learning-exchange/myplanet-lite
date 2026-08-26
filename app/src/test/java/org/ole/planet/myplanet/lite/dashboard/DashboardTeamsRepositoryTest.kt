package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.dashboard.AddTeamMemberRequest

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
            client = OkHttpClient.Builder().build(),
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
    fun searchTeams_usesActiveTeamNameSelector() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("{\"docs\": [{\"_id\": \"team-android\", \"name\": \"Equipo Android\"}]}")
                .setResponseCode(200)
        )

        val result = repository.searchTeams(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = StoredCredentials("testuser", "pass"),
            sessionCookie = "cookie",
            name = " Equipo Android ",
        )

        assertTrue(result.isSuccess)
        assertEquals("team-android", result.getOrNull()?.single()?.id)
        val request = mockWebServer.takeRequest()
        assertEquals("/db/teams/_find", request.path)
        assertEquals("POST", request.method)
        val selector = JSONObject(request.body.readUtf8()).getJSONObject("selector")
        assertEquals("(?i).*Equipo Android.*", selector.getJSONObject("name").getString("\$regex"))
        assertEquals("", selector.getJSONObject("_id").getString("\$ne"))
        assertEquals("active", selector.getString("status"))
        assertEquals("team", selector.getString("type"))
    }

    @Test
    fun createTeam_createsDocumentAndLeaderMembership() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("{\"docs\":[]}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("{\"ok\":true,\"id\":\"team-new\",\"rev\":\"1-team\"}"))
        mockWebServer.enqueue(MockResponse().setBody("{\"docs\":[]}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("[{\"ok\":true,\"id\":\"membership-new\",\"rev\":\"1-member\"}]"))

        val result = repository.createTeam(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = StoredCredentials("juan", "pass"),
            sessionCookie = "AuthSession=test",
            request = createTeamRequest(),
        )

        assertTrue(result.isSuccess)
        assertEquals("team-new", result.getOrNull()?.id)

        val duplicateRequest = mockWebServer.takeRequest()
        assertEquals("/db/teams/_find", duplicateRequest.path)
        val duplicatePayload = JSONObject(duplicateRequest.body.readUtf8())
        assertEquals(1, duplicatePayload.getInt("limit"))
        assertEquals("(?i)^\\s*Equipo Android\\s*$", duplicatePayload.getJSONObject("selector").getJSONObject("name").getString("\$regex"))

        val teamRequest = mockWebServer.takeRequest()
        assertEquals("/db/teams", teamRequest.path)
        val teamPayload = JSONObject(teamRequest.body.readUtf8())
        assertEquals("Equipo Android", teamPayload.getString("name"))
        assertEquals("Plan del equipo", teamPayload.getString("description"))
        assertEquals(false, teamPayload.getBoolean("public"))
        assertEquals(12, teamPayload.getInt("limit"))
        assertEquals("local", teamPayload.getString("teamType"))
        assertEquals("planet-one", teamPayload.getString("teamPlanetCode"))
        assertEquals("parent-one", teamPayload.getString("parentCode"))
        assertEquals("org.couchdb.user:juan", teamPayload.getString("createdBy"))

        val membershipFindRequest = mockWebServer.takeRequest()
        assertEquals("/db/teams/_find", membershipFindRequest.path)
        assertTrue(JSONObject(membershipFindRequest.body.readUtf8()).getJSONObject("selector").getBoolean("isLeader"))

        val membershipCreateRequest = mockWebServer.takeRequest()
        assertEquals("/db/teams/_bulk_docs", membershipCreateRequest.path)
        val membership = JSONObject(membershipCreateRequest.body.readUtf8()).getJSONArray("docs").getJSONObject(0)
        assertEquals("team-new", membership.getString("teamId"))
        assertEquals("membership", membership.getString("docType"))
        assertEquals("local", membership.getString("teamType"))
        assertTrue(membership.getBoolean("isLeader"))
        assertEquals("AuthSession=test", membershipCreateRequest.getHeader("Cookie"))
    }

    @Test
    fun createTeam_rejectsDuplicateNameBeforeCreatingDocument() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("{\"docs\":[{\"_id\":\"existing\"}]}"))

        val result = repository.createTeam(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = StoredCredentials("juan", "pass"),
            sessionCookie = "cookie",
            request = createTeamRequest(),
        )

        assertTrue(result.exceptionOrNull() is DuplicateTeamNameException)
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun retryTeamLeaderMembership_isIdempotentWhenMembershipExists() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("{\"docs\":[{\"_id\":\"membership-existing\"}]}"))

        val result = repository.retryTeamLeaderMembership(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = StoredCredentials("juan", "pass"),
            sessionCookie = "cookie",
            teamId = "team-existing",
            request = createTeamRequest(),
        )

        assertTrue(result.isSuccess)
        assertEquals("team-existing", result.getOrNull()?.id)
        assertEquals(1, mockWebServer.requestCount)
    }

    private fun createTeamRequest() = CreateTeamRequest(
        name = "Equipo Android",
        description = "Plan del equipo",
        isPublic = false,
        planetCode = "planet-one",
        parentCode = "parent-one",
        userId = "org.couchdb.user:juan",
    )

    @Test
    fun addTeamMember_success() = runTest {
        val putResponse = "{\"ok\": true, \"id\": \"doc1\", \"rev\": \"1-abc\"}"
        mockWebServer.enqueue(MockResponse().setBody(putResponse).setResponseCode(201))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.addTeamMember(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass"),
            sessionCookie = "cookie",
            request = AddTeamMemberRequest(
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
            request = JoinTeamRequest(
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




    @Test
    fun fetchTeamMembers_success() = runTest {
        val responseBody = """
            {
                "docs": [
                    {
                        "_id": "membership1",
                        "teamId": "team1",
                        "userId": "user1"
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = repository.fetchTeamMembers(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = StoredCredentials("u", "p"),
            sessionCookie = "cookie",
            teamId = "team1"
        )

        assertTrue(result.isSuccess)
        val list = result.getOrNull()
        assertEquals(1, list?.size)
        assertEquals("membership1", list?.get(0)?.id)
    }

    @Test
    fun fetchTeamMembers_failure() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = repository.fetchTeamMembers(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = StoredCredentials("u", "p"),
            sessionCookie = "cookie",
            teamId = "team1"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
    }
}
