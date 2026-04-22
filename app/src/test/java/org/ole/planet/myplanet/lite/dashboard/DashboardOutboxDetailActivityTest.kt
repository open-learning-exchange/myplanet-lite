package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.R
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardOutboxDetailActivityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: DashboardSurveyOutboxStore
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = DashboardSurveyOutboxStore(context)
        store.writableDatabase.delete("outbox_submissions", null, null)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        store.close()
    }

    @Test
    fun `activity finishes when launched without EXTRA_OUTBOX_ID`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(DashboardOutboxDetailActivity::class.java)
        val activity = controller.create().get()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `activity loads entry correctly when valid ID is provided`() = runTest(testDispatcher) {
        val submission = DashboardSurveySubmissionsRepository.SurveySubmission(
            parentId = "survey_1",
            parent = DashboardSurveySubmissionsRepository.SubmissionParent(
                id = "survey_1",
                rev = "1-abc",
                name = "Test Survey Name",
                description = "Description",
                questions = listOf(
                    DashboardSurveysRepository.SurveyQuestion(
                        body = "Question 1",
                        type = "text",
                        choices = emptyList()
                    )
                )
            ),
            user = DashboardSurveySubmissionsRepository.SubmissionUser(
                id = "user_1",
                name = "Test User",
                planetCode = "test",
                parentCode = "test"
            ),
            team = null,
            answers = listOf(
                DashboardSurveySubmissionsRepository.SubmissionAnswer(
                    value = "Answer 1"
                )
            ),
            status = "pending",
            startTime = 0L,
            lastUpdateTime = 0L,
            source = null,
            parentCode = null
        )

        val result = store.saveSubmission(
            submission = submission,
            surveyId = "survey_1",
            surveyName = "Test Survey Name",
            teamId = "team_1",
            teamName = "Test Team"
        )

        assertTrue("Failed to setup test data in store", result)

        val entries = store.getPendingForTeam("team_1")
        assertEquals(1, entries.size)
        val entryId = entries[0].id

        val intent = Intent(context, DashboardOutboxDetailActivity::class.java).apply {
            putExtra(DashboardOutboxDetailActivity.EXTRA_OUTBOX_ID, entryId)
        }

        val controller = Robolectric.buildActivity(DashboardOutboxDetailActivity::class.java, intent)
        val activity = controller.create().start().resume().get()



        val toolbar = activity.findViewById<MaterialToolbar>(R.id.outboxDetailToolbar)
        val startTime = System.currentTimeMillis()
        while(toolbar.title?.toString() != "Test Survey Name") {
            testDispatcher.scheduler.advanceUntilIdle()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(10)
            if (System.currentTimeMillis() - startTime > 2000) break
        }
        assertEquals("Test Survey Name", toolbar.title?.toString())

        val teamView = activity.findViewById<TextView>(R.id.outboxDetailTeam)
        assertEquals("Test Team", teamView.text.toString())
    }
}
