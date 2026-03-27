/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-07
 */
package org.ole.planet.myplanet.lite.auth

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException

sealed class AuthResult {
    data class Success(val response: LoginResponse) : AuthResult()
    data class Error(val code: Int?, val message: String) : AuthResult()
}
interface AuthService {
    suspend fun login(usernameOrEmail: String, password: String): AuthResult
    suspend fun logout()
    suspend fun getStoredToken(): String?
}
class NetworkAuthService(
    private val api: AuthApi,
    private val tokenStorage: TokenStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuthService {
    override suspend fun login(usernameOrEmail: String, password: String): AuthResult {
        return try {
            val response = withContext(ioDispatcher) {
                api.login(LoginRequest(name = usernameOrEmail, password = password))
            }
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.ok == true) {
                    val sessionCookie = response.headers()["Set-Cookie"]
                    if (!sessionCookie.isNullOrBlank()) {
                        tokenStorage.saveToken(sessionCookie)
                    } else {
                        tokenStorage.clearToken()
                    }
                    AuthResult.Success(body.copy(sessionCookie = sessionCookie))
                } else {
                    val message = if (body == null) {
                        "Respuesta inválida del servidor"
                    } else {
                        "El servidor rechazó el inicio de sesión"
                    }
                    AuthResult.Error(response.code(), message)
                }
            } else {
                val errorMessage = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    ?.let { parseErrorMessage(it) }
                    ?: "Error ${response.code()}"
                AuthResult.Error(response.code(), errorMessage)
            }
        } catch (http: HttpException) {
            AuthResult.Error(http.code(), http.message())
        } catch (io: IOException) {
            AuthResult.Error(null, "Error de red: ${io.localizedMessage ?: io.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Error(null, "Error inesperado: ${e.localizedMessage ?: e.message}")
        }
    }
    override suspend fun logout() {
        tokenStorage.clearToken()
    }
    override suspend fun getStoredToken(): String? = tokenStorage.getToken()
    private fun parseErrorMessage(rawBody: String): String {
        return try {
            val json = JSONObject(rawBody)
            when {
                json.has("message") -> json.getString("message")
                json.has("error") -> json.getString("error")
                json.has("detail") -> json.getString("detail")
                else -> rawBody
            }
        } catch (e: JSONException) {
            rawBody
        }
    }
}
