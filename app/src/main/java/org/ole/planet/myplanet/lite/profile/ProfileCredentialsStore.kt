@file:Suppress("DEPRECATION")
/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
 */

package org.ole.planet.myplanet.lite.profile

import android.content.Context
import java.security.MessageDigest
import org.ole.planet.myplanet.lite.MyPlanetLite
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

/**
 * Reads the credentials that were persisted after login so the profile screen can refresh data.
 */

object ProfileCredentialsStore {
    fun getPasswordKey(username: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("pw_$username".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    private const val KEY_REMEMBERED_USERNAME = "remembered_username"
    @Volatile
    private var sessionCredentials: StoredCredentials? = null
    @Volatile
    private var tempSignUpPassword: String? = null

    fun setSessionCredentials(credentials: StoredCredentials?) {
        sessionCredentials = credentials
    }

    fun getStoredCredentials(context: Context): StoredCredentials? {
        sessionCredentials?.let { return it }

        val appContext = context.applicationContext
        val securePrefs = SecurePreferencesProvider.getEncryptedPreferences(
            appContext,
            MyPlanetLite.SECURE_PREFS_NAME
        )

        val username = securePrefs.getString(KEY_REMEMBERED_USERNAME, null)?.takeIf { it.isNotBlank() }

        var password: String? = null
        if (username != null) {
            val dynamicKey = getPasswordKey(username)
            password = securePrefs.getString(dynamicKey, null)?.takeIf { it.isNotBlank() }

            // Migration
            if (password == null) {
                val legacyPassword = securePrefs.getString("remembered_password", null)?.takeIf { it.isNotBlank() }
                if (legacyPassword != null) {
                    password = legacyPassword
                    securePrefs.edit()
                        .putString(dynamicKey, legacyPassword)
                        .remove("remembered_password")
                        .apply()
                }
            }
        }
        return if (username != null && password != null) {
            StoredCredentials(username, password)
        } else {
            null
        }
    }

    fun saveTemporarySignUpPassword(context: Context, password: String) {
        tempSignUpPassword = password
    }

    fun consumeTemporarySignUpPassword(context: Context): String? {
        val pwd = tempSignUpPassword
        tempSignUpPassword = null
        return pwd
    }
}

data class StoredCredentials(val username: String, val password: String)
