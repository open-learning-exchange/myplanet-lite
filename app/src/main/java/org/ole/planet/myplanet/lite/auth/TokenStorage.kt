@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.util.EncryptedSharedPreferencesFactory

interface TokenStorage {
    suspend fun saveToken(token: String)
    suspend fun getToken(): String?
    suspend fun clearToken()
}

class SecureTokenStorage @JvmOverloads constructor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : TokenStorage {
    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferencesFactory.create(context.applicationContext, PREF_FILE_NAME)
    }

    override suspend fun saveToken(token: String) {
        withContext(dispatcher) {
            sharedPreferences.edit().putString(KEY_TOKEN, token).apply()
        }
    }

    override suspend fun getToken(): String? = withContext(dispatcher) {
        sharedPreferences.getString(KEY_TOKEN, null)
    }

    override suspend fun clearToken() {
        withContext(dispatcher) {
            sharedPreferences.edit().remove(KEY_TOKEN).apply()
        }
    }

    private companion object {
        const val PREF_FILE_NAME = "auth_secure_prefs"
        const val KEY_TOKEN = "planet_token"
    }
}
