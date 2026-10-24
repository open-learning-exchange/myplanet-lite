package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.util.FakeSharedPreferences
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

class DashboardCoursePassStoreTest {

    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        SecurePreferencesProvider.injectedPreferences = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.resetForTesting()
    }

    @Test
    fun `known passes accumulate and are never taken away`() {
        val store = DashboardCoursePassStore(context, "learner")

        store.addPassedSteps("course1", listOf(2))
        store.addPassedSteps("course1", listOf(4))
        store.addPassedSteps("course1", emptyList())

        assertEquals(setOf(2, 4), store.getPassedSteps("course1"))
        assertTrue(store.getPassedSteps("course2").isEmpty())
    }

    @Test
    fun `passes are kept per learner`() {
        DashboardCoursePassStore(context, "learner").addPassedSteps("course1", listOf(2))

        assertTrue(DashboardCoursePassStore(context, "other").getPassedSteps("course1").isEmpty())
    }

    @Test
    fun `pending passes are removed once saved`() {
        val store = DashboardCoursePassStore(context, "learner")

        store.addPendingPass("course1", 2)
        store.addPendingPass("course1", 3)
        store.removePendingPass("course1", 2)

        assertEquals(setOf(3), store.getPendingPasses("course1"))
        store.removePendingPass("course1", 3)
        assertTrue(store.getPendingPasses("course1").isEmpty())
    }
}
