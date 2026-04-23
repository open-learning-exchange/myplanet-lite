package org.ole.planet.myplanet.lite

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DashboardTeamSurveysFragmentTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
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

    @Test
    fun testFragmentLaunches() {
        val bundle = Bundle().apply {
            putString("arg_team_id", "team123")
            putString("arg_team_name", "Team Alpha")
        }
        launchFragmentInContainer<DashboardTeamSurveysFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_MyPlanetLite
        ).use { scenario ->
            scenario.onFragment { fragment ->
                assertNotNull(fragment)
                assertTrue(fragment.isSurveyFeedFor("team123", "Team Alpha"))
                assertFalse(fragment.isSurveyFeedFor("team123", "Team Beta"))
                assertFalse(fragment.isSurveyFeedFor("team456", "Team Alpha"))
            }
        }
    }

    @Test
    fun testLoadSurveys_MissingServer() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Ensure no server URL is stored
        whenever(mockPrefs.getString("server_url", null)).thenReturn(null)
        val bundle = Bundle().apply {
            putString("arg_team_id", "team123")
            putString("arg_team_name", "Team Alpha")
        }
        launchFragmentInContainer<DashboardTeamSurveysFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_MyPlanetLite
        ).use { scenario ->
            scenario.onFragment { fragment ->
                val errorView = fragment.view?.findViewById<TextView>(R.id.dashboardSurveysError)
                assertNotNull(errorView)
                assertTrue(errorView!!.isVisible)
                assertEquals(context.getString(R.string.dashboard_surveys_missing_server), errorView.text)
            }
        }
    }

    @Test
    fun testLoadSurveys_MissingTeam() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Mock server URL so it passes the first check
        whenever(mockPrefs.getString("server_url", null)).thenReturn("http://test.com")
        // No team arguments
        launchFragmentInContainer<DashboardTeamSurveysFragment>(
            themeResId = R.style.Theme_MyPlanetLite
        ).use { scenario ->
            scenario.onFragment { fragment ->
                val errorView = fragment.view?.findViewById<TextView>(R.id.dashboardSurveysError)
                assertNotNull(errorView)
                assertTrue(errorView!!.isVisible)
                assertEquals(context.getString(R.string.dashboard_surveys_missing_team), errorView.text)
            }
        }
    }

    @Test
    fun testLoadSurveys_MissingCredentials() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Mock server URL
        whenever(mockPrefs.getString("server_url", null)).thenReturn("http://test.com")
        // Do not mock credentials, so it returns null
        val bundle = Bundle().apply {
            putString("arg_team_id", "team123")
            putString("arg_team_name", "Team Alpha")
        }
        launchFragmentInContainer<DashboardTeamSurveysFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_MyPlanetLite
        ).use { scenario ->
            scenario.onFragment { fragment ->
                val errorView = fragment.view?.findViewById<TextView>(R.id.dashboardSurveysError)
                assertNotNull(errorView)
                assertTrue(errorView!!.isVisible)
                assertEquals(context.getString(R.string.dashboard_surveys_missing_credentials), errorView.text)
            }
        }
    }
}
