package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class DashboardCoursesRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardCoursesRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = DashboardCoursesRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun fetchUserCourseIds_success() = runTest {
        val jsonResponse = "{\"docs\": [{\"_id\": \"org.couchdb.user:testuser\", \"courseIds\": [\"course1\", \"course2\"]}]}"

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchUserCourseIds(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass")
        )

        assertTrue(result.isSuccess)
        val courses = result.getOrNull()
        assertEquals(2, courses?.size)
        assertEquals("course1", courses?.get(0))
        assertEquals("course2", courses?.get(1))

        val request = mockWebServer.takeRequest()
        assertEquals("/db/shelf/_find", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Authorization")?.startsWith("Basic") == true)

        val expectedRequestBody = "{\"selector\":{\"_id\":\"org.couchdb.user:testuser\"}}"
        assertEquals(expectedRequestBody, request.body.readUtf8())
    }

    @Test
    fun fetchUserCourseIds_missingBaseUrl() = runTest {
        val result = repository.fetchUserCourseIds(
            baseUrl = "  ",
            credentials = StoredCredentials("testuser", "pass")
        )
        assertTrue(result.isFailure)
        assertEquals("Missing server base URL", result.exceptionOrNull()?.message)
    }

    @Test
    fun fetchUserCourseIds_httpError() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchUserCourseIds(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass")
        )

        assertTrue(result.isFailure)
        assertEquals("Unexpected response 500", result.exceptionOrNull()?.message)
    }

    @Test
    fun fetchShelfDocument_success() = runTest {
        val jsonResponse = "{\"_id\": \"org.couchdb.user:testuser\", \"_rev\": \"1-abc\", \"courseIds\": [\"c1\"]}"

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchShelfDocument(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass")
        )

        assertTrue(result.isSuccess)
        val doc = result.getOrNull()
        assertEquals("org.couchdb.user:testuser", doc?.id)
        assertEquals("1-abc", doc?.rev)
        assertEquals(listOf("c1"), doc?.courseIds)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/shelf/org.couchdb.user:testuser", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun fetchShelfDocument_missingReason() = runTest {
        val jsonResponse = "{\"error\": \"not_found\", \"reason\": \"missing\"}"

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(404))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchShelfDocument(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass")
        )

        assertTrue(result.isSuccess)
        val doc = result.getOrNull()
        assertEquals("org.couchdb.user:testuser", doc?.id)
        assertEquals(null, doc?.rev)
    }

    @Test
    fun joinCourse_success_withCachedShelf() = runTest {
        // First pre-cache shelf document
        val shelfJson = "{\"docs\": [{\"_id\": \"org.couchdb.user:testuser\", \"_rev\": \"1-abc\", \"courseIds\": [\"c1\"]}]}"
        mockWebServer.enqueue(MockResponse().setBody(shelfJson).setResponseCode(200))
        val baseUrl = mockWebServer.url("/").toString()
        repository.fetchUserCourseIds(baseUrl, StoredCredentials("testuser", "pass"))
        mockWebServer.takeRequest() // Clear queue

        // Now mock the join course response
        val putResponse = "{\"ok\": true, \"id\": \"org.couchdb.user:testuser\", \"rev\": \"2-def\"}"
        mockWebServer.enqueue(MockResponse().setBody(putResponse).setResponseCode(200))

        val result = repository.joinCourse(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass"),
            courseId = "c2"
        )

        assertTrue(result.isSuccess)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/shelf/org.couchdb.user:testuser", request.path)
        assertEquals("PUT", request.method)
        val requestBody = request.body.readUtf8()
        assertTrue(requestBody.contains("\"courseIds\":[\"c1\",\"c2\"]"))
    }

    @Test
    fun joinCourse_success_noCache() = runTest {
        // Enqueue fetchShelfDocument response
        val shelfJson = "{\"_id\": \"org.couchdb.user:testuser\", \"_rev\": \"1-abc\", \"courseIds\": [\"c1\"]}"
        mockWebServer.enqueue(MockResponse().setBody(shelfJson).setResponseCode(200))

        // Enqueue the PUT response
        val putResponse = "{\"ok\": true, \"id\": \"org.couchdb.user:testuser\", \"rev\": \"2-def\"}"
        mockWebServer.enqueue(MockResponse().setBody(putResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.joinCourse(
            baseUrl = baseUrl,
            credentials = StoredCredentials("testuser", "pass"),
            courseId = "c2"
        )

        assertTrue(result.isSuccess)

        val req1 = mockWebServer.takeRequest()
        assertEquals("/db/shelf/org.couchdb.user:testuser", req1.path)
        assertEquals("GET", req1.method)

        val req2 = mockWebServer.takeRequest()
        assertEquals("/db/shelf/org.couchdb.user:testuser", req2.path)
        assertEquals("PUT", req2.method)
        val requestBody = req2.body.readUtf8()
        assertTrue(requestBody.contains("\"courseIds\":[\"c1\",\"c2\"]"))
    }
}
