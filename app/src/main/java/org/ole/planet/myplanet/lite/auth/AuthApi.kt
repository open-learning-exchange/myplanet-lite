/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-07
 */
package org.ole.planet.myplanet.lite.auth
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
interface AuthApi {
    @POST("db/_session")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}
