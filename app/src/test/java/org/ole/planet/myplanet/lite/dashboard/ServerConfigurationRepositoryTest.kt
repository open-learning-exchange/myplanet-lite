package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerConfigurationRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: ServerConfigurationRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = ServerConfigurationRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchConfiguration returns parsed document on success`() = runTest {
        val jsonResponse = """
            {
              "rows": [
                {
                  "doc": {
                    "keys": {
                      "openai": "test-key"
                    },
                    "models": {
                      "openai": "test-model"
                    },
                    "preferredLang": "en"
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchConfiguration(baseUrl)

        assertTrue(result.isSuccess)
        val doc = result.getOrNull()
        assertEquals("test-key", doc?.keys?.openAi)
        assertEquals("test-model", doc?.models?.openAi)
        assertEquals("en", doc?.preferredLang)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/configurations/_all_docs?include_docs=true", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun `fetchConfiguration returns null when no rows returned`() = runTest {
        val jsonResponse = """
            {
              "rows": []
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchConfiguration(baseUrl)

        assertTrue(result.isSuccess)
        val doc = result.getOrNull()
        assertEquals(null, doc)
    }

    @Test
    fun `fetchConfiguration fails when base url is missing`() = runTest {
        val result = repository.fetchConfiguration("   ")

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertEquals("Missing base url", exception?.message)
    }

    @Test
    fun `fetchConfiguration fails when base url is invalid`() = runTest {
        val result = repository.fetchConfiguration("invalid-url-scheme://test")

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertEquals("Invalid base url", exception?.message)
    }

    @Test
    fun `fetchConfiguration fails on http error`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchConfiguration(baseUrl)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertEquals("Unexpected response 500", exception?.message)
    }

    @Test
    fun `fetchConfiguration fails when base url is null`() = runTest {
        val result = repository.fetchConfiguration(null)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertEquals("Missing base url", exception?.message)
    }

    @Test
    fun `fetchConfiguration normalizes base url`() = runTest {
        val jsonResponse = "{\"rows\": []}"
        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = "  " + mockWebServer.url("/").toString() + "///  "
        val result = repository.fetchConfiguration(baseUrl)

        assertTrue(result.isSuccess)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/configurations/_all_docs?include_docs=true", request.path)
    }

    @Test
    fun `fetchConfiguration ignores rows with null doc`() = runTest {
        val jsonResponse = """
            {
              "rows": [
                {
                  "doc": null
                },
                {
                  "doc": {
                    "keys": {
                      "openai": "test-key-2"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchConfiguration(baseUrl)

        assertTrue(result.isSuccess)
        val doc = result.getOrNull()
        assertEquals("test-key-2", doc?.keys?.openAi)
    }

    @Test
    fun `fetchConfiguration fails when json is malformed`() = runTest {
        val malformedJson = "{ malformed json "
        mockWebServer.enqueue(MockResponse().setBody(malformedJson).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchConfiguration(baseUrl)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is com.squareup.moshi.JsonDataException || exception is java.io.EOFException || exception is com.squareup.moshi.JsonEncodingException)
    }

    @Test
    fun `fetchConfiguration fails on network exception`() = runTest {
        val errorClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                throw IOException("Network error")
            }
            .build()
        val errorRepository = ServerConfigurationRepository(client = errorClient)

        val baseUrl = "http://localhost:8080"
        val result = errorRepository.fetchConfiguration(baseUrl)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertEquals("Network error", exception?.message)
    }
}
