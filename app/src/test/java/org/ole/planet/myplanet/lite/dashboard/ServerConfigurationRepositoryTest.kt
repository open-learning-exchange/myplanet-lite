package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerConfigurationRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: ServerConfigurationRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = ServerConfigurationRepository()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchConfiguration returns failure when baseUrl is null`() = runTest {
        val result = repository.fetchConfiguration(null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Missing base url", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchConfiguration returns failure when baseUrl is empty`() = runTest {
        val result = repository.fetchConfiguration("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Missing base url", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchConfiguration returns failure on non-200 response`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = repository.fetchConfiguration(mockWebServer.url("/").toString())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Unexpected response 500", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchConfiguration returns failure on invalid json response`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("invalid json").setResponseCode(200))

        val result = repository.fetchConfiguration(mockWebServer.url("/").toString())

        assertTrue(result.isFailure)
    }

    @Test
    fun `fetchConfiguration parses successful response correctly`() = runTest {
        val responseBody = """
            {
                "rows": [
                    {
                        "doc": {
                            "keys": {
                                "openai": "sk-12345"
                            },
                            "models": {
                                "openai": "gpt-4"
                            },
                            "preferredLang": "en"
                        }
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = repository.fetchConfiguration(mockWebServer.url("/").toString())

        assertTrue(result.isSuccess)
        val doc = result.getOrNull()
        assertNotNull(doc)

        assertEquals("sk-12345", doc?.keys?.openAi)
        assertEquals("gpt-4", doc?.models?.openAi)
        assertEquals("en", doc?.preferredLang)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/configurations/_all_docs?include_docs=true", request.path)
    }

    @Test
    fun `fetchConfiguration parses successful response with empty rows correctly`() = runTest {
        val responseBody = """
            {
                "rows": []
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = repository.fetchConfiguration(mockWebServer.url("/").toString())

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `fetchConfiguration parses successful response with null doc correctly`() = runTest {
        val responseBody = """
            {
                "rows": [
                    {
                        "doc": null
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = repository.fetchConfiguration(mockWebServer.url("/").toString())

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }
}
