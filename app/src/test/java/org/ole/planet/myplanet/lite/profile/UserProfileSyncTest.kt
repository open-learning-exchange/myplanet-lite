package org.ole.planet.myplanet.lite.profile

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class UserProfileSyncTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var mockDatabase: UserProfileDatabase
    private lateinit var userProfileSync: UserProfileSync

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        okHttpClient = OkHttpClient()
        mockDatabase = mock()
        userProfileSync = UserProfileSync(okHttpClient, mockDatabase)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `refreshProfile with empty server base URL returns false`() = runTest {
        val result = userProfileSync.refreshProfile("   ", "testuser", null)
        assertFalse(result)
    }

    @Test
    fun `refreshProfile with failing network request returns false`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        val baseUrl = mockWebServer.url("/").toString()

        val result = userProfileSync.refreshProfile(baseUrl, "testuser", "my_cookie")

        assertFalse(result)
        val request = mockWebServer.takeRequest()
        assertEquals("/db/_users/org.couchdb.user:testuser", request.path)
        assertEquals("my_cookie", request.getHeader("Cookie"))
    }

    @Test
    fun `refreshProfile with empty body returns false`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val baseUrl = mockWebServer.url("/").toString()

        val result = userProfileSync.refreshProfile(baseUrl, "testuser", null)

        assertFalse(result)
    }

    @Test
    fun `refreshProfile with valid profile and no avatar saves to database and returns true`() = runTest {
        val jsonResponse = """
            {
                "name": "testuser",
                "firstName": "Test",
                "lastName": "User",
                "email": "test@example.com",
                "isUserAdmin": true,
                "_rev": "1-abc"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))
        val baseUrl = mockWebServer.url("/").toString()

        val result = userProfileSync.refreshProfile(baseUrl, "testuser", "my_cookie")

        assertTrue(result)

        val captor = argumentCaptor<UserProfile>()
        verify(mockDatabase).saveProfile(captor.capture())

        val savedProfile = captor.firstValue
        assertEquals("testuser", savedProfile.username)
        assertEquals("Test", savedProfile.firstName)
        assertEquals("User", savedProfile.lastName)
        assertEquals("test@example.com", savedProfile.email)
        assertTrue(savedProfile.isUserAdmin)
        assertEquals("1-abc", savedProfile.revision)
        assertNull(savedProfile.avatarImage)
    }

    @Test
    fun `refreshProfile with valid profile and avatar fetches avatar and saves to database`() = runTest {
        val jsonResponse = """
            {
                "name": "avataruser",
                "_attachments": {
                    "img": {
                        "content_type": "image/jpeg",
                        "length": 100
                    }
                }
            }
        """.trimIndent()

        val avatarBytes = byteArrayOf(1, 2, 3, 4, 5)

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/db/_users/org.couchdb.user:avataruser" -> {
                        MockResponse().setResponseCode(200).setBody(jsonResponse)
                    }
                    "/db/_users/org.couchdb.user:avataruser/img" -> {
                        // Check that cookie was passed
                        assertEquals("cookie123", request.getHeader("Cookie"))
                        MockResponse().setResponseCode(200).setBody(okio.Buffer().write(avatarBytes))
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        mockWebServer.dispatcher = dispatcher
        val baseUrl = mockWebServer.url("/").toString()

        val result = userProfileSync.refreshProfile(baseUrl, "avataruser", "cookie123")

        assertTrue(result)

        val captor = argumentCaptor<UserProfile>()
        verify(mockDatabase).saveProfile(captor.capture())

        val savedProfile = captor.firstValue
        assertEquals("avataruser", savedProfile.username)
        assertNotNull(savedProfile.avatarImage)
        assertTrue(savedProfile.avatarImage!!.contentEquals(avatarBytes))

        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `refreshProfile with valid profile and avatar but avatar fetch fails saves profile without avatar`() = runTest {
         val jsonResponse = """
            {
                "name": "avataruser_fail",
                "_attachments": {
                    "img": {
                        "content_type": "image/jpeg",
                        "length": 100
                    }
                }
            }
        """.trimIndent()

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/db/_users/org.couchdb.user:avataruser_fail" -> {
                        MockResponse().setResponseCode(200).setBody(jsonResponse)
                    }
                    "/db/_users/org.couchdb.user:avataruser_fail/img" -> {
                        MockResponse().setResponseCode(404) // Failed fetch
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        mockWebServer.dispatcher = dispatcher
        val baseUrl = mockWebServer.url("/").toString()

        val result = userProfileSync.refreshProfile(baseUrl, "avataruser_fail", null)

        assertTrue(result)

        val captor = argumentCaptor<UserProfile>()
        verify(mockDatabase).saveProfile(captor.capture())

        val savedProfile = captor.firstValue
        assertEquals("avataruser_fail", savedProfile.username)
        assertNull(savedProfile.avatarImage) // Avatar fetch failed, should be null
    }

    @Test
    fun `refreshProfile with exception returns false`() = runTest {
        val failingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                throw IOException("Network error")
            }
            .build()
        val failingSync = UserProfileSync(failingClient, mockDatabase)

        val result = failingSync.refreshProfile("http://localhost", "testuser", null)
        assertFalse(result)
    }

    @Test
    fun `clearProfile calls database clearProfile`() = runTest {
        userProfileSync.clearProfile()
        verify(mockDatabase).clearProfile()
    }
}
