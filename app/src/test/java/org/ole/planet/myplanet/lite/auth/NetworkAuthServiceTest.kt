/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-07
 */

package org.ole.planet.myplanet.lite.auth

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class NetworkAuthServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenStorage: FakeTokenStorage
    private lateinit var service: AuthService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        tokenStorage = FakeTokenStorage()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val api = retrofit.create(AuthApi::class.java)
        service = NetworkAuthService(api, tokenStorage, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `login success stores token`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "AuthSession=abc123; Path=/")
                .setBody("""{"ok":true,"name":"user@planet.com","roles":["learner"]}""")
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Success)
        val success = result as AuthResult.Success
        assertEquals("AuthSession=abc123; Path=/", tokenStorage.token)
        assertEquals("AuthSession=abc123; Path=/", success.response.sessionCookie)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/_session", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"name\":\"user@planet.com\""))
        assertTrue(body.contains("\"password\":\"secret\""))
    }

    @Test
    fun `login invalid credentials returns error`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"invalid"}""")
        )

        val result = service.login("user@planet.com", "bad")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(401, error.code)
        assertTrue(error.message.contains("invalid"))
    }

    @Test
    fun `login network failure returns error`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertTrue(error.message.contains("Error de red"))
    }

    @Test
    fun `login returns raw body when error JSON is malformed`() = runTest {
        val malformedJson = "Mal-formed { JSON"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(malformedJson)
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(400, error.code)
        assertEquals(malformedJson, error.message)
    }

    @Test
    fun `login returns raw body when error JSON has unknown keys`() = runTest {
        val unknownKeysJson = """{"unknown_key":"bad"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(unknownKeysJson)
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(400, error.code)
        assertEquals(unknownKeysJson, error.message)
    }

    private class FakeTokenStorage : TokenStorage {
        var token: String? = null

        override suspend fun saveToken(token: String) {
            this.token = token
        }

        override suspend fun getToken(): String? = token

        override suspend fun clearToken() {
            token = null
        }
    }

    @Test
    fun `login http exception returns error`() = runTest {
        val mockApi = mock<AuthApi>()
        val errorResponse = Response.error<LoginResponse>(
            500,
            "Internal Server Error".toResponseBody("text/plain".toMediaType())
        )
        whenever(mockApi.login(any())).thenThrow(HttpException(errorResponse))

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val result = serviceWithMockApi.login("user", "pass")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(500, error.code)
    }

    @Test
    fun `logout clears token`() = runTest {
        tokenStorage.saveToken("some_token")
        assertEquals("some_token", tokenStorage.token)

        service.logout()

        assertEquals(null, tokenStorage.token)
    }

    @Test
    fun `getStoredToken returns token`() = runTest {
        tokenStorage.saveToken("another_token")

        val token = service.getStoredToken()

        assertEquals("another_token", token)
    }

    @Test
    fun `getStoredToken returns null when empty`() = runTest {
        tokenStorage.clearToken()

        val token = service.getStoredToken()

        assertEquals(null, token)
    }

}
