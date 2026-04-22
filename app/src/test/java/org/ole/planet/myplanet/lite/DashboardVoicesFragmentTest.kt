package org.ole.planet.myplanet.lite

import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.mockito.Mockito
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import androidx.lifecycle.Lifecycle

@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"]) // Helps prevent FragmentScenario crash
class DashboardVoicesFragmentTest {

    @Test
    fun `newInstanceForTeam creates fragment with correct arguments`() {
        val teamId = "team_123"
        val teamName = "Test Team"

        val fragment = DashboardVoicesFragment.newInstanceForTeam(teamId, teamName)

        val args = fragment.arguments
        assertNotNull(args)
        assertEquals(teamId, args?.getString("arg_team_id"))
        assertEquals(teamName, args?.getString("arg_team_name"))
    }

    private fun mockPreferencesProviderAndLaunch(args: Bundle?, block: (DashboardVoicesFragment) -> Unit) {
        val mockPrefs = Mockito.mock(SharedPreferences::class.java)
        val mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)

        Mockito.`when`(mockPrefs.getString(Mockito.anyString(), Mockito.nullable(String::class.java))).thenReturn(null)
        Mockito.`when`(mockPrefs.edit()).thenReturn(mockEditor)

        // Using the actual injectedPreferences visibleForTesting property in SecurePreferencesProvider.kt
        SecurePreferencesProvider.injectedPreferences = mockPrefs

        // To ensure initializeSession reads the arguments from the Bundle provided through FragmentScenario,
        // we must allow it to proceed to RESUMED since the method is triggered via lifecycleScope.launch in onViewCreated
        val scenario = FragmentScenario.launchInContainer(DashboardVoicesFragment::class.java, args, R.style.Theme_MyPlanetLite)
        scenario.onFragment { fragment ->
            block(fragment)
        }

        SecurePreferencesProvider.injectedPreferences = null
    }

    @Test
    fun `isTeamFeedFor returns true when both id and name match`() {
        val teamId = "team_123"
        val teamName = "Test Team"

        val args = Bundle().apply {
            putString("arg_team_id", teamId)
            putString("arg_team_name", teamName)
        }

        mockPreferencesProviderAndLaunch(args) { fragment ->
            assertTrue(fragment.isTeamFeedFor(teamId, teamName))
        }
    }

    @Test
    fun `isTeamFeedFor returns true with case insensitive match`() {
        val teamId = "team_123"
        val teamName = "Test Team"

        val args = Bundle().apply {
            putString("arg_team_id", teamId)
            putString("arg_team_name", teamName)
        }

        mockPreferencesProviderAndLaunch(args) { fragment ->
            assertTrue(fragment.isTeamFeedFor("TEAM_123", "test team"))
        }
    }

    @Test
    fun `isTeamFeedFor returns false when id does not match`() {
        val teamId = "team_123"
        val teamName = "Test Team"

        val args = Bundle().apply {
            putString("arg_team_id", teamId)
            putString("arg_team_name", teamName)
        }

        mockPreferencesProviderAndLaunch(args) { fragment ->
            assertFalse(fragment.isTeamFeedFor("different_id", teamName))
        }
    }

    @Test
    fun `isTeamFeedFor returns false when name does not match`() {
        val teamId = "team_123"
        val teamName = "Test Team"

        val args = Bundle().apply {
            putString("arg_team_id", teamId)
            putString("arg_team_name", teamName)
        }

        mockPreferencesProviderAndLaunch(args) { fragment ->
            assertFalse(fragment.isTeamFeedFor(teamId, "Different Name"))
        }
    }

    @Test
    fun `isTeamFeedFor returns false when fragment has no team arguments`() {
        mockPreferencesProviderAndLaunch(null) { fragment ->
            assertFalse(fragment.isTeamFeedFor("team_123", "Test Team"))
        }
    }
}
