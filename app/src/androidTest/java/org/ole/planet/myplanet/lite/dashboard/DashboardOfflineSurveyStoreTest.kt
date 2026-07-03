package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardOfflineSurveyStoreTest {

    private lateinit var store: DashboardOfflineSurveyStore
    private lateinit var context: Context
    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        // Ensure clean state before tests
        context.deleteDatabase("dashboard_offline_surveys.db")
        context.getSharedPreferences("dashboard_offline_surveys", Context.MODE_PRIVATE).edit().clear().commit()

        // Pass both context and explicit moshi instance as mentioned in prompt
        store = DashboardOfflineSurveyStore(
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(),
            moshi = moshi
        )
    }

    @After
    fun teardown() {
        store.close()
        context.deleteDatabase("dashboard_offline_surveys.db")
        context.getSharedPreferences("dashboard_offline_surveys", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testSaveSurvey_invalidId() = runTest {
        val document = SurveyDocument(id = "   ", rev = "1-abc", teamId = "team1")
        val result = store.saveSurvey(document, null)
        assertFalse(result)
    }

    @Test
    fun testSaveSurvey_validId() = runTest {
        val document = SurveyDocument(id = "survey1", rev = "1-abc", teamId = "team1")
        val result = store.saveSurvey(document, null)
        assertTrue(result)

        val ids = store.getSavedSurveyIds()
        assertTrue(ids.contains("survey1"))
    }

    @Test
    fun testSaveSurvey_fallbackTeamId() = runTest {
        val document = SurveyDocument(id = "survey1", rev = "1-abc", teamId = null)
        val result = store.saveSurvey(document, "fallbackTeam")
        assertTrue(result)

        val surveys = store.getSavedSurveysForTeam("fallbackTeam")
        assertEquals(1, surveys.size)
        assertEquals("survey1", surveys[0].id)
    }

    @Test
    fun testGetSavedSurveyIds() = runTest {
        val doc1 = SurveyDocument(id = "s1", rev = "1-a", teamId = "t1")
        val doc2 = SurveyDocument(id = "s2", rev = "1-b", teamId = "t2")

        store.saveSurvey(doc1, null)
        store.saveSurvey(doc2, null)

        val ids = store.getSavedSurveyIds()
        assertEquals(2, ids.size)
        assertTrue(ids.contains("s1"))
        assertTrue(ids.contains("s2"))
    }

    @Test
    fun testGetSavedSurveysForTeam() = runTest {
        val doc1 = SurveyDocument(id = "s1", rev = "1-a", teamId = "t1")
        val doc2 = SurveyDocument(id = "s2", rev = "1-b", teamId = "t2")
        val doc3 = SurveyDocument(id = "s3", rev = "1-c", teamId = "t1")

        store.saveSurvey(doc1, null)
        store.saveSurvey(doc2, null)
        store.saveSurvey(doc3, null)

        val surveysT1 = store.getSavedSurveysForTeam("t1")
        assertEquals(2, surveysT1.size)
        val ids = surveysT1.map { it.id }.toSet()
        assertTrue(ids.contains("s1"))
        assertTrue(ids.contains("s3"))

        val surveysT2 = store.getSavedSurveysForTeam("t2")
        assertEquals(1, surveysT2.size)
        assertEquals("s2", surveysT2[0].id)

        val surveysT3 = store.getSavedSurveysForTeam("t3")
        assertTrue(surveysT3.isEmpty())
    }

    @Test
    fun testGetSavedSurveyRevisions() = runTest {
        val doc1 = SurveyDocument(id = "s1", rev = "1-a", teamId = "t1")
        val doc2 = SurveyDocument(id = "s2", rev = "1-b", teamId = "t2")
        val doc3 = SurveyDocument(id = "s3", rev = null, teamId = "t1") // null rev

        store.saveSurvey(doc1, null)
        store.saveSurvey(doc2, null)
        store.saveSurvey(doc3, null)

        val revisions = store.getSavedSurveyRevisions()
        assertEquals(3, revisions.size)
        assertEquals("1-a", revisions["s1"])
        assertEquals("1-b", revisions["s2"])
        assertEquals(null, revisions["s3"])
    }
}
