package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DashboardNewsActionsRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardNewsActionsRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = DashboardNewsActionsRepository()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun createDocument(
        id: String? = "doc-123",
        revision: String? = "1-abc"
    ) = DashboardNewsRepository.NewsDocument(
        id = id,
        revision = revision,
        docType = "news",
        time = 123456789L,
        createdOn = "planet-code",
        parentCode = "parent-code",
        user = null,
        replyTo = null,
        viewIn = emptyList(),
        messageType = "text",
        messagePlanetCode = "planet-code",
        message = "Test message",
        images = emptyList(),
        updatedDate = 123456789L,
        isDeleted = false
    )

    @Test
    fun deleteNews_successfulResponse_returnsSuccess() = runTest {
        val successResponse = """
            {
                "ok": true,
                "id": "doc-123",
                "rev": "2-def"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(successResponse))

        val result = repository.deleteNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "session=cookie123",
            document = createDocument()
        )

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertEquals(true, response?.ok)
        assertEquals("doc-123", response?.id)
        assertEquals("2-def", response?.revision)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/news/", request.path)
        assertEquals("POST", request.method)
        assertEquals("session=cookie123", request.getHeader("Cookie"))
    }

    @Test
    fun deleteNews_missingBaseUrl_returnsFailure() = runTest {
        val result = repository.deleteNews(
            baseUrl = "",
            sessionCookie = null,
            document = createDocument()
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertEquals("Missing server base URL", exception?.message)
    }

    @Test
    fun deleteNews_missingId_returnsFailure() = runTest {
        val result = repository.deleteNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = null,
            document = createDocument(id = "")
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertEquals("Missing document id", exception?.message)
    }

    @Test
    fun deleteNews_missingRevision_returnsFailure() = runTest {
        val result = repository.deleteNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = null,
            document = createDocument(revision = null)
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertEquals("Missing document revision", exception?.message)
    }

    @Test
    fun deleteNews_serverError_returnsFailure() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = repository.deleteNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = null,
            document = createDocument()
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertEquals("Unexpected response 500", exception?.message)
    }

    @Test
    fun deleteNews_invalidJson_returnsFailure() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val result = repository.deleteNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = null,
            document = createDocument()
        )

        assertTrue(result.isFailure)
    }
}
