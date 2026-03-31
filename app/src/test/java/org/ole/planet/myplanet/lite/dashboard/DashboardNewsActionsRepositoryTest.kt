package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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

    @Test
    fun testServerBaseUrlEmpty() = runTest {
        val document = DashboardNewsRepository.NewsDocument(
            id = "doc1",
            revision = "rev1",
            docType = "news",
            time = 12345L,
            createdOn = "planet1",
            parentCode = "parent1",
            user = null,
            replyTo = null,
            viewIn = null,
            messageType = null,
            messagePlanetCode = null,
            message = "Old message",
            images = null,
            updatedDate = null,
            isDeleted = false
        )
        val result = repository.updateNews(
            baseUrl = "",
            sessionCookie = "cookie",
            document = document,
            message = "New message",
            images = emptyList()
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertEquals("Missing server base URL", ex?.message)
    }

    @Test
    fun testMissingId() = runTest {
        val document = DashboardNewsRepository.NewsDocument(
            id = "",
            revision = "rev1",
            docType = "news",
            time = 12345L,
            createdOn = "planet1",
            parentCode = "parent1",
            user = null,
            replyTo = null,
            viewIn = null,
            messageType = null,
            messagePlanetCode = null,
            message = "Old message",
            images = null,
            updatedDate = null,
            isDeleted = false
        )
        val result = repository.updateNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            document = document,
            message = "New message",
            images = emptyList()
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertEquals("Missing document id", ex?.message)
    }

    @Test
    fun testMissingRevision() = runTest {
        val document = DashboardNewsRepository.NewsDocument(
            id = "id1",
            revision = null,
            docType = "news",
            time = 12345L,
            createdOn = "planet1",
            parentCode = "parent1",
            user = null,
            replyTo = null,
            viewIn = null,
            messageType = null,
            messagePlanetCode = null,
            message = "Old message",
            images = null,
            updatedDate = null,
            isDeleted = false
        )
        val result = repository.updateNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            document = document,
            message = "New message",
            images = emptyList()
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertEquals("Missing document revision", ex?.message)
    }

    @Test
    fun testUpdateNewsUnexpectedResponse() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val document = DashboardNewsRepository.NewsDocument(
            id = "doc1",
            revision = "rev1",
            docType = "news",
            time = 12345L,
            createdOn = "planet1",
            parentCode = "parent1",
            user = null,
            replyTo = null,
            viewIn = null,
            messageType = null,
            messagePlanetCode = null,
            message = "Old message",
            images = null,
            updatedDate = null,
            isDeleted = false
        )

        val result = repository.updateNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            document = document,
            message = "New message",
            images = emptyList()
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertEquals("Unexpected response 500", ex?.message)
    }

    @Test
    fun testUpdateNewsInvalidResponseBody() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("invalid_json"))

        val document = DashboardNewsRepository.NewsDocument(
            id = "doc1",
            revision = "rev1",
            docType = "news",
            time = 12345L,
            createdOn = "planet1",
            parentCode = "parent1",
            user = null,
            replyTo = null,
            viewIn = null,
            messageType = null,
            messagePlanetCode = null,
            message = "Old message",
            images = null,
            updatedDate = null,
            isDeleted = false
        )

        val result = repository.updateNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            document = document,
            message = "New message",
            images = emptyList()
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex?.message?.contains("Use JsonReader.setLenient(true) to accept malformed JSON") == true)
    }

    @Test
    fun testUpdateNewsSuccess() = runTest {
        val jsonResponse = """
            {
                "ok": true,
                "id": "doc1",
                "rev": "rev2"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val document = DashboardNewsRepository.NewsDocument(
            id = "doc1",
            revision = "rev1",
            docType = "news",
            time = 12345L,
            createdOn = "planet1",
            parentCode = "parent1",
            user = null,
            replyTo = null,
            viewIn = null,
            messageType = null,
            messagePlanetCode = null,
            message = "Old message",
            images = null,
            updatedDate = null,
            isDeleted = false
        )

        val images = listOf(DashboardNewsRepository.NewsImage("res1", "file1.png", null))

        val result = repository.updateNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            document = document,
            message = "New message",
            images = images
        )

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(true, response?.ok)
        assertEquals("doc1", response?.id)
        assertEquals("rev2", response?.revision)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/db/news/", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        assertEquals("cookie", recordedRequest.getHeader("Cookie"))

        val requestBody = recordedRequest.body.readUtf8()
        assertTrue(requestBody.contains("\"_id\":\"doc1\""))
        assertTrue(requestBody.contains("\"_rev\":\"rev1\""))
        assertTrue(requestBody.contains("\"message\":\"New message\""))
        assertTrue(requestBody.contains("\"images\":[{\"resourceId\":\"res1\",\"filename\":\"file1.png\"}]"))
    }

    @Test
    fun testUpdateNewsTeamViewIn() = runTest {
        val jsonResponse = """
            {
                "ok": true,
                "id": "doc1",
                "rev": "rev2"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val document = DashboardNewsRepository.NewsDocument(
            id = "doc1",
            revision = "rev1",
            docType = "news",
            time = 12345L,
            createdOn = "planet1",
            parentCode = "parent1",
            user = null,
            replyTo = null,
            viewIn = null,
            messageType = null,
            messagePlanetCode = null,
            message = "Old message",
            images = null,
            updatedDate = null,
            isDeleted = false
        )

        val result = repository.updateNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "cookie",
            document = document,
            message = "New message",
            images = emptyList(),
            teamId = "team1",
            teamName = "Team 1"
        )

        assertTrue(result.isSuccess)

        val recordedRequest = mockWebServer.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        assertTrue(requestBody.contains("\"viewIn\":[{\"section\":\"teams\",\"_id\":\"team1\",\"public\":false,\"name\":\"Team 1\",\"mode\":\"team\"}]"))
    }
}
