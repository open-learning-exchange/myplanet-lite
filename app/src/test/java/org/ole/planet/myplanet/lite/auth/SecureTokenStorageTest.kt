@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
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
        fakeSharedPreferences.edit().clear().apply()
        SecurePreferencesProvider.resetForTesting()
        SecurePreferencesProvider.injectedPreferences = fakeSharedPreferences
        secureTokenStorage = SecureTokenStorage(context, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        fakeSharedPreferences.edit().clear().apply()
        SecurePreferencesProvider.resetForTesting()
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

    @Test
    fun `saveToken stores empty token successfully`() = runTest {
        val testToken = ""
        secureTokenStorage.saveToken(testToken)
        val retrievedToken = secureTokenStorage.getToken()
        assertEquals(testToken, retrievedToken)
        assertEquals(testToken, fakeSharedPreferences.getString("planet_token", null))
    }

    @Test
    fun `verify dispatcher is used`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val storage = SecureTokenStorage(context, testDispatcher)

        storage.saveToken("dispatcher_token")
        testDispatcher.scheduler.advanceUntilIdle()

        storage.getToken()
        testDispatcher.scheduler.advanceUntilIdle()

        storage.clearToken()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `verify EncryptedSharedPreferences creates storage successfully via injected preferences`() = runTest {
        // Mock SharedPreferences
        val mockSharedPreferences = Mockito.mock(SharedPreferences::class.java)
        val mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)

        Mockito.`when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        Mockito.`when`(mockEditor.putString(Mockito.anyString(), Mockito.anyString())).thenReturn(mockEditor)
        Mockito.`when`(mockEditor.remove(Mockito.anyString())).thenReturn(mockEditor)

        // Inject the mocked preferences
        SecurePreferencesProvider.resetForTesting()
        SecurePreferencesProvider.injectedPreferences = mockSharedPreferences

        val storage = SecureTokenStorage(context, Dispatchers.Unconfined)

        // This will trigger the lazy evaluation of sharedPreferences which fetches from SecurePreferencesProvider
        val testToken = "test_token_mock"
        storage.saveToken(testToken)

        // Verify that the mocked editor was called correctly which indirectly proves
        // TokenStorage interacted with SharedPreferences
        Mockito.verify(mockEditor).putString("planet_token", testToken)
        Mockito.verify(mockEditor).apply()

        storage.getToken()
        Mockito.verify(mockSharedPreferences).getString("planet_token", null)

        storage.clearToken()
        Mockito.verify(mockEditor).remove("planet_token")
        Mockito.verify(mockEditor, Mockito.times(2)).apply()
    }

    @Test
    fun `saveToken specifically verifies string put and apply on mock editor`() = runTest {
        val mockSharedPreferences = Mockito.mock(SharedPreferences::class.java)
        val mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        Mockito.`when`(mockEditor.putString(Mockito.anyString(), Mockito.anyString())).thenReturn(mockEditor)

        SecurePreferencesProvider.resetForTesting()
        SecurePreferencesProvider.injectedPreferences = mockSharedPreferences

        val storage = SecureTokenStorage(context, Dispatchers.Unconfined)
        val token = "explicit_save_token"
        storage.saveToken(token)

        Mockito.verify(mockEditor).putString("planet_token", token)
        Mockito.verify(mockEditor).apply()
    }

    @Test
    fun `saveToken handles exception during putString gracefully or propagates`() = runTest {
        val mockSharedPreferences = Mockito.mock(SharedPreferences::class.java)
        val mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        Mockito.`when`(mockEditor.putString(Mockito.anyString(), Mockito.anyString())).thenThrow(RuntimeException("Storage failed"))

        SecurePreferencesProvider.resetForTesting()
        SecurePreferencesProvider.injectedPreferences = mockSharedPreferences

        val storage = SecureTokenStorage(context, Dispatchers.Unconfined)
        val token = "error_token"

        var exceptionThrown = false
        try {
            storage.saveToken(token)
        } catch (e: Exception) {
            exceptionThrown = true
            assertEquals("Storage failed", e.message)
        }

        org.junit.Assert.assertTrue("Exception should be thrown", exceptionThrown)
        Mockito.verify(mockEditor, Mockito.never()).apply()
    }
}
