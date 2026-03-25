package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardSurveyStatusStoreTest {

    private lateinit var context: Context
    private val PREFS_NAME = "dashboard_survey_statuses"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Ensure starting with clean state
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    @After
    fun tearDown() {
        // Clean up after test
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun getStatus_returnsNullForUnknownId() {
        val store = DashboardSurveyStatusStore(context)
        assertNull(store.getStatus("unknown_id"))
    }

    @Test
    fun getStatus_returnsNullForNullId() {
        val store = DashboardSurveyStatusStore(context)
        assertNull(store.getStatus(null))
    }

    @Test
    fun getStatus_returnsNullForBlankId() {
        val store = DashboardSurveyStatusStore(context)
        assertNull(store.getStatus("   "))
    }

    @Test
    fun ensureNewDefaults_setsNewStatusForUnknownIds() {
        val store = DashboardSurveyStatusStore(context)
        store.ensureNewDefaults(listOf("id1", "id2"))

        assertEquals(SurveyStatus.NEW, store.getStatus("id1"))
        assertEquals(SurveyStatus.NEW, store.getStatus("id2"))
    }

    @Test
    fun ensureNewDefaults_doesNotOverwriteExistingStatus() {
        val store = DashboardSurveyStatusStore(context)
        store.ensureNewDefaults(listOf("id1"))
        store.markViewed("id1")

        // Try setting defaults again
        store.ensureNewDefaults(listOf("id1", "id2"))

        assertEquals(SurveyStatus.VIEWED, store.getStatus("id1"))
        assertEquals(SurveyStatus.NEW, store.getStatus("id2"))
    }

    @Test
    fun ensureNewDefaults_handlesEmptyList() {
        val store = DashboardSurveyStatusStore(context)
        store.ensureNewDefaults(emptyList())
        // Should not crash, nothing to verify
    }

    @Test
    fun ensureNewDefaults_ignoresNullIds() {
        val store = DashboardSurveyStatusStore(context)
        store.ensureNewDefaults(listOf("id1", null))
        assertEquals(SurveyStatus.NEW, store.getStatus("id1"))
        assertNull(store.getStatus(null))
    }

    @Test
    fun markViewed_updatesStatusToViewed() {
        val store = DashboardSurveyStatusStore(context)
        store.markViewed("id1")
        assertEquals(SurveyStatus.VIEWED, store.getStatus("id1"))
    }

    @Test
    fun markCompleted_updatesStatusToCompleted() {
        val store = DashboardSurveyStatusStore(context)
        store.markCompleted("id1")
        assertEquals(SurveyStatus.COMPLETED, store.getStatus("id1"))
    }

    @Test
    fun withUsername_namespacesCorrectly() {
        val storeUserA = DashboardSurveyStatusStore(context, "userA")
        val storeUserB = DashboardSurveyStatusStore(context, "userB")
        val storeNoUser = DashboardSurveyStatusStore(context)

        storeUserA.markViewed("survey1")
        storeUserB.markCompleted("survey1")
        storeNoUser.ensureNewDefaults(listOf("survey1"))

        assertEquals(SurveyStatus.VIEWED, storeUserA.getStatus("survey1"))
        assertEquals(SurveyStatus.COMPLETED, storeUserB.getStatus("survey1"))
        assertEquals(SurveyStatus.NEW, storeNoUser.getStatus("survey1"))
    }

    @Test
    fun blankUsername_treatedAsNoUsername() {
        val storeBlankUser = DashboardSurveyStatusStore(context, "   ")
        val storeNoUser = DashboardSurveyStatusStore(context)

        storeBlankUser.markViewed("survey1")

        assertEquals(SurveyStatus.VIEWED, storeNoUser.getStatus("survey1"))
    }
}
