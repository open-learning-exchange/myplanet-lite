package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class CourseWizardActivityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        SecurePreferencesProvider.injectedPreferences = mockPrefs
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        SecurePreferencesProvider.resetForTesting()
        unmockkAll()
    }

    @Test
    fun `activity finishes when launched without steps`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(CourseWizardActivity::class.java)
        val activity = controller.create().get()

        assertTrue(activity.isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun `activity doesn't finish when launched with steps`() = runTest(testDispatcher) {
        val courseId = "course1"
        val courseTitle = "Test Course"
        val startStep = 0
        val steps = listOf(
            CourseItem.LessonStep(
                title = "Step 1",
                description = "Desc 1",
                mediaTypes = emptyList(),
                resources = emptyList(),
                exam = null,
                survey = null
            )
        )

        val intent = Intent(context, CourseWizardActivity::class.java).apply {
            putExtra("extra_course_id", courseId)
            putExtra("extra_title", courseTitle)
            putExtra("extra_steps", ArrayList(steps))
            putExtra("extra_start_step", startStep)
        }

        val controller = Robolectric.buildActivity(CourseWizardActivity::class.java, intent)
        val activity = controller.create().get()

        assertFalse(activity.isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun `next shows snackbar and stays on current step when exam is incomplete`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(
            CourseWizardActivity::class.java,
            createCourseIntent(
                listOf(
                    lessonStep(
                        title = "Step 1",
                        exam = requiredSurveyDocument("exam1")
                    ),
                    lessonStep(title = "Step 2")
                )
            )
        )
        val activity = controller.create().start().resume().visible().get()
        advanceUntilIdle()

        activity.findViewById<MaterialButton>(R.id.courseWizardNext).performClick()
        ShadowLooper.idleMainLooper()

        assertEquals("Step 1", activity.findViewById<TextView>(R.id.courseWizardStepTitle).text.toString())
        assertTrue(
            activity.window.decorView.containsText(
                activity.getString(R.string.course_wizard_complete_required_step)
            )
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun `next shows snackbar and stays on current step when survey is incomplete`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(
            CourseWizardActivity::class.java,
            createCourseIntent(
                listOf(
                    lessonStep(
                        title = "Step 1",
                        survey = requiredSurveyDocument("survey1")
                    ),
                    lessonStep(title = "Step 2")
                )
            )
        )
        val activity = controller.create().start().resume().visible().get()
        advanceUntilIdle()

        activity.findViewById<MaterialButton>(R.id.courseWizardNext).performClick()
        ShadowLooper.idleMainLooper()

        assertEquals("Step 1", activity.findViewById<TextView>(R.id.courseWizardStepTitle).text.toString())
        assertTrue(
            activity.window.decorView.containsText(
                activity.getString(R.string.course_wizard_complete_required_step)
            )
        )
        controller.pause().stop().destroy()
    }

    private fun createCourseIntent(steps: List<CourseItem.LessonStep>): Intent {
        return Intent(context, CourseWizardActivity::class.java).apply {
            putExtra("extra_course_id", "course1")
            putExtra("extra_title", "Test Course")
            putExtra("extra_steps", ArrayList(steps))
            putExtra("extra_start_step", 0)
        }
    }

    private fun lessonStep(
        title: String,
        survey: SurveyDocument? = null,
        exam: SurveyDocument? = null
    ): CourseItem.LessonStep {
        return CourseItem.LessonStep(
            title = title,
            description = "$title description",
            mediaTypes = emptyList(),
            resources = emptyList(),
            exam = exam,
            survey = survey
        )
    }

    private fun requiredSurveyDocument(id: String): SurveyDocument {
        return SurveyDocument(
            id = id,
            name = "Required",
            questions = listOf(SurveyQuestion(body = "Question", type = "input"))
        )
    }

    private fun View.containsText(expected: String): Boolean {
        if (this is TextView && text?.toString() == expected) return true
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                if (getChildAt(index).containsText(expected)) return true
            }
        }
        return false
    }
}