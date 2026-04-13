@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.AEADBadTagException

object SecurePreferencesProvider {
    @androidx.annotation.VisibleForTesting
    var injectedPreferences: SharedPreferences? = null

    private const val ENCRYPTED_PREFS_NAME = "encrypted_server_preferences"
    private const val LEGACY_PREFS_NAME = "server_preferences"

    @Suppress("unused")
    @Volatile
    private var instance: SharedPreferences? = null

    private val cachedInstances = mutableMapOf<String, SharedPreferences>()

    fun getServerPreferences(context: Context): SharedPreferences {
        injectedPreferences?.let { return it }

        return synchronized(this) {
            instance ?: getEncryptedPreferences(
                context = context,
                prefsName = ENCRYPTED_PREFS_NAME,
                onCreated = { appContext, encryptedPrefs ->
                    migrateLegacyPreferences(appContext, encryptedPrefs)
                }
            ).also { created ->
                instance = created
                cachedInstances[ENCRYPTED_PREFS_NAME] = created
            }
        }
    }

    fun getEncryptedPreferences(
        context: Context,
        prefsName: String
    ): SharedPreferences {
        return getEncryptedPreferences(
            context = context,
            prefsName = prefsName,
            onCreated = { _, _ -> }
        )
    }

    private fun getEncryptedPreferences(
        context: Context,
        prefsName: String,
        onCreated: (Context, SharedPreferences) -> Unit
    ): SharedPreferences {
        injectedPreferences?.let { return it }
        val appContext = context.applicationContext

        return synchronized(this) {
            cachedInstances[prefsName] ?: createEncryptedPreferences(
                appContext = appContext,
                prefsName = prefsName,
                onCreated = onCreated
            ).also { cachedInstances[prefsName] = it }
        }
    }

    private fun createEncryptedPreferences(
        appContext: Context,
        prefsName: String,
        onCreated: (Context, SharedPreferences) -> Unit
    ): SharedPreferences {
        val encryptedPrefs = runCatching {
            buildEncryptedPreferences(appContext, prefsName)
        }.getOrElse { error ->
            if (!shouldRecoverEncryptedPreferences(error)) {
                throw error
            }

            resetEncryptedStorage(prefsName)
            appContext.deleteSharedPreferences(prefsName)
            buildEncryptedPreferences(appContext, prefsName)
        }

        onCreated(appContext, encryptedPrefs)
        return encryptedPrefs
    }

    private fun buildEncryptedPreferences(appContext: Context, prefsName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext, masterKeyAlias(prefsName))
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun shouldRecoverEncryptedPreferences(error: Throwable): Boolean {
        return generateSequence(error) { it.cause }.any { cause ->
            cause is AEADBadTagException ||
                (cause.message?.contains("VERIFICATION_FAILED", ignoreCase = true) == true)
        }
    }

    private fun resetEncryptedStorage(prefsName: String) {
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(masterKeyAlias(prefsName))
        }
    }

    private fun masterKeyAlias(prefsName: String): String {
        val sanitizedName = prefsName
            .lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
        return "myplanet_lite_$sanitizedName"
    }

    @androidx.annotation.VisibleForTesting
    fun resetForTesting() {
        synchronized(this) {
            injectedPreferences = null
            instance = null
            cachedInstances.clear()
        }
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
        }
    }
}
