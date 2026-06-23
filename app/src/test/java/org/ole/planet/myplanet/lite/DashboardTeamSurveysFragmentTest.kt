package org.ole.planet.myplanet.lite

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import androidx.lifecycle.Lifecycle
import org.ole.planet.myplanet.lite.dashboard.DashboardOfflineSurveyStore
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyOutboxStore
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33], instrumentedPackages = ["androidx.loader.content"])
class DashboardTeamSurveysFragmentTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockPrefs = mock()
        mockEditor = mock()
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        SecurePreferencesProvider.injectedPreferences = mockPrefs
        mockWebServer = MockWebServer()
        mockWebServer.start()
        ProfileCredentialsStore.setSessionCredentials(org.ole.planet.myplanet.lite.profile.StoredCredentials("testuser", "testpass"))
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.injectedPreferences = null
        DashboardSurveyOutboxStore.resetForTesting(ApplicationProvider.getApplicationContext())
        DashboardOfflineSurveyStore.resetForTesting(ApplicationProvider.getApplicationContext())
        ProfileCredentialsStore.setSessionCredentials(null)
        mockWebServer.shutdown()
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
        // Clear session credentials that were set in setUp
        ProfileCredentialsStore.setSessionCredentials(null)
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

    @Test
    fun testLoadSurveys_FetchError_EmptyCache() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Mock server to return 500
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        val baseUrl = mockWebServer.url("/").toString()
        whenever(mockPrefs.getString("server_url", null)).thenReturn(baseUrl)

        val bundle = Bundle().apply {
            putString("arg_team_id", "team123")
            putString("arg_team_name", "Team Alpha")
        }

        launchFragmentInContainer<DashboardTeamSurveysFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_MyPlanetLite
        ).use { scenario ->

            scenario.moveToState(Lifecycle.State.RESUMED)

            var retries = 50
            var isErrorVisible = false
            var errorText = ""
            var isRefreshing = false

            while (retries > 0) {
                Thread.sleep(100)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()

                scenario.onFragment { fragment ->
                    val errorView = fragment.view?.findViewById<TextView>(R.id.dashboardSurveysError)
                    val swipeRefresh = fragment.view?.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.dashboardSurveysSwipeRefresh)

                    isErrorVisible = errorView?.isVisible == true
                    errorText = errorView?.text?.toString() ?: ""
                    isRefreshing = swipeRefresh?.isRefreshing == true
                }

                if (isErrorVisible && !isRefreshing) {
                    break
                }
                retries--
            }

            assertTrue("Error view should be visible", isErrorVisible)
            assertEquals(context.getString(R.string.dashboard_surveys_error_loading), errorText)
            assertTrue("SwipeRefresh should not be refreshing", !isRefreshing)
        }
    }

    @Test
<<<<<<< test-update-saved-surveys-15386669546053830364
    fun testUpdateSavedSurveys() {
        val bundle = Bundle().apply {
            putString("arg_team_id", "team123")
            putString("arg_team_name", "Team Alpha")
        }
        launchFragmentInContainer<DashboardTeamSurveysFragment>(
            fragmentArgs = bundle,
            themeResId = R.style.Theme_MyPlanetLite
        ).use { scenario ->
            scenario.onFragment { fragment ->
                // Ensure fragment is initialized
                fragment.view?.let {
                    // Call the method to verify it runs without crashing and updates internal state.
                    val pagerAdapterField = fragment.javaClass.getDeclaredField("pagerAdapter")
                    pagerAdapterField.isAccessible = true
                    val adapter = pagerAdapterField.get(fragment)

                    val updateMethod = adapter.javaClass.getDeclaredMethod("updateSavedSurveys", Set::class.java, Map::class.java)
                    updateMethod.isAccessible = true

                    // First we need to submit some items to verify the update modifies them
                    val submitMethod = adapter.javaClass.getDeclaredMethod(
                        "submit",
                        List::class.java,
                        List::class.java,
                        Map::class.java,
                        Set::class.java,
                        Map::class.java,
                        List::class.java
                    )
                    submitMethod.isAccessible = true

                    val doc1 = org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument(id = "doc1", rev = "1-abc")
                    val doc2 = org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument(id = "doc2", rev = "2-def")

                    submitMethod.invoke(
                        adapter,
                        listOf(doc1),
                        listOf(doc2),
                        emptyMap<String, Int>(),
                        emptySet<String>(),
                        emptyMap<String, String?>(),
                        emptyList<org.ole.planet.myplanet.lite.dashboard.DashboardSurveyOutboxStore.OutboxEntry>()
                    )

                    // Now perform the update
                    updateMethod.invoke(
                        adapter,
                        setOf("doc1"),
                        mapOf("doc1" to "1-abc")
                    )

                    // Verify the items are updated
                    val teamItemsField = adapter.javaClass.getDeclaredField("teamItems")
                    teamItemsField.isAccessible = true
                    val teamItems = teamItemsField.get(adapter) as List<*>

                    val adoptedItemsField = adapter.javaClass.getDeclaredField("adoptedItems")
                    adoptedItemsField.isAccessible = true
                    val adoptedItems = adoptedItemsField.get(adapter) as List<*>

                    assertEquals(1, teamItems.size)
                    assertEquals(1, adoptedItems.size)

                    val teamItem1 = teamItems[0]!!
                    val adoptedItem1 = adoptedItems[0]!!

                    val isSavedField = teamItem1.javaClass.getDeclaredField("isSaved")
                    isSavedField.isAccessible = true

                    val savedRevisionField = teamItem1.javaClass.getDeclaredField("savedRevision")
                    savedRevisionField.isAccessible = true

                    assertTrue(isSavedField.getBoolean(teamItem1))
                    assertEquals("1-abc", savedRevisionField.get(teamItem1))

                    assertFalse(isSavedField.getBoolean(adoptedItem1))
                    assertEquals(null, savedRevisionField.get(adoptedItem1))
                }
            }
        }
    }

=======
    fun testNewInstanceForTeam() {
        val teamId = "test_team_id"
        val teamName = "Test Team Name"

        val fragment = DashboardTeamSurveysFragment.newInstanceForTeam(teamId, teamName)

        assertNotNull(fragment)
        assertNotNull(fragment.arguments)
        assertEquals(teamId, fragment.arguments?.getString("arg_team_id"))
        assertEquals(teamName, fragment.arguments?.getString("arg_team_name"))
    }
>>>>>>> main
}
