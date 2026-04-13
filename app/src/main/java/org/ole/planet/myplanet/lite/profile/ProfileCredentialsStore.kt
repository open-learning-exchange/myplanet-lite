@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.profile
import android.content.Context
import org.ole.planet.myplanet.lite.MyPlanetLite
import org.ole.planet.myplanet.lite.util.EncryptedSharedPreferencesFactory
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
        val securePrefs = EncryptedSharedPreferencesFactory.create(context.applicationContext, MyPlanetLite.SECURE_PREFS_NAME)
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
