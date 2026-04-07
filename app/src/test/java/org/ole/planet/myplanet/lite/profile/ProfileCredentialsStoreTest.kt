@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class ProfileCredentialsStoreTest {
    private lateinit var mockContext: Context
    private lateinit var mockAppContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var encryptedSharedPreferencesMockStatic: MockedStatic<EncryptedSharedPreferences>
    private lateinit var masterKeyBuilderMockedConstruction: MockedConstruction<MasterKey.Builder>

    @Before
    fun setup() {
        mockContext = mock()
        mockAppContext = mock()
        mockPrefs = mock()

        whenever(mockContext.applicationContext).thenReturn(mockAppContext)

        // Mock MasterKey Builder via mockConstruction since we invoke the constructor
        masterKeyBuilderMockedConstruction = mockConstruction(MasterKey.Builder::class.java) { mock, _ ->
            whenever(mock.setKeyScheme(any())).thenReturn(mock)
            whenever(mock.build()).thenReturn(mock(MasterKey::class.java))
        }

        // Mock EncryptedSharedPreferences
        encryptedSharedPreferencesMockStatic = mockStatic(EncryptedSharedPreferences::class.java)
        encryptedSharedPreferencesMockStatic.`when`<SharedPreferences> {
            EncryptedSharedPreferences.create(
                eq(mockAppContext),
                any(),
                any(),
                any(),
                any()
            )
        }.thenReturn(mockPrefs)

        // Reset singleton state before each test
        ProfileCredentialsStore.setSessionCredentials(null)
    }

    @After
    fun teardown() {
        masterKeyBuilderMockedConstruction.close()
        encryptedSharedPreferencesMockStatic.close()
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
