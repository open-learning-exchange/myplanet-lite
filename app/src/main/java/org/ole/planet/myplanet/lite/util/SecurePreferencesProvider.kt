@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SecurePreferencesProvider {
    private val migrationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
            instance ?: run {
                val encryptedPrefs = getEncryptedPreferences(
                    context = context,
                    prefsName = ENCRYPTED_PREFS_NAME
                )
                val legacyFile = java.io.File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFS_NAME.xml")
                val created = if (legacyFile.exists()) {
                    val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                    val job = migrateLegacyPreferences(context, encryptedPrefs, legacyPrefs)
                    MigrationAwarePreferences(encryptedPrefs, legacyPrefs, job)
                } else {
                    encryptedPrefs
                }
                created.also {
                    instance = it
                    cachedInstances[ENCRYPTED_PREFS_NAME] = it
                }
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

    private fun migrateLegacyPreferences(
        context: Context,
        encryptedPrefs: SharedPreferences,
        legacyPrefs: SharedPreferences
    ): Job {
        return migrationScope.launch {
            val allLegacy = legacyPrefs.all
            if (allLegacy.isNotEmpty()) {
                val editor = encryptedPrefs.edit()
                for ((key, value) in allLegacy) {
                    if (encryptedPrefs.contains(key)) continue
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Long -> editor.putLong(key, value)
                        is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                    }
                }
                if (editor.commit()) {
                    context.deleteSharedPreferences(LEGACY_PREFS_NAME)
                }
            } else {
                context.deleteSharedPreferences(LEGACY_PREFS_NAME)
            }
        }
    }

    private class MigrationAwarePreferences(
        private val encryptedPrefs: SharedPreferences,
        private val legacyPrefs: SharedPreferences,
        private val migrationJob: Job
    ) : SharedPreferences {
        override fun getAll(): Map<String, *> {
            return if (migrationJob.isActive) {
                val combined = legacyPrefs.all.toMutableMap()
                combined.putAll(encryptedPrefs.all)
                combined
            } else {
                encryptedPrefs.all
            }
        }

        override fun getString(key: String?, defValue: String?): String? {
            return if (encryptedPrefs.contains(key)) {
                encryptedPrefs.getString(key, defValue)
            } else if (migrationJob.isActive) {
                legacyPrefs.getString(key, defValue)
            } else {
                defValue
            }
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            return if (encryptedPrefs.contains(key)) {
                encryptedPrefs.getStringSet(key, defValues)
            } else if (migrationJob.isActive) {
                legacyPrefs.getStringSet(key, defValues)
            } else {
                defValues
            }
        }

        override fun getInt(key: String?, defValue: Int): Int {
            return if (encryptedPrefs.contains(key)) {
                encryptedPrefs.getInt(key, defValue)
            } else if (migrationJob.isActive) {
                legacyPrefs.getInt(key, defValue)
            } else {
                defValue
            }
        }

        override fun getLong(key: String?, defValue: Long): Long {
            return if (encryptedPrefs.contains(key)) {
                encryptedPrefs.getLong(key, defValue)
            } else if (migrationJob.isActive) {
                legacyPrefs.getLong(key, defValue)
            } else {
                defValue
            }
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            return if (encryptedPrefs.contains(key)) {
                encryptedPrefs.getFloat(key, defValue)
            } else if (migrationJob.isActive) {
                legacyPrefs.getFloat(key, defValue)
            } else {
                defValue
            }
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return if (encryptedPrefs.contains(key)) {
                encryptedPrefs.getBoolean(key, defValue)
            } else if (migrationJob.isActive) {
                legacyPrefs.getBoolean(key, defValue)
            } else {
                defValue
            }
        }

        override fun contains(key: String?): Boolean {
            return encryptedPrefs.contains(key) || (migrationJob.isActive && legacyPrefs.contains(key))
        }

        override fun edit(): SharedPreferences.Editor = encryptedPrefs.edit()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            encryptedPrefs.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            encryptedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}
