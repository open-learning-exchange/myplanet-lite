/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
 */
package org.ole.planet.myplanet.lite.auth
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.text.Charsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
interface TokenStorage {
    suspend fun saveToken(token: String)
    suspend fun getToken(): String?
    suspend fun clearToken()
}
class SecureTokenStorage @JvmOverloads constructor(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : TokenStorage {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }
    private val sharedPreferences =
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
    override suspend fun saveToken(token: String) {
        withContext(dispatcher) {
            val encoded = encryptToken(token) ?: return@withContext
            sharedPreferences.edit().putString(KEY_TOKEN, encoded).apply()
        }
    }
    override suspend fun getToken(): String? = withContext(dispatcher) {
        val stored = sharedPreferences.getString(KEY_TOKEN, null) ?: return@withContext null
        decryptToken(stored)
    }
    override suspend fun clearToken() {
        withContext(dispatcher) {
            sharedPreferences.edit().remove(KEY_TOKEN).apply()
        }
    }
    private fun encryptToken(token: String): String? = runCatching {
        val cipher = Cipher.getInstance(AES_MODE).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(Int.SIZE_BYTES + iv.size + encrypted.size)
            .putInt(iv.size)
            .put(iv)
            .put(encrypted)
            .array()
        Base64.encodeToString(payload, Base64.NO_WRAP)
    }.getOrNull()
    private fun decryptToken(payload: String): String? = runCatching {
        val decoded = Base64.decode(payload, Base64.NO_WRAP)
        if (decoded.size <= Int.SIZE_BYTES) return@runCatching null
        val buffer = ByteBuffer.wrap(decoded)
        val ivSize = buffer.int
        if (ivSize <= 0 || ivSize > buffer.remaining()) return@runCatching null
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance(AES_MODE).apply {
            init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )
        }
        val plainBytes = cipher.doFinal(cipherText)
        String(plainBytes, Charsets.UTF_8)
    }.getOrNull()
    private fun getOrCreateSecretKey(): SecretKey {
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }
    private companion object {
        const val PREF_FILE_NAME = "auth_secure_prefs"
        const val KEY_TOKEN = "planet_token"
        const val KEY_ALIAS = "planet_token_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_MODE = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
    }
}
