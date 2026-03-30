/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-07
 */
package org.ole.planet.myplanet.lite.auth

data class LoginRequest(
    val name: String,
    val password: String
)
data class LoginResponse(
    val ok: Boolean,
    val name: String? = null,
    val roles: List<String>? = null,
    val sessionCookie: String? = null
)
