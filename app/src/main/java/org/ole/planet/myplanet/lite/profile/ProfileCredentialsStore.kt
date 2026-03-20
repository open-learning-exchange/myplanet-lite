@file:Suppress("DEPRECATION")
/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
 */

package org.ole.planet.myplanet.lite.profile

import android.content.Context
import org.ole.planet.myplanet.lite.MyPlanetLite

/**
 * Reads the credentials that were persisted after login so the profile screen can refresh data.
 */
object ProfileCredentialsStore {
    private const val KEY_REMEMBERED_USERNAME = "remembered_username"
    private const val KEY_REMEMBERED_PASSWORD = "remembered_password"
    @Volatile
    private var sessionCredentials: StoredCredentials? = null

    fun setSessionCredentials(credentials: StoredCredentials?) {
        sessionCredentials = credentials
    }

    fun getStoredCredentials(context: Context): StoredCredentials? {
        sessionCredentials?.let { return it }

        val appContext = context.applicationContext
        val masterKey = androidx.security.crypto.MasterKey.Builder(appContext)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        val securePrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
            appContext,
            MyPlanetLite.SECURE_PREFS_NAME,
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val username = securePrefs.getString(KEY_REMEMBERED_USERNAME, null)?.takeIf { it.isNotBlank() }
        val password = securePrefs.getString(KEY_REMEMBERED_PASSWORD, null)?.takeIf { it.isNotBlank() }
        return if (username != null && password != null) {
            StoredCredentials(username, password)
        } else {
            null
        }
    }
}

data class StoredCredentials(val username: String, val password: String)
