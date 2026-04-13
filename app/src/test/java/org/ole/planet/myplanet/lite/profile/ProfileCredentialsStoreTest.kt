@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.profile

import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.util.EncryptedSharedPreferencesFactory

class ProfileCredentialsStoreTest {
    private lateinit var mockContext: Context
    private lateinit var mockAppContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var factoryMockStatic: MockedStatic<EncryptedSharedPreferencesFactory>

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        mockAppContext = mock(Context::class.java)
        mockPrefs = mock(SharedPreferences::class.java)
        whenever(mockContext.applicationContext).thenReturn(mockAppContext)
        factoryMockStatic = mockStatic(EncryptedSharedPreferencesFactory::class.java)
        factoryMockStatic.`when`<SharedPreferences> {
            EncryptedSharedPreferencesFactory.create(any<Context>(), any<String>())
        }.thenReturn(mockPrefs)
        ProfileCredentialsStore.setSessionCredentials(null)
    }

    @After
    fun teardown() {
        factoryMockStatic.close()
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
}
