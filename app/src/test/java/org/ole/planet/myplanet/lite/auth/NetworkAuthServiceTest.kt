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
import org.mockito.kotlin.verify
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
    fun `login success without cookie clears token`() = runTest {
        tokenStorage.saveToken("old_token")
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"name":"user@planet.com","roles":["learner"]}""")
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Success)
        val success = result as AuthResult.Success
        assertEquals(null, tokenStorage.token)
        assertEquals(null, success.response.sessionCookie)
    }

    @Test
    fun `login success with empty cookie clears token`() = runTest {
        tokenStorage.saveToken("old_token")
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "")
                .setBody("""{"ok":true,"name":"user@planet.com","roles":["learner"]}""")
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Success)
        val success = result as AuthResult.Success
        assertEquals(null, tokenStorage.token)
        assertEquals("", success.response.sessionCookie)
    }

    @Test
    fun `login success with blank cookie clears token`() = runTest {
        tokenStorage.saveToken("old_token")
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "   ")
                .setBody("""{"ok":true,"name":"user@planet.com","roles":["learner"]}""")
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Success)
        val success = result as AuthResult.Success
        assertEquals(null, tokenStorage.token)
        assertEquals("", success.response.sessionCookie)
    }

    @Test
    fun `login success with null body returns invalid response error`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(204, error.code)
        assertEquals("Respuesta inválida del servidor", error.message)
    }

    @Test
    fun `login success with ok false returns server rejected error`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":false,"name":"user@planet.com","roles":["learner"]}""")
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(200, error.code)
        assertEquals("El servidor rechazó el inicio de sesión", error.message)
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
    fun `login error with empty error body returns default error message`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("")
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(500, error.code)
        assertEquals("Error 500", error.message)
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

    @Test
    fun `login error parses message key`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"message":"Invalid request message"}""")
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(400, error.code)
        assertEquals("Invalid request message", error.message)
    }

    @Test
    fun `login error parses detail key`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"detail":"Detailed error"}""")
        )

        val result = service.login("user@planet.com", "secret")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(400, error.code)
        assertEquals("Detailed error", error.message)
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
        val httpException = HttpException(errorResponse)
        whenever(mockApi.login(any())).thenThrow(httpException)

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val result = serviceWithMockApi.login("user", "pass")

        assertTrue(result is AuthResult.Failure.NetworkError)
        val error = result as AuthResult.Failure.NetworkError
        assertEquals(httpException, error.http)
    }

    @Test
    fun `login http exception 401 returns error`() = runTest {
        val mockApi = mock<AuthApi>()
        val errorResponse = Response.error<LoginResponse>(
            401,
            "Unauthorized".toResponseBody("text/plain".toMediaType())
        )
        whenever(mockApi.login(any())).thenThrow(HttpException(errorResponse))

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val result = serviceWithMockApi.login("user", "pass")

        assertEquals(AuthResult.Failure.InvalidCredentials, result)
    }

    @Test
    fun `login http exception 404 returns error`() = runTest {
        val mockApi = mock<AuthApi>()
        val errorResponse = Response.error<LoginResponse>(
            404,
            "Not Found".toResponseBody("text/plain".toMediaType())
        )
        whenever(mockApi.login(any())).thenThrow(HttpException(errorResponse))

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val result = serviceWithMockApi.login("user", "pass")

        assertEquals(AuthResult.Failure.InvalidCredentials, result)
    }

    @Test
    fun `login io exception returns network error`() = runTest {
        val mockApi = mock<AuthApi>()
        val ioException = java.io.IOException("Custom IO error")
        whenever(mockApi.login(any())).thenAnswer { throw ioException }

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val result = serviceWithMockApi.login("user", "pass")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(null, error.code)
        assertTrue(error.message.contains("Error de red: Custom IO error"))
    }

    @Test
    fun `login io exception localized message is used`() = runTest {
        val mockApi = mock<AuthApi>()
        val ioException = object : java.io.IOException("Regular message") {
            override fun getLocalizedMessage(): String {
                return "Localized IO error"
            }
        }
        whenever(mockApi.login(any())).thenAnswer { throw ioException }

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val result = serviceWithMockApi.login("user", "pass")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(null, error.code)
        assertTrue(error.message.contains("Error de red: Localized IO error"))
    }

    @Test
    fun `login io exception without message returns network error fallback`() = runTest {
        val mockApi = mock<AuthApi>()
        val ioException = java.io.IOException() // localizedMessage and message are null
        whenever(mockApi.login(any())).thenAnswer { throw ioException }

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val result = serviceWithMockApi.login("user", "pass")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(null, error.code)
        assertTrue(error.message.contains("Error de red: null"))
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun `login cancellation exception is rethrown`() = runTest {
        val mockApi = mock<AuthApi>()
        whenever(mockApi.login(any())).thenThrow(kotlinx.coroutines.CancellationException("Cancelled"))

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        serviceWithMockApi.login("user", "pass")
    }

    @Test
    fun `login unexpected exception returns generic error`() = runTest {
        val mockApi = mock<AuthApi>()
        val unexpectedException = RuntimeException("Something bad happened")
        whenever(mockApi.login(any())).thenThrow(unexpectedException)

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val result = serviceWithMockApi.login("user", "pass")

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(null, error.code)
        assertTrue(error.message.contains("Error inesperado: Something bad happened"))
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

    @Test
    fun `getStoredToken invokes getToken on mocked TokenStorage`() = runTest {
        val mockTokenStorage = mock<TokenStorage>()
        whenever(mockTokenStorage.getToken()).thenReturn("mock_token")
        val mockApi = mock<AuthApi>()
        val serviceWithMock = NetworkAuthService(mockApi, mockTokenStorage, Dispatchers.Unconfined)

        val token = serviceWithMock.getStoredToken()

        verify(mockTokenStorage).getToken()
        assertEquals("mock_token", token)
    }



    @Test
    fun `authenticate io exception returns error`() = runTest {
        val mockApi = mock<AuthApi>()
        val ioException = java.io.IOException("Custom IO error")
        whenever(mockApi.authenticate(any(), any())).thenAnswer { throw ioException }

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val credentials = UserCredentials("user", "pass")
        val result = serviceWithMockApi.authenticate("http://base", credentials)

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(null, error.code)
        assertTrue(error.message.contains("Error de red"))
    }

    @Test
    fun `authenticate socket timeout exception returns error`() = runTest {
        val mockApi = mock<AuthApi>()
        val timeoutException = java.net.SocketTimeoutException("Timeout")
        whenever(mockApi.authenticate(any(), any())).thenAnswer { throw timeoutException }

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val credentials = UserCredentials("user", "pass")
        val result = serviceWithMockApi.authenticate("http://base", credentials)

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(null, error.code)
        assertTrue(error.message.contains("Error de red"))
    }

    @Test
    fun `authenticate http exception returns network error`() = runTest {
        val mockApi = mock<AuthApi>()
        val errorResponse = Response.error<LoginResponse>(500, "".toResponseBody("application/json".toMediaType()))
        val httpException = HttpException(errorResponse)
        whenever(mockApi.authenticate(any(), any())).thenThrow(httpException)

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val credentials = UserCredentials("user", "pass")
        val result = serviceWithMockApi.authenticate("http://base", credentials)

        assertTrue(result is AuthResult.Failure.NetworkError)
        val error = result as AuthResult.Failure.NetworkError
        assertEquals(httpException, error.http)
    }

    @Test
    fun `authenticate generic exception returns error`() = runTest {
        val mockApi = mock<AuthApi>()
        val unexpectedException = RuntimeException("Something bad happened")
        whenever(mockApi.authenticate(any(), any())).thenThrow(unexpectedException)

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val credentials = UserCredentials("user", "pass")
        val result = serviceWithMockApi.authenticate("http://base", credentials)

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(null, error.code)
        assertTrue(error.message.contains("Error"))
    }

    @Test
    fun `authenticate unsuccessful response returns error`() = runTest {
        val mockApi = mock<AuthApi>()
        val errorResponse = Response.error<LoginResponse>(401, "".toResponseBody("application/json".toMediaType()))
        whenever(mockApi.authenticate(any(), any())).thenReturn(errorResponse)

        val serviceWithMockApi = NetworkAuthService(mockApi, tokenStorage, Dispatchers.Unconfined)
        val credentials = UserCredentials("user", "pass")
        val result = serviceWithMockApi.authenticate("http://base", credentials)

        assertTrue(result is AuthResult.Error)
        val error = result as AuthResult.Error
        assertEquals(401, error.code)
    }

    @Test
    fun `parseErrorMessage returns raw body on JSONException`() {
        val method = NetworkAuthService::class.java.getDeclaredMethod("parseErrorMessage", String::class.java)
        method.isAccessible = true
        val result = method.invoke(service, "invalid json {") as String
        assertEquals("invalid json {", result)
    }
}
