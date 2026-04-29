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
    fun `test getStoredCredentials returns from SharedPreferences when memory is empty`() {
        whenever(mockPrefs.getString(eq("remembered_username"), eq(null))).thenReturn("prefUser")
        whenever(mockPrefs.getString(eq("remembered_password"), eq(null))).thenReturn("prefPass")

        val result = ProfileCredentialsStore.getStoredCredentials(mockContext)

        assertEquals("prefUser", result?.username)
        assertEquals("prefPass", result?.password)
    }

    @Test
    fun `test getStoredCredentials returns null when SharedPreferences username is missing`() {
        whenever(mockPrefs.getString(eq("remembered_username"), eq(null))).thenReturn(null)
        whenever(mockPrefs.getString(eq("remembered_password"), eq(null))).thenReturn("prefPass")

        val result = ProfileCredentialsStore.getStoredCredentials(mockContext)

        assertNull(result)
    }

    @Test
    fun `test getStoredCredentials returns null when SharedPreferences password is missing`() {
        whenever(mockPrefs.getString(eq("remembered_username"), eq(null))).thenReturn("prefUser")
        whenever(mockPrefs.getString(eq("remembered_password"), eq(null))).thenReturn(null)

        val result = ProfileCredentialsStore.getStoredCredentials(mockContext)

        assertNull(result)
    }

    @Test
    fun `test getStoredCredentials returns null when SharedPreferences values are blank`() {
        whenever(mockPrefs.getString(eq("remembered_username"), eq(null))).thenReturn("   ")
        whenever(mockPrefs.getString(eq("remembered_password"), eq(null))).thenReturn("")

        val result = ProfileCredentialsStore.getStoredCredentials(mockContext)

        assertNull(result)
    }

    @Test
    fun `test saveTemporarySignUpPassword stores password correctly`() {
        val password = "tempPassword123"

        ProfileCredentialsStore.saveTemporarySignUpPassword(mockContext, password)

        verify(mockPrefs).edit()
        verify(mockEditor).putString(eq("temp_signup_password"), eq(password))
        verify(mockEditor).apply()
    }

    @Test
    fun `test consumeTemporarySignUpPassword returns value and clears when present`() {
        val password = "tempPassword123"
        whenever(mockPrefs.getString(eq("temp_signup_password"), eq(null))).thenReturn(password)

        val result = ProfileCredentialsStore.consumeTemporarySignUpPassword(mockContext)

        assertEquals(password, result)
        verify(mockPrefs).edit()
        verify(mockEditor).remove(eq("temp_signup_password"))
        verify(mockEditor).apply()
    }

    @Test
    fun `test consumeTemporarySignUpPassword returns null and does not clear when absent`() {
        whenever(mockPrefs.getString(eq("temp_signup_password"), eq(null))).thenReturn(null)

        val result = ProfileCredentialsStore.consumeTemporarySignUpPassword(mockContext)

        assertNull(result)
        verify(mockPrefs, never()).edit()
    }
}
