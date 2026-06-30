package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.dashboard.AddTeamMemberRequest
import org.ole.planet.myplanet.lite.util.DateStringAdapter

class DashboardTeamsOperationsTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var operations: DashboardTeamsOperations
    private lateinit var cache: ConcurrentHashMap<String, List<TeamMemberDetails>>

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        cache = ConcurrentHashMap()
        val moshi = Moshi.Builder()
            .add(DateStringAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
        operations = DashboardTeamsOperations(OkHttpClient(), moshi, cache)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun addTeamMember_success() {
        val putResponse = "{\"ok\": true}"
        mockWebServer.enqueue(MockResponse().setBody(putResponse).setResponseCode(201))

        val baseUrl = mockWebServer.url("/").toString()
        cache["team1"] = listOf()
        operations.addTeamMember(
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

        val request = mockWebServer.takeRequest()
        assertTrue(request.path?.startsWith("/db/teams/_bulk_docs") == true)
        assertEquals("POST", request.method)
        assertFalse(cache.containsKey("team1"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("team1"))
        assertTrue(body.contains("planet1"))
        assertTrue(body.contains("user1"))
        assertTrue(body.contains("planet2"))
    }


    @Test
    fun addTeamMember_missingBaseUrl() {
        val exception = assertThrows(IOException::class.java) {
            operations.addTeamMember(
                baseUrl = "",
                credentials = StoredCredentials("testuser", "pass"),
                sessionCookie = "cookie",
                request = AddTeamMemberRequest(
                    teamId = "team1",
                    teamPlanetCode = "planet1",
                    userId = "user1",
                    userPlanetCode = "planet2"
                )
            )
        }
        assertEquals("Missing server base URL", exception.message)
    }

    @Test
    fun addTeamMember_missingTeamId() {
        val exception = assertThrows(IOException::class.java) {
            operations.addTeamMember(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = StoredCredentials("testuser", "pass"),
                sessionCookie = "cookie",
                request = AddTeamMemberRequest(
                    teamId = "",
                    teamPlanetCode = "planet1",
                    userId = "user1",
                    userPlanetCode = "planet2"
                )
            )
        }
        assertEquals("Missing team id", exception.message)
    }

    @Test
    fun addTeamMember_missingTeamPlanetCode() {
        val exception = assertThrows(IOException::class.java) {
            operations.addTeamMember(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = StoredCredentials("testuser", "pass"),
                sessionCookie = "cookie",
                request = AddTeamMemberRequest(
                    teamId = "team1",
                    teamPlanetCode = "",
                    userId = "user1",
                    userPlanetCode = "planet2"
                )
            )
        }
        assertEquals("Missing team planet code", exception.message)
    }

    @Test
    fun addTeamMember_missingUserId() {
        val exception = assertThrows(IOException::class.java) {
            operations.addTeamMember(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = StoredCredentials("testuser", "pass"),
                sessionCookie = "cookie",
                request = AddTeamMemberRequest(
                    teamId = "team1",
                    teamPlanetCode = "planet1",
                    userId = "",
                    userPlanetCode = "planet2"
                )
            )
        }
        assertEquals("Missing user id", exception.message)
    }

    @Test
    fun addTeamMember_missingUserPlanetCode() {
        val exception = assertThrows(IOException::class.java) {
            operations.addTeamMember(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = StoredCredentials("testuser", "pass"),
                sessionCookie = "cookie",
                request = AddTeamMemberRequest(
                    teamId = "team1",
                    teamPlanetCode = "planet1",
                    userId = "user1",
                    userPlanetCode = ""
                )
            )
        }
        assertEquals("Missing user planet code", exception.message)
    }

    @Test
    fun addTeamMember_unsuccessfulResponse() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val exception = assertThrows(IOException::class.java) {
            operations.addTeamMember(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = StoredCredentials("testuser", "pass"),
                sessionCookie = "cookie",
                request = AddTeamMemberRequest(
                    teamId = "team1",
                    teamPlanetCode = "planet1",
                    userId = "user1",
                    userPlanetCode = "planet2"
                )
            )
        }
        assertTrue(exception.message?.startsWith("Unexpected response") == true)
    }
}
