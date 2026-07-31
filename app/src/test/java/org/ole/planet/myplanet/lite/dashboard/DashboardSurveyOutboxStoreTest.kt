package org.ole.planet.myplanet.lite.dashboard

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionParent
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionTeam
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionUser
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SurveySubmission
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardSurveyOutboxStoreTest {

    private lateinit var store: DashboardSurveyOutboxStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDispatcher = UnconfinedTestDispatcher()
        store = DashboardSurveyOutboxStore.getInstance(context, testDispatcher, testDispatcher)
        store.writableDatabase.delete("outbox_submissions", null, null)
    }

    @After
    fun teardown() {
        DashboardSurveyOutboxStore.resetForTesting(ApplicationProvider.getApplicationContext())
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
    fun saveSubmission_savesSuccessfully() = runBlocking {
        val submission = createSubmission()
        val saved = store.saveSubmission(submission, "survey1", "Test Survey", "team1", "Team A")

        assertTrue(saved)
        val pending = store.getPendingForTeam("team1")
        assertEquals(1, pending.size)
        assertEquals("survey1", pending[0].surveyId)
        assertEquals("1", pending[0].surveyRev)
        assertEquals("team1", pending[0].teamId)
        assertEquals("Team A", pending[0].teamName)
        assertEquals("Test Survey", pending[0].surveyName)
        assertEquals("parent123", pending[0].submission.parentId)
        assertEquals("parent123", pending[0].submission.parent.id)
        assertEquals("1", pending[0].submission.parent.rev)
    }

    @Test
    fun getPendingForTeam_returnsDescendingOrder() = runBlocking {
        val submission1 = createSubmission()
        val submission2 = createSubmission()

        store.saveSubmission(submission1, "survey1", "Survey 1", "team1", "Team A")
        // Delay to ensure created_at is different
        kotlinx.coroutines.delay(10.milliseconds)
        store.saveSubmission(submission2, "survey2", "Survey 2", "team1", "Team A")

        val pending = store.getPendingForTeam("team1")
        assertEquals(2, pending.size)
        // Since order is DESC, the second submission (latest) should be first
        assertEquals("survey2", pending[0].surveyId)
        assertEquals("survey1", pending[1].surveyId)
    }

    @Test
    fun getPendingForTeam_ignoresInvalidPayload() = runBlocking {
        val submission = createSubmission()
        store.saveSubmission(submission, "survey1", "Test Survey", "team1", "Team A")

        // Insert a row with an invalid payload
        val values = ContentValues().apply {
            put("survey_id", "invalid_survey")
            put("team_id", "team1")
            put("team_name", "Team A")
            put("survey_name", "Invalid Survey")
            put("created_at", System.currentTimeMillis())
            put("payload", "invalid json")
        }
        store.writableDatabase.insert("outbox_submissions", null, values)

        val pending = store.getPendingForTeam("team1")
        // It should only return the valid entry
        assertEquals(1, pending.size)
        assertEquals("survey1", pending[0].surveyId)
    }

    @Test
    fun getPendingForTeam_filtersByTeam() = runBlocking {
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
    fun getEntry_returnsCorrectEntry() = runBlocking {
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
    fun deleteEntry_removesEntryAndReturnsTrue() = runBlocking {
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
    fun deleteEntry_withMultipleEntries_removesOnlySpecifiedEntry() = runBlocking {
        val submission1 = createSubmission()
        val submission2 = createSubmission()

        store.saveSubmission(submission1, "survey1", "Test Survey 1", "team1", "Team A")
        store.saveSubmission(submission2, "survey2", "Test Survey 2", "team2", "Team B")

        val pendingAll = store.getPendingForTeam(null)
        assertEquals(2, pendingAll.size)

        val entryIdToDelete = pendingAll.find { it.surveyId == "survey1" }?.id
        assertNotNull(entryIdToDelete)

        val deleted = store.deleteEntry(entryIdToDelete!!)
        assertTrue(deleted)

        val pendingAfterDelete = store.getPendingForTeam(null)
        assertEquals(1, pendingAfterDelete.size)
        assertEquals("survey2", pendingAfterDelete[0].surveyId)

        val deletedEntry = store.getEntry(entryIdToDelete)
        assertNull(deletedEntry)
    }

    @Test
    fun deleteEntries_removesMultipleEntries() = runBlocking {
        val submission1 = createSubmission()
        val submission2 = createSubmission()
        val submission3 = createSubmission()

        store.saveSubmission(submission1, "survey1", "Test Survey 1", "team1", "Team A")
        store.saveSubmission(submission2, "survey2", "Test Survey 2", "team1", "Team A")
        store.saveSubmission(submission3, "survey3", "Test Survey 3", "team1", "Team A")

        val pending = store.getPendingForTeam("team1")
        assertEquals(3, pending.size)

        val idsToDelete = pending.take(2).map { it.id }
        val deleted = store.deleteEntries(idsToDelete)
        assertTrue(deleted)

        val pendingAfter = store.getPendingForTeam("team1")
        assertEquals(1, pendingAfter.size)
        assertEquals(pending.last().surveyId, pendingAfter[0].surveyId)
    }

    @Test
    fun deleteEntries_benchmark() = runBlocking {
        val submission = createSubmission()
        for (i in 1..2000) {
            store.saveSubmission(submission, "survey$i", "Test Survey", "team1", "Team A")
        }
        val pending = store.getPendingForTeam("team1")
        assertEquals(2000, pending.size)
        val idsToDelete = pending.map { it.id }

        val time = kotlin.system.measureTimeMillis {
            store.deleteEntries(idsToDelete)
        }
        println("BENCHMARK: deleteEntries took $time ms for 2000 entries")
    }

    @Test
    fun saveSubmission_recoversSurveyMetadataFromPayload() = runBlocking {
        val submission = createSubmission()
        val saved = store.saveSubmission(submission, null, null, null, null)
        assertTrue(saved)

        val pending = store.getPendingForTeam(null)
        assertEquals(1, pending.size)
        assertEquals("parent123", pending[0].surveyId)
        assertEquals("1", pending[0].surveyRev)
        assertNull(pending[0].teamId)
    }

    @Test
    fun saveSubmission_returnsFalseOnInsertFailure() = runBlocking {
        val submission = createSubmission()
        val spyDb = spy(store.writableDatabase)
        doReturn(-1L).`when`(spyDb).insert(any(), anyOrNull(), any())
        val spyStore = spy(store)
        doReturn(spyDb).`when`(spyStore).writableDatabase

        val saved = spyStore.saveSubmission(submission, "survey1", "Test Survey", "team1", "Team A")
        assertFalse(saved)
    }
}
