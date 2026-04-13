@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

object EncryptedSharedPreferencesFactory {
    @JvmStatic
    fun create(context: Context, fileName: String): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return try {
            createInternal(appContext, fileName)
        } catch (e: Exception) {
            deleteCorruptedPrefs(appContext, fileName)
            try {
                createInternal(appContext, fileName)
            } catch (e2: Exception) {
                appContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            }
        }
    }

    private fun createInternal(appContext: Context, fileName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deleteCorruptedPrefs(appContext: Context, fileName: String) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (e: Exception) {}
        try {
            appContext.deleteSharedPreferences(fileName)
        } catch (e: Exception) {}
    }
}
