/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
 */

package org.ole.planet.myplanet.lite.profile

/*
 * Represents the authenticated user profile that is cached locally after login.
 */
data class UserProfile(
    val username: String,
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val email: String?,
    val language: String?,
    val phoneNumber: String?,
    val birthDate: String?,
    val gender: String?,
    val level: String?,
    val avatarImage: ByteArray?,
    val revision: String?,
    val derivedKey: String?,
    val rawDocument: String?,
    val isUserAdmin: Boolean,
)
