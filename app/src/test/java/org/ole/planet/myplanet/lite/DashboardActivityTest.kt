package org.ole.planet.myplanet.lite

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardActivityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val sharedPrefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        SecurePreferencesProvider.injectedPreferences = sharedPrefs
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        SecurePreferencesProvider.resetForTesting()
    }

    private fun setupNetworkConnectivity(isConnected: Boolean) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowConnectivityManager = Shadows.shadowOf(connectivityManager)
        if (isConnected) {
            val capabilities = ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowConnectivityManager.setDefaultNetworkActive(true)
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                shadowConnectivityManager.setNetworkCapabilities(activeNetwork, capabilities)
            }
        } else {
            shadowConnectivityManager.setDefaultNetworkActive(false)
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                shadowConnectivityManager.setNetworkCapabilities(activeNetwork, null)
            }
        }
    }

    @Test
    fun `isOfflineModeActive returns true when offline mode is active`() {
        // Default Robolectric behavior is offline
        ActivityScenario.launch<DashboardActivity>(DashboardActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertTrue("isOfflineModeActive should return true when offline", activity.isOfflineModeActive())
            }
        }
    }

    @Test
    fun `isOfflineModeActive returns false when online`() {
        setupNetworkConnectivity(true)
        val intent = Intent(context, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_OFFLINE_MODE, false)
        }
        ActivityScenario.launch<DashboardActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertFalse("isOfflineModeActive should return false when online", activity.isOfflineModeActive())
            }
        }
    }

    @Test
    fun `activity initializes views correctly`() {
        ActivityScenario.launch<DashboardActivity>(DashboardActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                Shadows.shadowOf(Looper.getMainLooper()).idle()

                val viewPager = activity.findViewById<ViewPager2>(R.id.dashboardViewPager)
                val surveysContainer = activity.findViewById<FrameLayout>(R.id.dashboardSurveysContainer)
                val homeIcon = activity.findViewById<ImageView>(R.id.dashboardHomeIcon)
                val surveysIcon = activity.findViewById<ImageView>(R.id.dashboardSurveysIcon)
                val coursesIcon = activity.findViewById<ImageView>(R.id.dashboardCoursesIcon)
                val teamMembersIcon = activity.findViewById<ImageView>(R.id.dashboardTeamMembersIcon)

                // For robolectric the offline mode kicks in. Offline mode only shows surveys
                assertEquals("Surveys Container should be visible in offline mode", View.VISIBLE, surveysContainer.visibility)
                assertEquals("ViewPager should be gone in offline mode", View.GONE, viewPager.visibility)

                assertEquals("Home Icon should be visible", View.VISIBLE, homeIcon.visibility)
                assertEquals("Surveys Icon should be visible", View.VISIBLE, surveysIcon.visibility)
                assertEquals("Courses Icon should be visible", View.VISIBLE, coursesIcon.visibility)
                assertEquals("Team Members Icon should be visible", View.VISIBLE, teamMembersIcon.visibility)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `getVoicePageSizePreference returns default 20 when not set`() {
        assertEquals(20, DashboardActivity.getVoicePageSizePreference(context))
    }

    @Test
    fun `getVoicePageSizePreference returns valid options`() {
        val prefs = SecurePreferencesProvider.getServerPreferences(context)

        prefs.edit().putInt("voice_page_size", 10).commit()
        assertEquals(10, DashboardActivity.getVoicePageSizePreference(context))

        prefs.edit().putInt("voice_page_size", 40).commit()
        assertEquals(40, DashboardActivity.getVoicePageSizePreference(context))
    }

    @Test
    fun `getVoicePageSizePreference returns default 20 for invalid options`() {
        val prefs = SecurePreferencesProvider.getServerPreferences(context)
        prefs.edit().putInt("voice_page_size", 30).commit()
        assertEquals(20, DashboardActivity.getVoicePageSizePreference(context))
    }

    @Test
    fun `isSurveyTranslationEnabled returns default value when not set`() {
        ActivityScenario.launch<DashboardActivity>(DashboardActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(true, activity.isSurveyTranslationEnabled())
            }
        }
    }

    @Test
    fun `isSurveyTranslationEnabled returns true when enabled in preferences`() {
        SecurePreferencesProvider.getServerPreferences(context).edit()
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATIONS_ENABLED, true)
            .commit()
        ActivityScenario.launch<DashboardActivity>(DashboardActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(true, activity.isSurveyTranslationEnabled())
            }
        }
    }

    @Test
    fun `isSurveyTranslationEnabled returns false when disabled in preferences`() {
        SecurePreferencesProvider.getServerPreferences(context).edit()
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATIONS_ENABLED, false)
            .commit()
        ActivityScenario.launch<DashboardActivity>(DashboardActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(false, activity.isSurveyTranslationEnabled())
            }
        }
    }

    @Test
    fun `isSurveyTranslationActive returns true when enabled and accepted`() {
        SecurePreferencesProvider.getServerPreferences(context).edit()
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATIONS_ENABLED, true)
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, true)
            .commit()
        assertTrue(DashboardActivity.isSurveyTranslationActive(context))
    }

    @Test
    fun `isSurveyTranslationActive returns false when enabled but not accepted`() {
        SecurePreferencesProvider.getServerPreferences(context).edit()
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATIONS_ENABLED, true)
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false)
            .commit()
        assertFalse(DashboardActivity.isSurveyTranslationActive(context))
    }

    @Test
    fun `isSurveyTranslationActive returns false when not enabled but accepted`() {
        SecurePreferencesProvider.getServerPreferences(context).edit()
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATIONS_ENABLED, false)
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, true)
            .commit()
        assertFalse(DashboardActivity.isSurveyTranslationActive(context))
    }

    @Test
    fun `isSurveyTranslationActive returns false when neither enabled nor accepted`() {
        SecurePreferencesProvider.getServerPreferences(context).edit()
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATIONS_ENABLED, false)
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false)
            .commit()
        assertFalse(DashboardActivity.isSurveyTranslationActive(context))
    }

    @Test
    fun `isSurveyTranslationConsentAccepted returns default false when not set`() {
        assertEquals(false, DashboardActivity.isSurveyTranslationConsentAccepted(context))
    }

    @Test
    fun `isSurveyTranslationConsentAccepted returns true when set to true in preferences`() {
        SecurePreferencesProvider.getServerPreferences(context).edit()
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, true)
            .commit()
        assertEquals(true, DashboardActivity.isSurveyTranslationConsentAccepted(context))
    }

    @Test
    fun `isSurveyTranslationConsentAccepted returns false when set to false in preferences`() {
        SecurePreferencesProvider.getServerPreferences(context).edit()
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false)
            .commit()
        assertEquals(false, DashboardActivity.isSurveyTranslationConsentAccepted(context))
    }

    @Test
    fun `bottom navigation switches sections correctly`() {
        ActivityScenario.launch<DashboardActivity>(DashboardActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                Shadows.shadowOf(Looper.getMainLooper()).idle()

                val viewPager = activity.findViewById<ViewPager2>(R.id.dashboardViewPager)
                val surveysContainer = activity.findViewById<FrameLayout>(R.id.dashboardSurveysContainer)
                val coursesContainer = activity.findViewById<FrameLayout>(R.id.dashboardCoursesContainer)
                val teamMembersContainer = activity.findViewById<FrameLayout>(R.id.dashboardTeamMembersContainer)

                val surveysIcon = activity.findViewById<ImageView>(R.id.dashboardSurveysIcon)
                val coursesIcon = activity.findViewById<ImageView>(R.id.dashboardCoursesIcon)
                val teamMembersIcon = activity.findViewById<ImageView>(R.id.dashboardTeamMembersIcon)
                val homeIcon = activity.findViewById<ImageView>(R.id.dashboardHomeIcon)

                // Offline mode defaults to Surveys
                assertEquals("Surveys Container should be visible initially in offline mode", View.VISIBLE, surveysContainer.visibility)
                assertEquals("ViewPager should be gone initially in offline mode", View.GONE, viewPager.visibility)

                // Click Courses
                coursesIcon.performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals("Surveys Container should be gone after clicking Courses", View.GONE, surveysContainer.visibility)
                assertEquals("Courses Container should be visible after clicking Courses", View.VISIBLE, coursesContainer.visibility)

                // Click Surveys
                surveysIcon.performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals("Courses Container should be gone after clicking Surveys", View.GONE, coursesContainer.visibility)
                assertEquals("Surveys Container should be visible after clicking Surveys", View.VISIBLE, surveysContainer.visibility)

                // Click Team Members (Disabled in offline mode so it shouldn't work)
                teamMembersIcon.performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals("Surveys Container should remain visible", View.VISIBLE, surveysContainer.visibility)
                assertEquals("Team Members Container should remain gone", View.GONE, teamMembersContainer.visibility)

                // Click Home (Disabled in offline mode so it shouldn't work)
                homeIcon.performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals("Surveys Container should remain visible", View.VISIBLE, surveysContainer.visibility)
                assertEquals("ViewPager should remain gone", View.GONE, viewPager.visibility)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
