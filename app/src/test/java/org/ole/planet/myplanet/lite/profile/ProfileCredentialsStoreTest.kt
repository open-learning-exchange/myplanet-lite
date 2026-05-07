@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.profile

import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

class ProfileCredentialsStoreTest {
    private lateinit var mockContext: Context
    private lateinit var mockAppContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        mockContext = mock()
        mockAppContext = mock()
        mockPrefs = mock()
        mockEditor = mock()

        whenever(mockContext.applicationContext).thenReturn(mockAppContext)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.remove(any())).thenReturn(mockEditor)
        SecurePreferencesProvider.resetForTesting()
        SecurePreferencesProvider.injectedPreferences = mockPrefs

        // Reset singleton state before each test
        ProfileCredentialsStore.setSessionCredentials(null)
    }

    @After
    fun teardown() {
        SecurePreferencesProvider.resetForTesting()
        ProfileCredentialsStore.setSessionCredentials(null)
    }

    @Test
    fun `test getStoredCredentials returns memory credentials when set`() {
        val creds = StoredCredentials("testUser", "testPass")
        ProfileCredentialsStore.setSessionCredentials(creds)

        val result = ProfileCredentialsStore.getStoredCredentials(mockContext)

        assertEquals("testUser", result?.username)
        assertEquals("testPass", result?.password)
    }

    @Test
    fun `test getPasswordKey generates correct SHA-256 hash`() {
        val expectedHash = "70cfbf5a0d1834b3577863bd449f2684d79adae9c658423167a192ad6ddbe272" // for "pw_testuser"
        val key = ProfileCredentialsStore.getPasswordKey("testuser")
        assertEquals(expectedHash, key)
    }

    @Test
    fun `test getStoredCredentials returns from SharedPreferences using dynamic key`() {
        val dynamicKey = ProfileCredentialsStore.getPasswordKey("prefUser")
        whenever(mockPrefs.getString(eq("remembered_username"), eq(null))).thenReturn("prefUser")
        whenever(mockPrefs.getString(eq(dynamicKey), eq(null))).thenReturn("prefPass")

        val result = ProfileCredentialsStore.getStoredCredentials(mockContext)

        assertEquals("prefUser", result?.username)
        assertEquals("prefPass", result?.password)
    }

    @Test
    fun `test getStoredCredentials migrates password from legacy key`() {
        val dynamicKey = ProfileCredentialsStore.getPasswordKey("prefUser")
        whenever(mockPrefs.getString(eq("remembered_username"), eq(null))).thenReturn("prefUser")
        whenever(mockPrefs.getString(eq(dynamicKey), eq(null))).thenReturn(null)
        whenever(mockPrefs.getString(eq("remembered_password"), eq(null))).thenReturn("legacyPass")

        val result = ProfileCredentialsStore.getStoredCredentials(mockContext)

        assertEquals("prefUser", result?.username)
        assertEquals("legacyPass", result?.password)
        verify(mockEditor).putString(eq(dynamicKey), eq("legacyPass"))
        verify(mockEditor).remove(eq("remembered_password"))
    }

    @Test
    fun `test getStoredCredentials returns null when SharedPreferences username is missing`() {
        whenever(mockPrefs.getString(eq("remembered_username"), eq(null))).thenReturn(null)

        val result = ProfileCredentialsStore.getStoredCredentials(mockContext)

        assertNull(result)
    }

    @Test
    fun `test getStoredCredentials returns null when SharedPreferences password is missing`() {
        val dynamicKey = ProfileCredentialsStore.getPasswordKey("prefUser")
        whenever(mockPrefs.getString(eq("remembered_username"), eq(null))).thenReturn("prefUser")
        whenever(mockPrefs.getString(eq(dynamicKey), eq(null))).thenReturn(null)
        whenever(mockPrefs.getString(eq("remembered_password"), eq(null))).thenReturn(null)

        val result = ProfileCredentialsStore.getStoredCredentials(mockContext)

        assertNull(result)
    }

    @Test
    fun `test getStoredCredentials returns null when SharedPreferences values are blank`() {
        val dynamicKey = ProfileCredentialsStore.getPasswordKey("   ")
        whenever(mockPrefs.getString(eq("remembered_username"), eq(null))).thenReturn("   ")
        whenever(mockPrefs.getString(eq(dynamicKey), eq(null))).thenReturn("")
        whenever(mockPrefs.getString(eq("remembered_password"), eq(null))).thenReturn("")

        val result = ProfileCredentialsStore.getStoredCredentials(mockContext)

        assertNull(result)
    }

    @Test
    fun `test saveTemporarySignUpPassword stores password in memory correctly`() {
        val password = "tempPassword123"

        ProfileCredentialsStore.saveTemporarySignUpPassword(mockContext, password)

        // Memory should not touch prefs
        verify(mockPrefs, never()).edit()

        val result = ProfileCredentialsStore.consumeTemporarySignUpPassword(mockContext)
        assertEquals(password, result)
    }

    @Test
    fun `test consumeTemporarySignUpPassword returns value and clears memory when present`() {
        val password = "tempPassword123"
        ProfileCredentialsStore.saveTemporarySignUpPassword(mockContext, password)

        val result1 = ProfileCredentialsStore.consumeTemporarySignUpPassword(mockContext)
        val result2 = ProfileCredentialsStore.consumeTemporarySignUpPassword(mockContext)

        assertEquals(password, result1)
        assertNull(result2)
    }

    @Test
    fun `test consumeTemporarySignUpPassword returns null when memory is empty`() {
        val result = ProfileCredentialsStore.consumeTemporarySignUpPassword(mockContext)

        assertNull(result)
    }
}
