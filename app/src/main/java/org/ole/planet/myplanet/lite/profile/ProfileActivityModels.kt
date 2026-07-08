/*
 * Author: Walfre Lopez Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-08
 */

package org.ole.planet.myplanet.lite.profile

internal data class ProfileFormValues(
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val email: String?,
    val phoneNumber: String?,
    val birthDateIso: String?,
    val birthYear: String?,
    val age: String?,
    val gender: String?,
    val language: String?,
    val level: String?,
)

internal data class ProfileUpdateContext(
    val storedProfile: UserProfile?,
    val normalizedBase: String,
    val username: String,
    val sessionCookie: String,
)
