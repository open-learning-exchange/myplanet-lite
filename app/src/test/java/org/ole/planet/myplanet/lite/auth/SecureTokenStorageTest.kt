package org.ole.planet.myplanet.lite.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecureTokenStorageTest {

    private lateinit var context: Context
    private lateinit var secureTokenStorage: SecureTokenStorage
    private lateinit var mockKeyStore: KeyStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        mockkStatic(KeyStore::class)
        mockKeyStore = mockk(relaxed = true)
        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore

        mockkStatic(KeyGenerator::class)
        val mockKeyGenerator = mockk<KeyGenerator>(relaxed = true)
        every { KeyGenerator.getInstance(any(), any<String>()) } returns mockKeyGenerator
        val mockSecretKey = mockk<SecretKey>(relaxed = true)
        every { mockKeyGenerator.generateKey() } returns mockSecretKey

        mockkStatic(Cipher::class)
        val mockCipher = mockk<Cipher>(relaxed = true)
        every { Cipher.getInstance(any()) } returns mockCipher
        every { mockCipher.iv } returns ByteArray(12) { 0 }

        every { mockCipher.doFinal(any()) } answers {
            val input = firstArg<ByteArray>()
            // Using a simple deterministic encryption mock: just prepend a string.
            // But we must support decryption too!
            // When decrypting, SecureTokenStorage calls doFinal with ciphertext (without IV and Size which were stripped in decryptToken)
            // The input for encryption is plain bytes.
            // Let's check the size: if it's the exact same array we generated during mock encryption, we strip.
            // If the input starts with our "encrypted_" prefix, we strip it. Otherwise, we prepend.
            val prefix = "encrypted_".toByteArray()
            if (input.size > prefix.size && input.sliceArray(0 until prefix.size).contentEquals(prefix)) {
                // Decrypt
                val result = ByteArray(input.size - prefix.size)
                System.arraycopy(input, prefix.size, result, 0, result.size)
                result
            } else {
                // Encrypt
                prefix + input
            }
        }

        secureTokenStorage = SecureTokenStorage(context, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getToken returns null initially`() = runTest {
        val token = secureTokenStorage.getToken()
        assertNull(token)
    }

    @Test
    fun `saveToken encrypts and stores token successfully`() = runTest {
        val testToken = "test_token_123"
        secureTokenStorage.saveToken(testToken)

        // Read raw shared prefs to ensure it is stored as encrypted payload
        val prefs = context.getSharedPreferences("auth_secure_prefs", Context.MODE_PRIVATE)
        val storedValue = prefs.getString("planet_token", null)
        assert(storedValue != null)

        // Retrieve token
        val retrievedToken = secureTokenStorage.getToken()
        assertEquals(testToken, retrievedToken)
    }

    @Test
    fun `clearToken removes token`() = runTest {
        val testToken = "test_token_123"
        secureTokenStorage.saveToken(testToken)

        val prefs = context.getSharedPreferences("auth_secure_prefs", Context.MODE_PRIVATE)
        assert(prefs.getString("planet_token", null) != null)

        secureTokenStorage.clearToken()

        assertNull(prefs.getString("planet_token", null))
        assertNull(secureTokenStorage.getToken())
    }

    @Test
    fun `saveToken overwrites previous token`() = runTest {
        val testToken1 = "test_token_123"
        val testToken2 = "test_token_456"

        secureTokenStorage.saveToken(testToken1)
        secureTokenStorage.saveToken(testToken2)

        val retrievedToken = secureTokenStorage.getToken()
        assertEquals(testToken2, retrievedToken)
    }
}
