@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecureTokenStorageTest {
    private lateinit var context: Context
    private lateinit var secureTokenStorage: SecureTokenStorage
    private lateinit var fakeSharedPreferences: SharedPreferences
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeSharedPreferences = context.getSharedPreferences("fake_prefs", Context.MODE_PRIVATE)
        mockkConstructor(androidx.security.crypto.MasterKey.Builder::class)
        every { anyConstructed<androidx.security.crypto.MasterKey.Builder>().setKeyScheme(any()) } answers { callOriginal() }
        every { anyConstructed<androidx.security.crypto.MasterKey.Builder>().build() } returns mockk<androidx.security.crypto.MasterKey>(relaxed = true)
        mockkStatic(androidx.security.crypto.EncryptedSharedPreferences::class)
        every {
            androidx.security.crypto.EncryptedSharedPreferences.create(
                any<Context>(),
                any<String>(),
                any<androidx.security.crypto.MasterKey>(),
                any<androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
                any<androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme>()
            )
        } returns fakeSharedPreferences
        secureTokenStorage = SecureTokenStorage(context, Dispatchers.Unconfined)
    }
    @After
    fun tearDown() {
        fakeSharedPreferences.edit().clear().apply()
        unmockkAll()
    }
    @Test
    fun `getToken returns null initially`() = runTest {
        val token = secureTokenStorage.getToken()
        assertNull(token)
    }
    @Test
    fun `saveToken stores token successfully`() = runTest {
        val testToken = "test_token_123"
        secureTokenStorage.saveToken(testToken)
        val retrievedToken = secureTokenStorage.getToken()
        assertEquals(testToken, retrievedToken)
        assertEquals(testToken, fakeSharedPreferences.getString("planet_token", null))
    }
    @Test
    fun `clearToken removes token`() = runTest {
        val testToken = "test_token_123"
        secureTokenStorage.saveToken(testToken)
        assertEquals(testToken, fakeSharedPreferences.getString("planet_token", null))
        secureTokenStorage.clearToken()
        assertNull(fakeSharedPreferences.getString("planet_token", null))
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
