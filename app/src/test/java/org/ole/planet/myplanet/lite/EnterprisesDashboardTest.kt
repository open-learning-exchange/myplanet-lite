package org.ole.planet.myplanet.lite

import android.content.SharedPreferences
import com.google.android.material.navigation.NavigationView
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnterprisesDashboardTest {
    @After
    fun tearDown() {
        SecurePreferencesProvider.injectedPreferences = null
    }

    @Test
    fun `survey translation switch reflects persisted active state`() {
        val preferences = mock(SharedPreferences::class.java)
        `when`(
            preferences.getBoolean(DashboardActivity.KEY_SURVEY_TRANSLATIONS_ENABLED, true),
        ).thenReturn(true)
        `when`(
            preferences.getBoolean(DashboardActivity.KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false),
        ).thenReturn(true)
        SecurePreferencesProvider.injectedPreferences = preferences

        val activity = Robolectric.buildActivity(EnterprisesDashboard::class.java).also {
            it.get().setTheme(R.style.Theme_MyPlanetLite)
        }.create().start().resume().get()
        val settingsDrawer =
            activity.findViewById<NavigationView>(R.id.enterprisesSettingsDrawer)
        val surveyTranslationItem =
            settingsDrawer.menu.findItem(R.id.menu_settings_survey_translation)

        assertTrue(surveyTranslationItem.isChecked)
    }
}
