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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

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
