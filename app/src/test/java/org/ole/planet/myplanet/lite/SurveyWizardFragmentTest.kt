package org.ole.planet.myplanet.lite

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.common.sdkinternal.MlKitContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SurveyWizardFragmentTest {

    @Test
    fun testNewInstance_createsFragmentWithArguments() {
        val document = SurveyDocument(
            id = "doc1",
            rev = "rev1",
            name = "Survey 1",
            questions = emptyList()
        )

        val fragment = SurveyWizardFragment.newInstance(
            document = document,
            teamId = "team1",
            teamName = "Team One",
            courseId = "course1",
            isExam = true
        )

        val args = fragment.arguments
        assertNotNull(args)
        @Suppress("DEPRECATION")
        assertEquals(document, args?.getSerializable("arg_document"))
        assertEquals("team1", args?.getString("arg_team_id"))
        assertEquals("Team One", args?.getString("arg_team_name"))
        assertEquals("course1", args?.getString("arg_course_id"))
        assertEquals(true, args?.getBoolean("arg_is_exam"))
    }

    private lateinit var mockPrefs: android.content.SharedPreferences

    @org.junit.Before
    fun setup() {
        mockPrefs = org.mockito.Mockito.mock(android.content.SharedPreferences::class.java)
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = mockPrefs
    }

    @org.junit.After
    fun teardown() {
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = null
    }

    @Test
    fun testOnViewCreated_withEmptyQuestions_showsToastAndPopsBackStack() {
        MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())

        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()

        val document = SurveyDocument(
            id = "doc1",
            rev = "rev1",
            name = "Empty Survey",
            questions = emptyList()
        )
        val args = Bundle().apply {
            putSerializable("arg_document", document)
        }

        val fragment = SurveyWizardFragment().apply {
            arguments = args
        }

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "survey")
            .addToBackStack(null)
            .commit()

        activity.supportFragmentManager.executePendingTransactions()

        val latestToast = ShadowToast.getTextOfLatestToast()
        assertNotNull("Expected a toast to be shown", latestToast)
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
    }

    @Test
    fun testOnViewCreated_withQuestions_displaysSurveyTitleAndDescription() {
        MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())

        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val questions = listOf(
            SurveyQuestion(
                body = "What is your name?",
                type = "input"
            )
        )
        val document = SurveyDocument(
            id = "doc1",
            rev = "rev1",
            name = "Test Survey Title",
            description = "Test Survey Description",
            questions = questions
        )
        val args = Bundle().apply {
            putSerializable("arg_document", document)
        }

        val fragment = SurveyWizardFragment().apply {
            arguments = args
        }

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "survey")
            .commit()

        activity.supportFragmentManager.executePendingTransactions()

        val titleView = fragment.view?.findViewById<android.widget.TextView>(R.id.surveyWizardTitle)
        val descriptionView = fragment.view?.findViewById<android.widget.TextView>(R.id.surveyWizardDescription)

        assertNotNull("Title view should be present", titleView)
        assertEquals("Test Survey Title", titleView?.text)

        assertNotNull("Description view should be present", descriptionView)
        assertEquals("Test Survey Description", descriptionView?.text)
    }
}
