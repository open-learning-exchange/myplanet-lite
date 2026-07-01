package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import com.google.android.material.button.MaterialButton
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
        val descriptionView =
            fragment.view?.findViewById<android.widget.TextView>(R.id.surveyWizardDescription)

        assertNotNull("Title view should be present", titleView)
        assertEquals("Test Survey Title", titleView?.text)

        assertNotNull("Description view should be present", descriptionView)
        assertEquals("Test Survey Description", descriptionView?.text)
    }

    @Test
    fun testOnViewCreated_withRatingScaleMax_displaysConfiguredNumberOfButtons() {
        mapOf(
            2 to 2,
            3 to 3,
            5 to 5,
            6 to 3,
            8 to 3,
            9 to 3,
            12 to 4
        ).forEach { (scaleMax, expectedColumns) ->
            assertRatingScaleButtons(scaleMax, expectedColumns)
        }
    }

    private fun assertRatingScaleButtons(scaleMax: Int, expectedColumns: Int) {
        MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())

        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val questions = listOf(
            SurveyQuestion(
                body = "How much?",
                type = "ratingScale",
                scaleMax = scaleMax
            )
        )
        val document = SurveyDocument(
            id = "doc1",
            rev = "rev1",
            name = "Scaled Survey",
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

        SurveyWizardFragment::class.java.getDeclaredField("currentIndex").apply {
            isAccessible = true
            setInt(fragment, 1)
        }
        SurveyWizardFragment::class.java.getDeclaredMethod("showStep", Int::class.javaPrimitiveType)
            .apply {
                isAccessible = true
                invoke(fragment, 1)
            }

        val ratingButtons = fragment.view
            ?.let { findViewsOfType(it, MaterialButton::class.java) }
            .orEmpty()
            .filter { button -> button.text?.toString()?.toIntOrNull() != null }
        val gridLayout = fragment.view
            ?.let { findViewsOfType(it, GridLayout::class.java) }
            ?.firstOrNull()

        assertEquals((1..scaleMax).map { it.toString() }, ratingButtons.map { it.text.toString() })
        assertEquals(expectedColumns, gridLayout?.columnCount)
    }

    private fun <T : View> findViewsOfType(root: View, type: Class<T>): List<T> {
        val matches = mutableListOf<T>()
        if (type.isInstance(root)) {
            type.cast(root)?.let { matches.add(it) }
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                matches.addAll(findViewsOfType(root.getChildAt(index), type))
            }
        }
        return matches
    }

    @Test
    fun testParseBirthDateIso() {
        val fragment = SurveyWizardFragment()

        val formatter =
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }

        // Valid dates
        assertEquals(formatter.parse("2000-01-01")?.time, fragment.parseBirthDateIso("2000-01-01"))
        assertEquals(formatter.parse("2023-01-01")?.time, fragment.parseBirthDateIso("2023-01-01"))

        // Invalid strings
        assertEquals(null, fragment.parseBirthDateIso("invalid"))
        assertEquals(null, fragment.parseBirthDateIso("2000/01/01"))

        // Null and blank strings
        assertEquals(null, fragment.parseBirthDateIso(null))
        assertEquals(null, fragment.parseBirthDateIso(""))
        assertEquals(null, fragment.parseBirthDateIso("   "))
        fun testNormalizeCorrectChoice_withString() {
            val fragment = SurveyWizardFragment()
            val result = fragment.normalizeCorrectChoice("correct")
            assertEquals(listOf("correct"), result)
        }

        @Test
        fun testNormalizeCorrectChoice_withList() {
            val fragment = SurveyWizardFragment()
            val list = listOf(
                mapOf("id" to "1"),
                mapOf("_id" to "2"),
                mapOf("text" to "3"),
                mapOf("other" to "4"),
                "plain_string"
            )
            val result = fragment.normalizeCorrectChoice(list)
            assertEquals(listOf("1", "2", "3", "plain_string"), result)
        }

        @Test
        fun testNormalizeCorrectChoice_withNull() {
            val fragment = SurveyWizardFragment()
            val result = fragment.normalizeCorrectChoice(null)
            assertEquals(emptyList<String>(), result)
        }

        @Test
        fun testNormalizeCorrectChoice_withOtherType() {
            val fragment = SurveyWizardFragment()
            val result = fragment.normalizeCorrectChoice(12345)
            assertEquals(listOf("12345"), result)
        }
    }
}
