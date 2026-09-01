package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.DateStringAdapter

class DashboardTeamsUserOperationsTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var operations: DashboardTeamsUserOperations
    private val credentials = StoredCredentials("testuser", "testpass")

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val moshi = Moshi.Builder()
            .add(DateStringAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
        operations = DashboardTeamsUserOperations(OkHttpClient(), moshi)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun fetchTeamMemberProfileDetails_success() {
        val responseBody = """
            {
                "_id": "org.couchdb.user:testuser",
                "name": "testuser",
                "firstName": "Test",
                "lastName": "User",
                "email": "test@example.com",
                "_attachments": {
                    "img": {
                        "content_type": "image/png"
                    }
                }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = operations.fetchTeamMemberProfileDetails(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = "cookie",
            username = "testuser"
        )

        assertEquals("testuser", result.username)
        assertEquals("Test", result.firstName)
        assertEquals("User", result.lastName)
        assertEquals("test@example.com", result.email)
        assertTrue(result.hasAvatar)
        assertEquals("Test User", result.fullName)
    }

    @Test
    fun fetchTeamMemberProfileDetails_missingBaseUrl() {
        val exception = assertThrows(IOException::class.java) {
            operations.fetchTeamMemberProfileDetails(
                baseUrl = "",
                credentials = credentials,
                sessionCookie = null,
                username = "testuser"
            )
        }
        assertEquals("Missing base url", exception.message)
    }

    @Test
    fun fetchTeamMemberProfileDetails_notFound() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val exception = assertThrows(IOException::class.java) {
            operations.fetchTeamMemberProfileDetails(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = credentials,
                sessionCookie = null,
                username = "testuser"
            )
        }
        assertEquals("Profile not found", exception.message)
    }

    @Test
    fun fetchUserProfiles_success() {
        val responseBody = """
            {
                "docs": [
                    {
                        "_id": "user1",
                        "firstName": "User",
                        "lastName": "One"
                    },
                    {
                        "_id": "user2",
                        "firstName": "User",
                        "lastName": "Two"
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = operations.fetchUserProfiles(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = "cookie",
            userIds = listOf("user1", "user2")
        )

        assertEquals(2, result.size)
        assertEquals("user1", result[0]._id)
        assertEquals("User", result[0].firstName)
        assertEquals("user2", result[1]._id)
    }

    @Test
    fun fetchUserProfiles_emptyIds() {
        val result = operations.fetchUserProfiles(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = null,
            userIds = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchUserProfiles_missingCredentials() {
        val exception = assertThrows(IOException::class.java) {
            operations.fetchUserProfiles(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = null,
                sessionCookie = null,
                userIds = listOf("user1")
            )
        }
        assertEquals("Missing credentials for basic auth", exception.message)
    }

    @Test
    fun fetchUserProfiles_unexpectedResponse() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val exception = assertThrows(IOException::class.java) {
            operations.fetchUserProfiles(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = credentials,
                sessionCookie = null,
                userIds = listOf("user1")
            )
        }
        assertTrue(exception.message?.startsWith("Unexpected response") == true)
    }

    @Test
    fun fetchAllUsers_success() {
        val responseBody = """
            {
                "docs": [
                    {
                        "_id": "user1",
                        "planetCode": "planet1",
                        "parentCode": "parent1"
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val request = FetchUsersRequest(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = "cookie",
            planetCode = "planet1",
            parentCode = "parent1",
            pageSize = 10,
            skip = 0,
            searchTerm = "test",
            excludedUserIds = listOf("excluded1")
        )

        val result = operations.fetchAllUsers(request)

        assertEquals(1, result.size)
        assertEquals("user1", result[0]._id)

        val takeRequest = mockWebServer.takeRequest()
        val requestBody = takeRequest.body.readUtf8()
        assertTrue(requestBody.contains("planet1"))
        assertTrue(requestBody.contains("parent1"))
        assertTrue(requestBody.contains("excluded1"))
        assertTrue(requestBody.contains("(?i)test"))
    }

    @Test
    fun fetchAllUsers_zeroPageSize() {
        val request = FetchUsersRequest(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = null,
            planetCode = "planet1",
            parentCode = "parent1",
            pageSize = 0
        )

        val result = operations.fetchAllUsers(request)
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchAllUsers_missingPlanetCode() {
        val request = FetchUsersRequest(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = null,
            planetCode = "",
            parentCode = "parent1"
        )

        val exception = assertThrows(IOException::class.java) {
            operations.fetchAllUsers(request)
        }
        assertEquals("Missing planet code for user search", exception.message)
    }

    @Test
    fun fetchAllUsers_missingParentCode() {
        val request = FetchUsersRequest(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = credentials,
            sessionCookie = null,
            planetCode = "planet1",
            parentCode = ""
        )

        val exception = assertThrows(IOException::class.java) {
            operations.fetchAllUsers(request)
        }
        assertEquals("Missing parent code for user search", exception.message)
    }
}
