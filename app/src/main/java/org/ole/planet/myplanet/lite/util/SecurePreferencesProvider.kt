@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePreferencesProvider {
    @androidx.annotation.VisibleForTesting
    var injectedPreferences: SharedPreferences? = null

    private const val ENCRYPTED_PREFS_NAME = "encrypted_server_preferences"
    private const val LEGACY_PREFS_NAME = "server_preferences"

    @Volatile
    private var instance: SharedPreferences? = null

    fun getServerPreferences(context: Context): SharedPreferences {
        injectedPreferences?.let { return it }

        return instance ?: synchronized(this) {
            instance ?: createEncryptedPreferences(context.applicationContext).also { instance = it }
        }
    }

    private fun createEncryptedPreferences(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedPrefs = EncryptedSharedPreferences.create(
            appContext,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        migrateLegacyPreferences(appContext, encryptedPrefs)
        return encryptedPrefs
    }

    private fun migrateLegacyPreferences(context: Context, encryptedPrefs: SharedPreferences) {
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val allLegacy = legacyPrefs.all
        if (allLegacy.isNotEmpty()) {
            val editor = encryptedPrefs.edit()
            for ((key, value) in allLegacy) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                }
            }
            editor.apply()
            legacyPrefs.edit().clear().apply()
            context.deleteSharedPreferences(LEGACY_PREFS_NAME)
        }
    }
}
