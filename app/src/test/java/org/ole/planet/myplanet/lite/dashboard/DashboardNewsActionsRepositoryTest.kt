package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Test

class DashboardNewsActionsRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardNewsActionsRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = DashboardNewsActionsRepository(AuthDependencies.client, AuthDependencies.moshi, Dispatchers.IO)
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

    @Test
    fun resolveViewInEntries_withExistingTeamsEntry_updatesMissingFields() {
        val entry = DashboardNewsRepository.ViewInEntry(
            section = "teams",
            id = "team-1",
            isPublic = null,
            name = null,
            mode = null
        )
        val document = createDocument().copy(viewIn = listOf(entry))
        val result = DashboardNewsActionsRepository.resolveViewInEntries(
            document = document,
            teamId = "team-1",
            teamName = "My Team"
        )
        assertEquals(1, result.size)
        val updatedEntry = result[0]
        assertEquals("teams", updatedEntry.section)
        assertEquals("team-1", updatedEntry.id)
        assertEquals(false, updatedEntry.isPublic)
        assertEquals("My Team", updatedEntry.name)
        assertEquals("team", updatedEntry.mode)
    }

    @Test
    fun resolveViewInEntries_withExistingNonTeamsEntry_returnsUnmodified() {
        val entry = DashboardNewsRepository.ViewInEntry(
            section = "community",
            id = "planet@parent"
        )
        val document = createDocument().copy(viewIn = listOf(entry))
        val result = DashboardNewsActionsRepository.resolveViewInEntries(
            document = document,
            teamId = "team-1",
            teamName = "My Team"
        )
        assertEquals(1, result.size)
        val returnedEntry = result[0]
        assertEquals("community", returnedEntry.section)
        assertEquals("planet@parent", returnedEntry.id)
        assertEquals(null, returnedEntry.isPublic)
    }

    @Test
    fun resolveViewInEntries_fallback_withTeamId_buildsTeamEntry() {
        val document = createDocument().copy(viewIn = emptyList())
        val result = DashboardNewsActionsRepository.resolveViewInEntries(
            document = document,
            teamId = "team-2",
            teamName = "Team Two"
        )
        assertEquals(1, result.size)
        val entry = result[0]
        assertEquals("teams", entry.section)
        assertEquals("team-2", entry.id)
        assertEquals(false, entry.isPublic)
        assertEquals("Team Two", entry.name)
        assertEquals("team", entry.mode)
    }

    @Test
    fun resolveViewInEntries_fallback_withoutTeamId_buildsCommunityEntry() {
        val document = createDocument().copy(
            viewIn = emptyList(),
            createdOn = "earth",
            parentCode = "solar"
        )
        val result = DashboardNewsActionsRepository.resolveViewInEntries(
            document = document,
            teamId = null,
            teamName = null
        )
        assertEquals(1, result.size)
        val entry = result[0]
        assertEquals("community", entry.section)
        assertEquals("earth@solar", entry.id)
        assertEquals(null, entry.isPublic)
    }

    @Test
    fun resolveViewInEntries_fallback_missingPlanetOrParent_returnsEmptyList() {
        val document1 = createDocument().copy(viewIn = emptyList(), createdOn = null, parentCode = "solar")
        val result1 = DashboardNewsActionsRepository.resolveViewInEntries(
            document = document1,
            teamId = null,
            teamName = null
        )
        assertTrue(result1.isEmpty())

        val document2 = createDocument().copy(viewIn = emptyList(), createdOn = "earth", parentCode = null)
        val result2 = DashboardNewsActionsRepository.resolveViewInEntries(
            document = document2,
            teamId = null,
            teamName = null
        )
        assertTrue(result2.isEmpty())
    }
}
