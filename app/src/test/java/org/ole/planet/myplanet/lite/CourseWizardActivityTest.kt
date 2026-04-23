package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.DashboardCoursePageFragment.CourseItem
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
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
    }
}
