package org.ole.planet.myplanet.lite.dashboard

import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class DashboardTeamsDependenciesTest {

    @Before
    fun setup() {
        DashboardTeamsDependencies.resetForTesting()
    }

    @After
    fun tearDown() {
        DashboardTeamsDependencies.resetForTesting()
    }

    @Test
    fun provideRepository_returnsSingleton() {
        val repo1 = DashboardTeamsDependencies.provideRepository()
        val repo2 = DashboardTeamsDependencies.provideRepository()

        assertSame(repo1, repo2)
    }

    @Test
    fun overrideRepository_overridesProvidedRepository() {
        val originalRepo = DashboardTeamsDependencies.provideRepository()
        val mockRepo = mock(DashboardTeamsRepository::class.java)

        DashboardTeamsDependencies.overrideRepository(mockRepo)

        val newRepo = DashboardTeamsDependencies.provideRepository()

        assertNotSame(originalRepo, newRepo)
        assertSame(mockRepo, newRepo)
    }

    @Test
    fun overrideRepository_null_clearsOverride() {
        val originalRepo = DashboardTeamsDependencies.provideRepository()
        val mockRepo = mock(DashboardTeamsRepository::class.java)

        DashboardTeamsDependencies.overrideRepository(mockRepo)
        DashboardTeamsDependencies.overrideRepository(null)

        val restoredRepo = DashboardTeamsDependencies.provideRepository()

        assertSame(originalRepo, restoredRepo)
        assertNotSame(mockRepo, restoredRepo)
    }

    @Test
    fun resetForTesting_clearsCachedInstanceAndOverride() {
        val originalRepo = DashboardTeamsDependencies.provideRepository()
        val mockRepo = mock(DashboardTeamsRepository::class.java)

        DashboardTeamsDependencies.overrideRepository(mockRepo)
        DashboardTeamsDependencies.resetForTesting()

        val freshRepo = DashboardTeamsDependencies.provideRepository()

        assertNotSame(originalRepo, freshRepo)
        assertNotSame(mockRepo, freshRepo)
    }
}
