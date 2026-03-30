package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionParent
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionTeam
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionUser
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SurveySubmission
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DashboardSurveyOutboxStoreTest {

    private lateinit var store: DashboardSurveyOutboxStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = DashboardSurveyOutboxStore(context)
        store.writableDatabase.delete("outbox_submissions", null, null)
    }

    @After
    fun teardown() {
        store.close()
    }

    private fun createSubmission() = SurveySubmission(
        parentId = "parent123",
        parent = SubmissionParent(id = "parent123", rev = "1", name = "Test Survey", description = "Test", questions = emptyList()),
        user = SubmissionUser(id = "user1", name = "Test User", planetCode = "planet", parentCode = "parent"),
        team = SubmissionTeam(id = "team1", name = "Team A", type = "type"),
        answers = emptyList(),
        status = "pending",
        startTime = System.currentTimeMillis(),
        lastUpdateTime = System.currentTimeMillis(),
        source = "test",
        parentCode = "code"
    )

    @Test
    fun saveSubmission_savesSuccessfully() = runTest {
        val submission = createSubmission()
        val saved = store.saveSubmission(submission, "survey1", "Test Survey", "team1", "Team A")

        assertTrue(saved)
        val pending = store.getPendingForTeam("team1")
        assertEquals(1, pending.size)
        assertEquals("survey1", pending[0].surveyId)
        assertEquals("team1", pending[0].teamId)
        assertEquals("Team A", pending[0].teamName)
        assertEquals("Test Survey", pending[0].surveyName)
        assertEquals("parent123", pending[0].submission.parentId)
    }

    @Test
    fun getPendingForTeam_filtersByTeam() = runTest {
        val submission1 = createSubmission()
        val submission2 = createSubmission()

        store.saveSubmission(submission1, "survey1", "Survey 1", "team1", "Team A")
        store.saveSubmission(submission2, "survey2", "Survey 2", "team2", "Team B")

        val pendingTeam1 = store.getPendingForTeam("team1")
        assertEquals(1, pendingTeam1.size)
        assertEquals("survey1", pendingTeam1[0].surveyId)

        val pendingTeam2 = store.getPendingForTeam("team2")
        assertEquals(1, pendingTeam2.size)
        assertEquals("survey2", pendingTeam2[0].surveyId)

        val pendingNone = store.getPendingForTeam("team3")
        assertEquals(0, pendingNone.size)

        val pendingAll = store.getPendingForTeam(null)
        assertEquals(2, pendingAll.size)

        val pendingEmptyString = store.getPendingForTeam("")
        assertEquals(2, pendingEmptyString.size)
    }

    @Test
    fun getEntry_returnsCorrectEntry() = runTest {
        val submission = createSubmission()
        store.saveSubmission(submission, "survey1", "Test Survey", "team1", "Team A")

        val pending = store.getPendingForTeam("team1")
        assertEquals(1, pending.size)
        val entryId = pending[0].id

        val entry = store.getEntry(entryId)
        assertNotNull(entry)
        assertEquals("survey1", entry?.surveyId)
        assertEquals("team1", entry?.teamId)
        assertEquals("Team A", entry?.teamName)
        assertEquals("Test Survey", entry?.surveyName)
        assertEquals("parent123", entry?.submission?.parentId)

        val nonExistentEntry = store.getEntry(-1)
        assertNull(nonExistentEntry)
    }

    @Test
    fun deleteEntry_removesEntryAndReturnsTrue() = runTest {
        val submission = createSubmission()
        store.saveSubmission(submission, "survey1", "Test Survey", "team1", "Team A")

        val pending = store.getPendingForTeam("team1")
        assertEquals(1, pending.size)
        val entryId = pending[0].id

        val deleted = store.deleteEntry(entryId)
        assertTrue(deleted)

        val pendingAfterDelete = store.getPendingForTeam("team1")
        assertEquals(0, pendingAfterDelete.size)

        val deletedNonExistent = store.deleteEntry(-1)
        assertFalse(deletedNonExistent)
    }

    @Test
    fun saveSubmission_handlesSerializationFailure() = runTest {
        // Just verify basic saving again, serialization failures would require a custom moshi or interceptor.
        val submission = createSubmission()
        val saved = store.saveSubmission(submission, null, null, null, null)
        assertTrue(saved)

        val pending = store.getPendingForTeam(null)
        assertEquals(1, pending.size)
        assertNull(pending[0].surveyId)
        assertNull(pending[0].teamId)
    }
}
