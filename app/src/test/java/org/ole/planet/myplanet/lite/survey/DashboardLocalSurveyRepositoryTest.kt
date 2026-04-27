package org.ole.planet.myplanet.lite.survey

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.ole.planet.myplanet.lite.dashboard.DashboardOfflineSurveyStore
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyOutboxStore
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionParent
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionUser
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SurveySubmission
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DashboardLocalSurveyRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: DashboardLocalSurveyRepository
    private lateinit var mockPrefs: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockPrefs = mock(SharedPreferences::class.java)
        SecurePreferencesProvider.injectedPreferences = mockPrefs
        DashboardOfflineSurveyStore.resetForTesting(context)
        DashboardSurveyOutboxStore.resetForTesting(context)
        repository = DashboardLocalSurveyRepository(context)
    }

    @After
    fun tearDown() {
        repository.close()
        DashboardOfflineSurveyStore.resetForTesting(context)
        DashboardSurveyOutboxStore.resetForTesting(context)
        SecurePreferencesProvider.resetForTesting()
    }

    private fun createSubmission() = SurveySubmission(
        parentId = "parent123",
        parent = SubmissionParent(id = "parent123", rev = "1", name = "Test Survey", description = "Test", questions = emptyList()),
        user = SubmissionUser(id = "user1", name = "Test User", planetCode = "planet", parentCode = "parent"),
        team = null,
        answers = emptyList(),
        status = "pending",
        startTime = System.currentTimeMillis(),
        lastUpdateTime = System.currentTimeMillis(),
        source = "test",
        parentCode = "code"
    )

    private fun createSurveyDocument() = SurveyDocument(
        id = "survey123",
        rev = "1",
        teamId = "team1",
        name = "Test Survey",
        description = "Test Description",
        passingPercentage = 50,
        sourceSurveyId = "source123",
        createdDate = "2023-01-01",
        questions = emptyList(),
        totalMarks = 100
    )

    @Test
    fun `saveSurvey success saves document to offline store`() = runTest {
        val document = createSurveyDocument()
        val success = repository.saveSurvey(document, fallbackTeamId = null)
        assertTrue(success)

        val ids = repository.getSavedSurveyIds()
        assertTrue(ids.contains("survey123"))

        val surveys = repository.getSavedSurveysForTeam("team1")
        assertEquals(1, surveys.size)
        assertEquals("survey123", surveys[0].id)

        val revisions = repository.getSavedSurveyRevisions()
        assertEquals("1", revisions["survey123"])
    }

    @Test
    fun `saveSubmission success saves to outbox store`() = runTest {
        val submission = createSubmission()
        val success = repository.saveSubmission(
            submission = submission,
            surveyId = "survey123",
            surveyName = "Test Survey",
            teamId = "team1",
            teamName = "Team A"
        )
        assertTrue(success)

        val pending = repository.getPendingForTeam("team1")
        assertEquals(1, pending.size)
        val entry = pending[0]
        assertEquals("survey123", entry.surveyId)
        assertEquals("team1", entry.teamId)

        val retrieved = repository.getEntry(entry.id)
        assertEquals("survey123", retrieved?.surveyId)
        assertEquals("test", retrieved?.submission?.source)
    }

    @Test
    fun `deleteEntry removes from outbox store`() = runTest {
        val submission = createSubmission()
        repository.saveSubmission(
            submission = submission,
            surveyId = "survey123",
            surveyName = "Test Survey",
            teamId = "team1",
            teamName = "Team A"
        )

        val pending = repository.getPendingForTeam("team1")
        val entry = pending[0]

        val deleted = repository.deleteEntry(entry.id)
        assertTrue(deleted)

        val afterDelete = repository.getPendingForTeam("team1")
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun `flushPendingSurveyOutbox exits early if offline`() = runTest {
        // Robolectric network is offline by default unless explicitly configured otherwise
        val submission = createSubmission()
        repository.saveSubmission(
            submission = submission,
            surveyId = "survey123",
            surveyName = "Test Survey",
            teamId = "team1",
            teamName = "Team A"
        )

        repository.flushPendingSurveyOutbox()

        val pending = repository.getPendingForTeam("team1")
        assertEquals(1, pending.size) // Not flushed because offline
    }
}
