package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardCourseDetailsBottomSheetTest {

    private lateinit var course: CourseItem

    @Before
    fun setup() {
        course = CourseItem(
            id = "course1",
            title = "Test Course",
            description = "Test description",
            coverPath = null,
            steps = listOf(
                CourseItem.LessonStep(
                    title = "Step 1",
                    description = "Step 1 desc",
                    mediaTypes = listOf("video", "pdf")
                )
            ),
            rating = 4.5,
            progressPercent = 50,
            currentStep = 0
        )
    }

    @Test
    fun testCourseDetailsDisplayedCorrectly_WhenEnrolled() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().start().resume().get()
        val fragment = DashboardCourseDetailsBottomSheet()

        val args = Bundle().apply {
            putString("arg_title", course.title)
            putString("arg_course_id", course.id)
            putString("arg_description", course.description)
            putStringArrayList("arg_step_titles", ArrayList(course.steps.map { it.title }))
            putStringArrayList("arg_step_media_types", ArrayList(course.steps.map { it.mediaTypes.joinToString(",") }))
            course.currentStep?.let { putInt("arg_current_step", it) }
            putBoolean("arg_is_enrolled", true)
        }
        fragment.arguments = args
        fragment.show(activity.supportFragmentManager, "test")

        // Let Robolectric execute the transaction
        activity.supportFragmentManager.executePendingTransactions()

        val view = fragment.view
        assertNotNull("Fragment view should not be null", view)
        view!!

        val titleView = view.findViewById<TextView>(R.id.dashboardCourseDetailsTitle)
        assertNotNull(titleView)
        val expectedTitle = fragment.getString(R.string.dashboard_course_lessons_title, course.title)
        assertEquals(expectedTitle, titleView.text)

        val descriptionView = view.findViewById<TextView>(R.id.dashboardCourseDetailsDescription)
        assertNotNull(descriptionView)
        assertTrue(descriptionView.text.toString().contains("Test description"))

        val stepsContainer = view.findViewById<LinearLayout>(R.id.dashboardCourseDetailsStepsContainer)
        assertNotNull(stepsContainer)
        assertEquals(1, stepsContainer.childCount)

        val ctaButton = view.findViewById<TextView>(R.id.dashboardCourseDetailsCta)
        assertNotNull(ctaButton)
        assertEquals(fragment.getString(R.string.dashboard_course_lessons_open_course), ctaButton.text)

        val leaveButton = view.findViewById<View>(R.id.dashboardCourseDetailsLeave)
        assertNotNull(leaveButton)
        assertEquals(View.VISIBLE, leaveButton.visibility)
    }

    @Test
    fun testCourseDetailsDisplayedCorrectly_WhenNotEnrolled() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().start().resume().get()
        val fragment = DashboardCourseDetailsBottomSheet()

        val args = Bundle().apply {
            putString("arg_title", course.title)
            putString("arg_course_id", course.id)
            putString("arg_description", course.description)
            putStringArrayList("arg_step_titles", ArrayList(course.steps.map { it.title }))
            putStringArrayList("arg_step_media_types", ArrayList(course.steps.map { it.mediaTypes.joinToString(",") }))
            course.currentStep?.let { putInt("arg_current_step", it) }
            putBoolean("arg_is_enrolled", false)
        }
        fragment.arguments = args
        fragment.show(activity.supportFragmentManager, "test")

        activity.supportFragmentManager.executePendingTransactions()

        val view = fragment.view
        assertNotNull("Fragment view should not be null", view)
        view!!

        val ctaButton = view.findViewById<TextView>(R.id.dashboardCourseDetailsCta)
        assertNotNull(ctaButton)
        assertEquals(fragment.getString(R.string.dashboard_course_lessons_join_course), ctaButton.text)

        val leaveButton = view.findViewById<View>(R.id.dashboardCourseDetailsLeave)
        assertNotNull(leaveButton)
        assertEquals(View.GONE, leaveButton.visibility)
    }

    @Test
    fun testEmptyDescriptionAndSteps() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().start().resume().get()
        val fragment = DashboardCourseDetailsBottomSheet()

        val emptyCourse = course.copy(description = "", steps = emptyList())

        val args = Bundle().apply {
            putString("arg_title", emptyCourse.title)
            putString("arg_course_id", emptyCourse.id)
            putString("arg_description", emptyCourse.description)
            putStringArrayList("arg_step_titles", ArrayList(emptyCourse.steps.map { it.title }))
            putStringArrayList("arg_step_media_types", ArrayList(emptyCourse.steps.map { it.mediaTypes.joinToString(",") }))
            emptyCourse.currentStep?.let { putInt("arg_current_step", it) }
            putBoolean("arg_is_enrolled", true)
        }
        fragment.arguments = args
        fragment.show(activity.supportFragmentManager, "test")

        activity.supportFragmentManager.executePendingTransactions()

        val view = fragment.view
        assertNotNull("Fragment view should not be null", view)
        view!!

        val descriptionView = view.findViewById<TextView>(R.id.dashboardCourseDetailsDescription)
        assertEquals(View.GONE, descriptionView.visibility)

        val stepsContainer = view.findViewById<LinearLayout>(R.id.dashboardCourseDetailsStepsContainer)
        assertEquals(0, stepsContainer.childCount)
    }

    @Test
    fun testShowCompanionMethod() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().start().resume().get()
        var joinInvoked = false
        var leaveInvoked = false
        var openInvoked = false

        DashboardCourseDetailsBottomSheet.show(
            activity.supportFragmentManager,
            course,
            isEnrolled = true,
            onJoinCourse = { joinInvoked = true },
            onLeaveCourse = { leaveInvoked = true },
            onOpenCourse = { openInvoked = true }
        )

        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentByTag("course_details") as DashboardCourseDetailsBottomSheet
        assertNotNull(fragment)

        val view = fragment.view
        assertNotNull(view)
        view!!

        val ctaButton = view.findViewById<TextView>(R.id.dashboardCourseDetailsCta)
        ctaButton.performClick()
        assertTrue(openInvoked)

        // Testing leave button requires simulating a dialog interaction which is trickier,
        // but we know the button triggers a MaterialAlertDialogBuilder
    }
}