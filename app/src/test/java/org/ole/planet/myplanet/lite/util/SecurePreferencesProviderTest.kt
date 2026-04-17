@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.never

class SecurePreferencesProviderTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        `when`(mockContext.applicationContext).thenReturn(mockContext)
        val mockAppInfo = mock(ApplicationInfo::class.java)
        mockAppInfo.dataDir = "/tmp"
        `when`(mockContext.applicationInfo).thenReturn(mockAppInfo)
        mockPrefs = mock(SharedPreferences::class.java)
        SecurePreferencesProvider.resetForTesting()
    }

    @After
    fun teardown() {
        SecurePreferencesProvider.resetForTesting()
    }

    @Test
    fun `getServerPreferences returns injectedPreferences when set`() {
        SecurePreferencesProvider.injectedPreferences = mockPrefs
        val result = SecurePreferencesProvider.getServerPreferences(mockContext)
        assertEquals(mockPrefs, result)
    }

    @Test
    fun `getServerPreferences returns instance when already created`() {
        SecurePreferencesProvider.injectedPreferences = mockPrefs

        val result = SecurePreferencesProvider.getServerPreferences(mockContext)
        assertEquals(mockPrefs, result)
    }

    @Test
    fun `getServerPreferences creates EncryptedSharedPreferences when instance is null`() {
        val legacyPrefs = mock(SharedPreferences::class.java)
        `when`(mockContext.getSharedPreferences("server_preferences", Context.MODE_PRIVATE)).thenReturn(legacyPrefs)
        `when`(legacyPrefs.all).thenReturn(emptyMap<String, Any?>())

        val mockMasterKey = mock(MasterKey::class.java)

        val encryptedPrefsStatic = mockStatic(EncryptedSharedPreferences::class.java)

        try {
            encryptedPrefsStatic.`when`<SharedPreferences> {
                EncryptedSharedPreferences.create(
                    eq(mockContext),
                    eq("encrypted_server_preferences"),
                    any(),
                    any(),
                    any()
                )
            }.thenReturn(mockPrefs)

            mockConstruction(MasterKey.Builder::class.java) { mock, context ->
                `when`(mock.setKeyScheme(any())).thenReturn(mock)
                `when`(mock.build()).thenReturn(mockMasterKey)
            }.use { builderMock ->
                val result = SecurePreferencesProvider.getServerPreferences(mockContext)
                assertEquals(mockPrefs, result)
            }
        } finally {
            encryptedPrefsStatic.close()
        }
    }

    @Test
    fun `migrateLegacyPreferences migrates data and deletes legacy prefs`() = runBlocking {
        val mockLegacyPrefs = mock(SharedPreferences::class.java)
        val mockEncryptedPrefs = mock(SharedPreferences::class.java)
        val mockEncryptedEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences("server_preferences", Context.MODE_PRIVATE))
            .thenReturn(mockLegacyPrefs)

        val legacyData = mapOf<String, Any?>(
            "string_key" to "value",
            "int_key" to 42,
            "bool_key" to true,
            "float_key" to 3.14f,
            "long_key" to 100L,
            "set_key" to setOf("a", "b")
        )

        `when`(mockLegacyPrefs.all).thenReturn(legacyData)
        `when`(mockEncryptedPrefs.edit()).thenReturn(mockEncryptedEditor)
        `when`(mockEncryptedEditor.commit()).thenReturn(true)

        val method = SecurePreferencesProvider::class.java.getDeclaredMethod(
            "migrateLegacyPreferences",
            Context::class.java,
            SharedPreferences::class.java,
            SharedPreferences::class.java
        )
        method.isAccessible = true
        val job = method.invoke(SecurePreferencesProvider, mockContext, mockEncryptedPrefs, mockLegacyPrefs) as Job
        job.join()

        verify(mockEncryptedEditor).putString("string_key", "value")
        verify(mockEncryptedEditor).putInt("int_key", 42)
        verify(mockEncryptedEditor).putBoolean("bool_key", true)
        verify(mockEncryptedEditor).putFloat("float_key", 3.14f)
        verify(mockEncryptedEditor).putLong("long_key", 100L)
        verify(mockEncryptedEditor).putStringSet("set_key", setOf("a", "b"))
        verify(mockEncryptedEditor).commit()

        verify(mockContext).deleteSharedPreferences("server_preferences")
    }

    @Test
    fun `migrateLegacyPreferences does nothing when legacy prefs are empty`() = runBlocking {
        val mockLegacyPrefs = mock(SharedPreferences::class.java)
        val mockEncryptedPrefs = mock(SharedPreferences::class.java)

        `when`(mockContext.getSharedPreferences("server_preferences", Context.MODE_PRIVATE))
            .thenReturn(mockLegacyPrefs)

        `when`(mockLegacyPrefs.all).thenReturn(emptyMap<String, Any?>())

        val method = SecurePreferencesProvider::class.java.getDeclaredMethod(
            "migrateLegacyPreferences",
            Context::class.java,
            SharedPreferences::class.java,
            SharedPreferences::class.java
        )
        method.isAccessible = true
        val job = method.invoke(SecurePreferencesProvider, mockContext, mockEncryptedPrefs, mockLegacyPrefs) as Job
        job.join()

        verify(mockEncryptedPrefs, never()).edit()
        verify(mockLegacyPrefs, never()).edit()
        verify(mockContext, never()).deleteSharedPreferences(any())
    }
}
