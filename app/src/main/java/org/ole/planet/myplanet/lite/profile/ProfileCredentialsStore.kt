@file:Suppress("DEPRECATION")
/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
 */

package org.ole.planet.myplanet.lite.profile

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Reads the credentials that were persisted after login so the profile screen can refresh data.
 */
object ProfileCredentialsStore {
    private const val KEY_REMEMBER_CREDENTIALS = "remember_credentials"
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
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val securePrefs = EncryptedSharedPreferences.create(
            appContext,
            "secure_server_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
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
