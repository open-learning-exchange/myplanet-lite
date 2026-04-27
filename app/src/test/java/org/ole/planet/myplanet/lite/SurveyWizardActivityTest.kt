package org.ole.planet.myplanet.lite

import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.mlkit.common.sdkinternal.MlKitContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SurveyWizardActivityTest {
    @Before
    fun setUp() {
        val mockPrefs = mock(SharedPreferences::class.java)
        SecurePreferencesProvider.injectedPreferences = mockPrefs
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            MlKitContext.initializeIfNeeded(context)
        } catch (e: Exception) {
            // Ignore if already initialized
        }
    }
    @After
    fun tearDown() {
        SecurePreferencesProvider.injectedPreferences = null
    }
    @Test
    fun `onCreate with null survey finishes activity`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SurveyWizardActivity::class.java)
        val controller = Robolectric.buildActivity(SurveyWizardActivity::class.java, intent)
        val activity = controller.create().get()
        assertTrue(activity.isFinishing)
    }
    @Test
    fun `onCreate with survey sets up toolbar and fragment`() {
        val surveyDocument = SurveyDocument(id = "survey123", name = "Test Survey")
        val intent = SurveyWizardActivity.newIntent(
            context = ApplicationProvider.getApplicationContext(),
            document = surveyDocument,
            teamId = "team1",
            teamName = "Test Team",
            courseId = "course1",
            isExam = true
        )
        val controller = Robolectric.buildActivity(SurveyWizardActivity::class.java, intent)
        val activity = controller.create().start().resume().visible().get()
        assertFalse(activity.isFinishing)
        // Verify toolbar setup
        val toolbar: MaterialToolbar = activity.findViewById(R.id.surveyWizardToolbar)
        assertNotNull(toolbar)
        // Verify fragment was added
        val fragment: Fragment? = activity.supportFragmentManager.findFragmentById(R.id.surveyWizardFragmentContainer)
        assertNotNull(fragment)
        assertTrue(fragment is SurveyWizardFragment)
    }
    @Test
    fun `newIntent creates correct intent`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val surveyDocument = SurveyDocument(id = "survey123", name = "Test Survey")
        val intent = SurveyWizardActivity.newIntent(
            context = context,
            document = surveyDocument,
            teamId = "team1",
            teamName = "Test Team",
            courseId = "course1",
            isExam = true
        )
        assertEquals(SurveyWizardActivity::class.java.name, intent.component?.className)
        assertTrue(intent.hasExtra(SurveyWizardActivity.EXTRA_DOCUMENT))
        val extraDoc = IntentCompat.getSerializableExtra(intent, SurveyWizardActivity.EXTRA_DOCUMENT, SurveyDocument::class.java)!!
        assertEquals("survey123", extraDoc.id)
        // Use reflection to get private constants for testing
        assertEquals("team1", intent.getStringExtra("extra_team_id"))
        assertEquals("Test Team", intent.getStringExtra("extra_team_name"))
        assertEquals("course1", intent.getStringExtra("extra_course_id"))
        assertTrue(intent.getBooleanExtra("extra_is_exam", false))
    }
}
