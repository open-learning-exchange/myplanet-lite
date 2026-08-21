package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.ole.planet.myplanet.lite.DashboardActivity
import org.ole.planet.myplanet.lite.MyPlanetLite
import org.ole.planet.myplanet.lite.SplashScreen
import org.ole.planet.myplanet.lite.SurveyWizardActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppNavigatorTest {

    @Test
    fun `extractPostId returns forwarded post id`() {
        val intent = Intent().apply {
            putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, "12345")
        }
        assertEquals("12345", AppNavigator.extractPostId(intent))
    }

    @Test
    fun `extractPostId falls back to deep link post id`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myplanetlite://post/12345")
        }
        assertEquals("12345", AppNavigator.extractPostId(intent))
    }

    @Test
    fun `extractPostId returns null if no post id is present`() {
        val intent = Intent()
        assertNull(AppNavigator.extractPostId(intent))
    }

    @Test
    fun `navigateToDashboard starts DashboardActivity with right extras`() {
        val context: Context = mock()
        AppNavigator.navigateToDashboard(context, "12345", true)

        argumentCaptor<Intent>().apply {
            verify(context).startActivity(capture())
            assertEquals(DashboardActivity::class.java.name, firstValue.component?.className)
            assertTrue(firstValue.getBooleanExtra(DashboardActivity.EXTRA_OFFLINE_MODE, false))
            assertEquals("12345", firstValue.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID))
        }
    }

    @Test
    fun `navigateToLogin starts MyPlanetLite with right extras`() {
        val context: Context = mock()
        val originalIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myplanetlite://post/12345")
            putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, "54321")
        }
        AppNavigator.navigateToLogin(context, true, originalIntent)

        argumentCaptor<Intent>().apply {
            verify(context).startActivity(capture())
            assertEquals(MyPlanetLite::class.java.name, firstValue.component?.className)
            assertTrue(firstValue.getBooleanExtra(MyPlanetLite.EXTRA_ALLOW_AUTO_LOGIN, false))
            assertEquals("54321", firstValue.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID))
        }
    }

    @Test
    fun `navigateToLogin forwards original action and data`() {
        val context: Context = mock()
        val originalIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myplanetlite://post/12345")
        }
        AppNavigator.navigateToLogin(context, false, originalIntent)

        argumentCaptor<Intent>().apply {
            verify(context).startActivity(capture())
            assertEquals(MyPlanetLite::class.java.name, firstValue.component?.className)
            assertEquals(Intent.ACTION_VIEW, firstValue.action)
            assertEquals(Uri.parse("myplanetlite://post/12345"), firstValue.data)
        }
    }

    @Test
    fun `navigateToSplash starts SplashScreen with right extras`() {
        val context: Context = mock()
        AppNavigator.navigateToSplash(context, "12345")

        argumentCaptor<Intent>().apply {
            verify(context).startActivity(capture())
            assertEquals(SplashScreen::class.java.name, firstValue.component?.className)
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, firstValue.flags)
            assertEquals("12345", firstValue.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID))
        }
    }

    @Test
    fun `navigateToPostDetail starts DashboardPostDetailActivity with right extras`() {
        val context: Context = mock()
        AppNavigator.navigateToPostDetail(context, "12345")

        argumentCaptor<Intent>().apply {
            verify(context).startActivity(capture())
            assertEquals(DashboardPostDetailActivity::class.java.name, firstValue.component?.className)
            assertEquals("12345", firstValue.getStringExtra(DashboardPostDetailActivity.EXTRA_POST_ID))
        }
    }

    @Test
    fun `navigateToSurvey starts SurveyWizardActivity with right extras`() {
        val context: Context = mock()
        val document = DashboardSurveysRepository.SurveyDocument(id = "survey-1")

        // Assuming SurveyWizardActivity.newIntent returns an Intent properly configured
        // We will just verify it sets the NEW_TASK and CLEAR_TASK flags since other params are encapsulated
        AppNavigator.navigateToSurvey(context, document, "team-1", "Team 1", "https://server.com", true)

        argumentCaptor<Intent>().apply {
            verify(context).startActivity(capture())
            assertEquals(SurveyWizardActivity::class.java.name, firstValue.component?.className)
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, firstValue.flags)
        }
    }
}
