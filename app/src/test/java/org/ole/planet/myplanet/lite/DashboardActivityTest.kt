package org.ole.planet.myplanet.lite

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
    fun `learning starts with offline courses and has no surveys option`() {
        ActivityScenario.launch<DashboardActivity>(DashboardActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(View.VISIBLE, activity.findViewById<FrameLayout>(R.id.dashboardCoursesContainer).visibility)
                assertEquals(View.GONE, activity.findViewById<FrameLayout>(R.id.dashboardVoicesContainer).visibility)
                assertEquals(3, activity.findViewById<android.widget.LinearLayout>(R.id.dashboardBottomNavigation).childCount)
            }
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
    fun `learning bottom navigation switches between resources and offline courses`() {
        ActivityScenario.launch<DashboardActivity>(DashboardActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val courses = activity.findViewById<FrameLayout>(R.id.dashboardCoursesContainer)
                val resources = activity.findViewById<FrameLayout>(R.id.dashboardResourcesContainer)
                activity.findViewById<ImageView>(R.id.dashboardResourcesIcon).performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(View.VISIBLE, resources.visibility)
                val resourcePage = activity.supportFragmentManager.findFragmentById(R.id.dashboardResourcesContainer)
                assertTrue(resourcePage is DashboardResourcesPageFragment)
                assertFalse((resourcePage as DashboardResourcesPageFragment).isTeamResourcesTab)
                assertEquals(View.GONE, courses.visibility)
                activity.findViewById<ImageView>(R.id.dashboardCoursesIcon).performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(View.VISIBLE, courses.visibility)
                assertEquals(View.GONE, resources.visibility)
            }
        }
    }

    @Test
    fun `teams surveys opens full survey section and hides creation buttons`() {
        val intent = Intent(context, TeamsDashboard::class.java).apply {
            putExtra(TeamsDashboard.EXTRA_OPEN_SURVEYS, true)
            putExtra(DashboardActivity.EXTRA_OFFLINE_MODE, true)
        }
        ActivityScenario.launch<TeamsDashboard>(intent).use { scenario ->
            scenario.onActivity { activity ->
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertTrue(activity.isOfflineModeActive())
                assertTrue(activity.supportFragmentManager.findFragmentById(R.id.teamsDashboardSurveysContainer) is DashboardSurveysFragment)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.teamsDashboardSurveysContainer).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.teamsDashboardCreateFab).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.teamsDashboardAddVoiceFab).visibility)
                activity.findViewById<ImageView>(R.id.teamsDashboardMyTeamsIcon).performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(View.GONE, activity.findViewById<View>(R.id.teamsDashboardSurveysContainer).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.teamsDashboardMyTeamsContainer).visibility)
            }
        }
    }
    @Test
    fun `teams members opens existing members section`() {
        ActivityScenario.launch<TeamsDashboard>(TeamsDashboard::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.teamsDashboardTeamMembersIcon).performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertTrue(activity.supportFragmentManager.findFragmentById(R.id.teamsDashboardMembersContainer) is DashboardTeamMembersFragment)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.teamsDashboardMembersContainer).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.teamsDashboardMyTeamsContainer).visibility)
                activity.findViewById<ImageView>(R.id.teamsDashboardMyTeamsIcon).performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(View.GONE, activity.findViewById<View>(R.id.teamsDashboardMembersContainer).visibility)
            }
        }
    }

    @Test
    fun `teams resources opens team resource page and hides other sections`() {
        ActivityScenario.launch<TeamsDashboard>(TeamsDashboard::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<ImageView>(R.id.teamsDashboardResourcesIcon).performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                val resources = activity.supportFragmentManager.findFragmentById(R.id.teamsDashboardResourcesContainer)
                assertTrue(resources is DashboardResourcesPageFragment)
                assertTrue((resources as DashboardResourcesPageFragment).isTeamResourcesTab)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.teamsDashboardResourcesContainer).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.teamsDashboardMyTeamsContainer).visibility)
                activity.findViewById<ImageView>(R.id.teamsDashboardMyTeamsIcon).performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(View.GONE, activity.findViewById<View>(R.id.teamsDashboardResourcesContainer).visibility)
            }
        }
    }

}
