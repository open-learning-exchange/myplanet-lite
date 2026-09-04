package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
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
        repository = VoicesComposerRepository(
            client = OkHttpClient.Builder().build(),
            moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `createVoice tags the news document as myplanet-lite`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"ok":true,"id":"news_1","rev":"1-abc"}"""),
        )

        val result = repository.createVoice(
            VoicesComposerRepository.CreateVoiceParams(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = StoredCredentials("testUser", "testPass"),
                sessionCookie = "AuthSession=test-cookie",
                message = "hello",
                createdOn = "planet",
                parentCode = "parent",
                replyTo = null,
                images = emptyList(),
                labels = emptyList(),
                userPayload = null,
            ),
        )

        assertTrue(result.isSuccess)
        val request = mockWebServer.takeRequest()
        assertEquals("/db/news", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("myplanet-lite", body.getString("app"))
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
    fun `ensureReplyImageUpload correctly coordinates creation and upload`() = runTest {
        val creationResponse = """
            {
                "ok": true,
                "id": "new_res_456",
                "rev": "rev_creation"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(creationResponse))

        val uploadResponse = """
            {
                "ok": true,
                "id": "new_res_456",
                "rev": "rev_upload",
                "resourceId": "new_res_456",
                "filename": "image.jpg",
                "markdown": "![](resources/new_res_456/image.jpg)"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(uploadResponse))

        val credentials = StoredCredentials("testUser", "testPass")
        val baseUrl = mockWebServer.url("/").toString()
        val context = VoiceImageResourceContext(
            username = "testUser",
            resideOn = "planet",
            sourcePlanet = "planet",
            androidId = "android123",
            deviceName = "device",
            customDeviceName = "custom",
        )
        val fileName = "image.jpg"
        val bytes = byteArrayOf(4, 5, 6)

        val result = repository.ensureReplyImageUpload(
            baseUrl = baseUrl,
            credentials = credentials,
            context = context,
            fileName = fileName,
            jpegBytes = bytes
        )

        assertNotNull(result)
        assertEquals("new_res_456", result.resourceId)
        assertEquals("rev_upload", result.revision)
        assertEquals("![](resources/new_res_456/image.jpg)", result.markdown)

        val createRequest = mockWebServer.takeRequest()
        assertEquals("POST", createRequest.method)
        assertEquals("/db/resources", createRequest.path)

        val uploadRequest = mockWebServer.takeRequest()
        assertEquals("PUT", uploadRequest.method)
        assertEquals("/db/resources/new_res_456/image.jpg", uploadRequest.path)
        assertEquals("rev_creation", uploadRequest.getHeader("If-Match"))
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
    @Test
    fun `createVoice returns successful response on valid input`() = runTest {
        val validJsonResponse = """
            {
                "ok": true,
                "id": "voice_123",
                "rev": "rev_abc"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(validJsonResponse))

        val credentials = StoredCredentials("testUser", "testPass")
        val baseUrl = mockWebServer.url("/").toString()
        val params = VoicesComposerRepository.CreateVoiceParams(
            baseUrl = baseUrl,
            credentials = credentials,
            sessionCookie = "cookie",
            message = "Test message",
            createdOn = "planet_code",
            parentCode = "parent_code",
            replyTo = null,
            images = emptyList(),
            labels = emptyList(),
            userPayload = null,
            teamId = "team_1",
            teamName = "Team 1"
        )

        val result = repository.createVoice(params)

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(true, response?.ok)
        assertEquals("voice_123", response?.id)
        assertEquals("rev_abc", response?.revision)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/db/news", request.path)
        assertNotNull(request.getHeader("Authorization"))
        assertEquals("cookie", request.getHeader("Cookie"))
    }

    @Test
    fun `createVoice throws exception on missing base URL`() = runTest {
        val params = VoicesComposerRepository.CreateVoiceParams(
            baseUrl = "",
            credentials = null,
            sessionCookie = null,
            message = "Test message",
            createdOn = "planet_code",
            parentCode = "parent_code",
            replyTo = null,
            images = emptyList(),
            labels = emptyList(),
            userPayload = null,
            teamId = "team_1",
            teamName = "Team 1"
        )

        val result = repository.createVoice(params)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Missing server base URL", result.exceptionOrNull()?.message)
    }

}
