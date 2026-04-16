package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import okhttp3.OkHttpClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Before
import org.junit.Test

class DashboardNewsRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardNewsRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = DashboardNewsRepository(
            client = OkHttpClient.Builder().build(),
            moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
            /* using default dispatcher */
        )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchNews returns failure when baseUrl is empty`() = runTest {
        val result = repository.fetchNews(
            baseUrl = "",
            sessionCookie = "cookie",
            skip = 0,
            bookmark = null,
            limit = 10,
            createdOn = null,
            parentCode = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Missing server base URL", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchNews returns failure on non-200 response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = repository.fetchNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            skip = 0,
            bookmark = null,
            limit = 10,
            createdOn = null,
            parentCode = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Unexpected response 500", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchNews returns failure on invalid json response`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("invalid json").setResponseCode(200))

        val result = repository.fetchNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            skip = 0,
            bookmark = null,
            limit = 10,
            createdOn = null,
            parentCode = null
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `fetchNews parses successful response correctly`() = runTest {
        val responseBody = """
            {
                "docs": [
                    {
                        "_id": "doc1",
                        "docType": "news",
                        "replyTo": ""
                    },
                    {
                        "_id": "doc2",
                        "docType": "news",
                        "replyTo": "doc1"
                    },
                    {
                        "_id": "doc3",
                        "docType": "news",
                        "replyTo": "doc1"
                    }
                ],
                "bookmark": "bookmark123"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = repository.fetchNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            skip = 0,
            bookmark = null,
            limit = 10,
            createdOn = "planet1",
            parentCode = "parent1"
        )

        assertTrue(result.isSuccess)
        val page = result.getOrNull()
        assertNotNull(page)

        assertEquals(1, page!!.items.size)
        assertEquals("doc1", page.items[0].id)

        assertEquals(3, page.consumed)

        assertFalse(page.hasMore)

        assertEquals("bookmark123", page.bookmark)

        assertEquals(2, page.commentCounts["doc1"])

        val request = mockWebServer.takeRequest()
        assertEquals("/db/news/_find", request.path)
        assertEquals("cookie", request.getHeader("Cookie"))
    }

    @Test
    fun `fetchNews maps request payload correctly with teamName`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))

        repository.fetchNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "session_id=123",
            skip = 5,
            bookmark = "my_bookmark",
            limit = 20,
            createdOn = "test_planet",
            parentCode = "test_parent",
            teamName = "test_team"
        )

        val request = mockWebServer.takeRequest()
        assertEquals("session_id=123", request.getHeader("Cookie"))
        val bodyStr = request.body.readUtf8()

        assertTrue(bodyStr.contains("\"limit\":20"))
        assertTrue(bodyStr.contains("\"skip\":5"))
        assertTrue(bodyStr.contains("\"bookmark\":\"my_bookmark\""))
        assertTrue(bodyStr.contains("\"createdOn\":\"test_planet\""))
        assertTrue(bodyStr.contains("\"test_team\""))
    }

    @Test
    fun `fetchComments returns failure when baseUrl is empty`() = runTest {
        val result = repository.fetchComments(
            baseUrl = "",
            sessionCookie = "cookie",
            postId = "post123",
            limit = 10,
            createdOn = null,
            parentCode = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Missing server base URL", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchComments returns failure when postId is empty`() = runTest {
        val result = repository.fetchComments(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            postId = "",
            limit = 10,
            createdOn = null,
            parentCode = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Missing post id", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchComments returns failure on non-200 response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = repository.fetchComments(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            postId = "post123",
            limit = 10,
            createdOn = null,
            parentCode = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Unexpected response 500", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchComments parses successful response correctly`() = runTest {
        val responseBody = """
            {
                "docs": [
                    {
                        "_id": "doc1",
                        "docType": "news",
                        "replyTo": "post123"
                    },
                    {
                        "_id": "doc2",
                        "docType": "news",
                        "replyTo": "otherPost"
                    },
                    {
                        "_id": "doc3",
                        "docType": "news",
                        "replyTo": "post123"
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = repository.fetchComments(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            postId = "post123",
            limit = 10,
            createdOn = "planet1",
            parentCode = "parent1"
        )

        assertTrue(result.isSuccess)
        val comments = result.getOrNull()
        assertNotNull(comments)

        assertEquals(2, comments!!.size)
        assertTrue(comments.all { it.replyTo == "post123" })
        assertEquals("doc1", comments[0].id)
        assertEquals("doc3", comments[1].id)
    }
}
