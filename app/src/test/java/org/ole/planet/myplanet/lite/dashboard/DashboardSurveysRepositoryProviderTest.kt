package org.ole.planet.myplanet.lite.dashboard

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class DashboardSurveysRepositoryProviderTest {

    @After
    fun tearDown() {
        DashboardSurveysRepositoryProvider.reset()
    }

    @Test
    fun testGetRepository_returnsSingleton() {
        val repo1 = DashboardSurveysRepositoryProvider.getRepository()
        val repo2 = DashboardSurveysRepositoryProvider.getRepository()

        assertNotNull(repo1)
        assertSame(repo1, repo2)
    }

    @Test
    fun testSetRepository_overridesInstance() {
        val mockRepo: DashboardSurveysRepository = mock()

        DashboardSurveysRepositoryProvider.setRepository(mockRepo)
        val retrievedRepo = DashboardSurveysRepositoryProvider.getRepository()

        assertSame(mockRepo, retrievedRepo)
    }

    @Test
    fun testReset_clearsInstance() {
        val repo1 = DashboardSurveysRepositoryProvider.getRepository()
        DashboardSurveysRepositoryProvider.reset()
        val repo2 = DashboardSurveysRepositoryProvider.getRepository()

        assertNotNull(repo1)
        assertNotNull(repo2)
        // Since we reset, a new instance should be created and returned, so they shouldn't be the same object
        assert(repo1 !== repo2)
    }
}
