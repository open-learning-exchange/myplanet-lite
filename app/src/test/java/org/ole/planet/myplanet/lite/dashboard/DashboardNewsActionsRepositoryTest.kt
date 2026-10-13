package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun enqueueUpdate(serverDocument: String) {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(serverDocument))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{ "ok": true, "id": "doc-123", "rev": "4-ghi" }"""))
    }

    private fun savedDocument(): org.json.JSONObject {
        mockWebServer.takeRequest()
        return org.json.JSONObject(mockWebServer.takeRequest().body.readUtf8())
    }

    @Test
    fun updateNews_preservesOriginalAppField() = runTest {
        enqueueUpdate("""{ "_id": "doc-123", "_rev": "1-abc", "message": "Test message", "app": "myplanet" }""")

        val result = repository.updateNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = null,
            document = createDocument(),
            message = "Updated message",
            images = emptyList(),
        )

        assertTrue(result.isSuccess)
        assertEquals("myplanet", savedDocument().getString("app"))
    }

    @Test
    fun updateNews_omitsAppWhenDocumentHasNone() = runTest {
        enqueueUpdate("""{ "_id": "doc-123", "_rev": "1-abc", "message": "Test message" }""")

        val result = repository.updateNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = null,
            document = createDocument(),
            message = "Updated message",
            images = emptyList(),
        )

        assertTrue(result.isSuccess)
        assertFalse(savedDocument().has("app"))
    }

    @Test
    fun updateNews_editsTheLatestServerCopyAndKeepsFieldsItDoesNotModel() = runTest {
        enqueueUpdate(
            """
            {
                "_id": "doc-123",
                "_rev": "3-new",
                "message": "Test message",
                "labels": [ "help" ],
                "reactions": { "👍": [ "org.couchdb.user:ana" ] },
                "user": { "_id": "org.couchdb.user:ana", "name": "ana", "planetCode": "planet-code" },
                "viewIn": [ { "_id": "planet-code@parent-code", "section": "community", "sharedDate": 1700000000000 } ]
            }
            """.trimIndent(),
        )

        val result = repository.updateNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = "session=cookie123",
            document = createDocument(),
            message = "Updated message",
            images = emptyList(),
        )

        assertTrue(result.isSuccess)
        val fetch = mockWebServer.takeRequest()
        assertEquals("GET", fetch.method)
        assertEquals("/db/news/doc-123", fetch.path)
        assertEquals("session=cookie123", fetch.getHeader("Cookie"))
        val saved = org.json.JSONObject(mockWebServer.takeRequest().body.readUtf8())
        assertEquals("3-new", saved.getString("_rev"))
        assertEquals("Updated message", saved.getString("message"))
        assertEquals("help", saved.getJSONArray("labels").getString(0))
        assertEquals("org.couchdb.user:ana", saved.getJSONObject("reactions").getJSONArray("👍").getString(0))
        assertEquals("planet-code", saved.getJSONObject("user").getString("planetCode"))
        assertEquals(1700000000000L, saved.getJSONArray("viewIn").getJSONObject(0).getLong("sharedDate"))
    }

    @Test
    fun updateNews_failedFetch_writesNothing() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "not_found" }"""))

        val result = repository.updateNews(
            baseUrl = mockWebServer.url("/").toString(),
            sessionCookie = null,
            document = createDocument(),
            message = "Updated message",
            images = emptyList(),
        )

        assertTrue(result.isFailure)
        assertEquals("Unexpected response 404", result.exceptionOrNull()?.message)
        assertEquals(1, mockWebServer.requestCount)
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
