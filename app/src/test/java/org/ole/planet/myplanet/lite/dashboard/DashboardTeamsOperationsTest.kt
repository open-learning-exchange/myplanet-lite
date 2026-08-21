package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun fetchTeamJoinRequestDetails_success() {
        val requestsResponse = """
            {
                "docs": [
                    {
                        "_id": "req1",
                        "userId": "org.couchdb.user:testuser",
                        "teamId": "team1"
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(requestsResponse).setResponseCode(200))

        val profilesResponse = """
            {
                "docs": [
                    {
                        "_id": "org.couchdb.user:testuser",
                        "firstName": "Test",
                        "lastName": "User",
                        "_attachments": {
                            "img": {
                                "content_type": "image/png"
                            }
                        }
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(profilesResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val details = operations.fetchTeamJoinRequestDetails(
            baseUrl,
            StoredCredentials("u", "p"),
            "cookie",
            "team1",
            "code1",
        )

        assertEquals(1, details.size)
        val detail = details[0]
        assertEquals("testuser", detail.username)
        assertEquals("Test User", detail.fullName)
        assertTrue(detail.hasAvatar)
        assertEquals("req1", detail.request.id)
    }

    @Test
    fun fetchTeamJoinRequestDetails_profilesFailureGraceful() {
        val requestsResponse = """
            {
                "docs": [
                    {
                        "_id": "req2",
                        "userId": "org.couchdb.user:testuser2",
                        "teamId": "team2"
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(requestsResponse).setResponseCode(200))

        // Profiles fetch fails
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val baseUrl = mockWebServer.url("/").toString()
        val details = operations.fetchTeamJoinRequestDetails(
            baseUrl,
            StoredCredentials("u", "p"),
            "cookie",
            "team2",
            "code2",
        )

        assertEquals(1, details.size)
        val detail = details[0]
        assertEquals("testuser2", detail.username)
        assertEquals("testuser2", detail.fullName) // Falls back to username
        assertFalse(detail.hasAvatar)
        assertEquals("req2", detail.request.id)
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

    @Test
    fun fetchUserProfile_networkFailure() {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val method = DashboardTeamsOperations::class.java.getDeclaredMethod(
            "fetchUserProfile",
            String::class.java,
            String::class.java,
            StoredCredentials::class.java,
            String::class.java
        )
        method.isAccessible = true

        val result = method.invoke(
            operations,
            mockWebServer.url("/").toString(),
            "testuser",
            StoredCredentials("testuser", "pass"),
            "cookie"
        )
        assertNull(result)
    }

    @Test
    fun fetchTeamMembers_success() {
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

        val result = operations.fetchTeamMembers(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = StoredCredentials("u", "p"),
            sessionCookie = "cookie",
            teamId = "team1"
        )

        assertEquals(1, result.size)
        assertEquals("membership1", result[0].id)
        assertEquals("team1", result[0].teamId)
        assertEquals("user1", result[0].userId)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path?.startsWith("/db/teams/_find") == true)
        val body = request.body.readUtf8()
        assertTrue(body.contains("team1"))
        assertTrue(body.contains("membership"))
    }

    @Test
    fun fetchTeamMembers_missingBaseUrl() {
        val exception = assertThrows(IOException::class.java) {
            operations.fetchTeamMembers(
                baseUrl = "",
                credentials = null,
                sessionCookie = null,
                teamId = "team1"
            )
        }
        assertEquals("Missing server base URL", exception.message)
    }

    @Test
    fun fetchTeamMembers_missingTeamId() {
        val exception = assertThrows(IOException::class.java) {
            operations.fetchTeamMembers(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = null,
                sessionCookie = null,
                teamId = ""
            )
        }
        assertEquals("Missing team id", exception.message)
    }

    @Test
    fun fetchTeamMembers_unsuccessfulResponse() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val exception = assertThrows(IOException::class.java) {
            operations.fetchTeamMembers(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = StoredCredentials("u", "p"),
                sessionCookie = null,
                teamId = "team1"
            )
        }
        assertTrue(exception.message?.startsWith("Unexpected response") == true)
    }
}
