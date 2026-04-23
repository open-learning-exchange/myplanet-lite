package org.ole.planet.myplanet.lite

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferencesTest
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardSurveysFragmentTest {

    private lateinit var context: Context
    private lateinit var mockPrefs: DashboardTeamSelectionPreferencesTest.FakeSharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockPrefs = DashboardTeamSelectionPreferencesTest.FakeSharedPreferences()
        SecurePreferencesProvider.injectedPreferences = mockPrefs
        DashboardTeamSelectionPreferences.clearSelectedTeam(context)
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.injectedPreferences = null
    }

    @Test
    fun `when no team is selected, empty view is visible and content is hidden`() {
        val scenario = launchFragmentInContainer<DashboardSurveysFragment>(
            themeResId = R.style.Theme_MyPlanetLite
        )

        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val emptyView = view.findViewById<View>(R.id.dashboardSurveysEmptyView)
            val contentContainer = view.findViewById<View>(R.id.dashboardSurveysContentContainer)

            assertTrue(emptyView.visibility == View.VISIBLE)
            assertTrue(contentContainer.visibility == View.GONE)

            // Verify empty view text is correct
            val emptyTextView = emptyView as android.widget.TextView
            assertEquals(fragment.getString(R.string.dashboard_teams_select_team_hint), emptyTextView.text.toString())
        }
    }

    @Test
    fun `when team is selected, empty view is hidden and team surveys fragment is loaded`() {
        DashboardTeamSelectionPreferences.setSelectedTeam(context, "team-123", "Team Alpha")

        val scenario = launchFragmentInContainer<DashboardSurveysFragment>(
            themeResId = R.style.Theme_MyPlanetLite
        )

        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val emptyView = view.findViewById<View>(R.id.dashboardSurveysEmptyView)
            val contentContainer = view.findViewById<View>(R.id.dashboardSurveysContentContainer)

            assertTrue(emptyView.visibility == View.GONE)
            assertTrue(contentContainer.visibility == View.VISIBLE)

            // Execute pending transactions
            fragment.childFragmentManager.executePendingTransactions()

            val teamFragment = fragment.childFragmentManager.findFragmentById(R.id.dashboardSurveysContentContainer)
            assertNotNull(teamFragment)
            assertTrue(teamFragment is DashboardTeamSurveysFragment)
            assertTrue((teamFragment as DashboardTeamSurveysFragment).isSurveyFeedFor("team-123", "Team Alpha"))
        }
    }

    @Test
    fun `onResume updates content when team selection changes`() {
        // Start with team 1
        DashboardTeamSelectionPreferences.setSelectedTeam(context, "team-123", "Team Alpha")

        val scenario = launchFragmentInContainer<DashboardSurveysFragment>(
            themeResId = R.style.Theme_MyPlanetLite,
            initialState = Lifecycle.State.RESUMED
        )

        scenario.onFragment { fragment ->
            fragment.childFragmentManager.executePendingTransactions()
            var teamFragment = fragment.childFragmentManager.findFragmentById(R.id.dashboardSurveysContentContainer)
            assertTrue((teamFragment as DashboardTeamSurveysFragment).isSurveyFeedFor("team-123", "Team Alpha"))

            // Change team preference behind the scenes
            DashboardTeamSelectionPreferences.setSelectedTeam(context, "team-456", "Team Beta")
        }

        // Trigger onResume by moving state
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)

        scenario.onFragment { fragment ->
            fragment.childFragmentManager.executePendingTransactions()
            val teamFragment = fragment.childFragmentManager.findFragmentById(R.id.dashboardSurveysContentContainer)
            assertNotNull(teamFragment)
            assertTrue(teamFragment is DashboardTeamSurveysFragment)
            assertTrue((teamFragment as DashboardTeamSurveysFragment).isSurveyFeedFor("team-456", "Team Beta"))
        }
    }
}
