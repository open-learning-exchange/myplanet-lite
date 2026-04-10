package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class VoicesComposerRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: VoicesComposerRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = VoicesComposerRepository()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `uploadResourceBinary returns successful response on valid input`() = runTest {
        val validJsonResponse = """
            {
                "ok": true,
                "id": "res_123",
                "rev": "rev_abc",
                "resourceId": "res_123",
                "filename": "audio.mp3",
                "markdown": "link"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(validJsonResponse))

        val credentials = StoredCredentials("testUser", "testPass")
        val baseUrl = mockWebServer.url("/").toString()
        val resourceId = "res_123"
        val fileName = "audio.mp3"
        val revision = "rev_abc"
        val bytes = byteArrayOf(1, 2, 3)

        val response = repository.uploadResourceBinary(
            baseUrl = baseUrl,
            credentials = credentials,
            resourceId = resourceId,
            fileName = fileName,
            revision = revision,
            bytes = bytes
        )

        assertNotNull(response)
        assertEquals(true, response.ok)
        assertEquals("res_123", response.id)
        assertEquals("rev_abc", response.revision)
        assertEquals("res_123", response.resourceId)
        assertEquals("audio.mp3", response.filename)
        assertEquals("link", response.markdown)

        val request = mockWebServer.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/db/resources/res_123/audio.mp3", request.path)
        assertNotNull(request.getHeader("Authorization"))
        assertEquals("rev_abc", request.getHeader("If-Match"))

        val bodyBytes = request.body.readByteArray()
        org.junit.Assert.assertArrayEquals(bytes, bodyBytes)
    }

    @Test
    fun `uploadResourceBinary throws IOException on missing base URL`() = runTest {
        val credentials = StoredCredentials("testUser", "testPass")
        val bytes = byteArrayOf(1, 2, 3)

        try {
            repository.uploadResourceBinary(
                baseUrl = "",
                credentials = credentials,
                resourceId = "res_123",
                fileName = "audio.mp3",
                revision = "rev_abc",
                bytes = bytes
            )
            org.junit.Assert.fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Missing server base URL", e.message)
        }
    }

    @Test
    fun `uploadResourceBinary throws IOException on unsuccessful HTTP response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val credentials = StoredCredentials("testUser", "testPass")
        val baseUrl = mockWebServer.url("/").toString()
        val bytes = byteArrayOf(1, 2, 3)

        try {
            repository.uploadResourceBinary(
                baseUrl = baseUrl,
                credentials = credentials,
                resourceId = "res_123",
                fileName = "audio.mp3",
                revision = "rev_abc",
                bytes = bytes
            )
            org.junit.Assert.fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message?.startsWith("Unexpected response") == true)
        }
    }

    @Test
    fun `uploadResourceBinary throws IOException on invalid JSON response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("null"))

        val credentials = StoredCredentials("testUser", "testPass")
        val baseUrl = mockWebServer.url("/").toString()
        val bytes = byteArrayOf(1, 2, 3)

        try {
            repository.uploadResourceBinary(
                baseUrl = baseUrl,
                credentials = credentials,
                resourceId = "res_123",
                fileName = "audio.mp3",
                revision = "rev_abc",
                bytes = bytes
            )
            org.junit.Assert.fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Invalid response body", e.message)
        }
    }
}
