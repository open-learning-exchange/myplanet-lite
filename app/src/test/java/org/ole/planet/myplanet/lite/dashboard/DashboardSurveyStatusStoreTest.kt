package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DashboardSurveyStatusStoreTest {

    private lateinit var context: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockPrefs = mock()
        mockEditor = mock()
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        SecurePreferencesProvider.injectedPreferences = mockPrefs
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.injectedPreferences = null
    }

    @org.junit.Test
    fun `getStatus returns null when ID is null`() {
        val store = DashboardSurveyStatusStore(context)
        org.junit.Assert.assertNull(store.getStatus(null))
    }

    @org.junit.Test
    fun `getStatus returns null when ID is blank`() {
        val store = DashboardSurveyStatusStore(context)
        org.junit.Assert.assertNull(store.getStatus("   "))
    }

    @org.junit.Test
    fun `getStatus returns null when key not in prefs`() {
        val store = DashboardSurveyStatusStore(context)
        whenever(mockPrefs.getString("survey123", null)).thenReturn(null)
        org.junit.Assert.assertNull(store.getStatus("survey123"))
    }

    @org.junit.Test
    fun `getStatus returns NEW when stored in prefs`() {
        val store = DashboardSurveyStatusStore(context)
        whenever(mockPrefs.getString("survey123", null)).thenReturn("NEW")
        org.junit.Assert.assertEquals(SurveyStatus.NEW, store.getStatus("survey123"))
    }

    @org.junit.Test
    fun `getStatus returns null when invalid status stored`() {
        val store = DashboardSurveyStatusStore(context)
        whenever(mockPrefs.getString("survey123", null)).thenReturn("INVALID")
        org.junit.Assert.assertNull(store.getStatus("survey123"))
    }

    @org.junit.Test
    fun `getStatus with username uses namespaced key`() {
        val store = DashboardSurveyStatusStore(context, "user1")
        whenever(mockPrefs.getString("user1:survey123", null)).thenReturn("COMPLETED")
        org.junit.Assert.assertEquals(SurveyStatus.COMPLETED, store.getStatus("survey123"))
    }

    @org.junit.Test
    fun `ensureNewDefaults with empty list does nothing`() {
        val store = DashboardSurveyStatusStore(context)
        store.ensureNewDefaults(emptyList())
        org.mockito.kotlin.verify(mockPrefs, org.mockito.kotlin.never()).edit()
    }

    @org.junit.Test
    fun `ensureNewDefaults sets NEW for missing keys`() {
        val store = DashboardSurveyStatusStore(context)
        whenever(mockPrefs.contains("survey1")).thenReturn(false)
        whenever(mockPrefs.contains("survey2")).thenReturn(true)

        store.ensureNewDefaults(listOf("survey1", "survey2"))

        org.mockito.kotlin.verify(mockEditor).putString("survey1", "NEW")
        org.mockito.kotlin.verify(mockEditor, org.mockito.kotlin.never()).putString("survey2", any())
        org.mockito.kotlin.verify(mockEditor).apply()
    }

    @org.junit.Test
    fun `ensureNewDefaults with username uses namespaced keys`() {
        val store = DashboardSurveyStatusStore(context, "user1")
        whenever(mockPrefs.contains("user1:survey1")).thenReturn(false)

        store.ensureNewDefaults(listOf("survey1"))

        org.mockito.kotlin.verify(mockEditor).putString("user1:survey1", "NEW")
        org.mockito.kotlin.verify(mockEditor).apply()
    }

    @org.junit.Test
    fun `markViewed sets status to VIEWED`() {
        val store = DashboardSurveyStatusStore(context)
        store.markViewed("survey123")

        org.mockito.kotlin.verify(mockEditor).putString("survey123", "VIEWED")
        org.mockito.kotlin.verify(mockEditor).apply()
    }

    @org.junit.Test
    fun `markCompleted sets status to COMPLETED`() {
        val store = DashboardSurveyStatusStore(context)
        store.markCompleted("survey123")

        org.mockito.kotlin.verify(mockEditor).putString("survey123", "COMPLETED")
        org.mockito.kotlin.verify(mockEditor).apply()
    }

    @org.junit.Test
    fun `markViewed with username uses namespaced key`() {
        val store = DashboardSurveyStatusStore(context, "user1")
        store.markViewed("survey123")

        org.mockito.kotlin.verify(mockEditor).putString("user1:survey123", "VIEWED")
        org.mockito.kotlin.verify(mockEditor).apply()
    }
}
