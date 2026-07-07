package org.ole.planet.myplanet.lite.dashboard

object DashboardTeamsDependencies {
    @Volatile
    private var repositoryOverride: DashboardTeamsRepository? = null

    private var cachedRepository: DashboardTeamsRepository? = null

    fun provideRepository(): DashboardTeamsRepository {
        return repositoryOverride ?: synchronized(this) {
            cachedRepository ?: DashboardTeamsRepository().also { cachedRepository = it }
        }
    }

    @androidx.annotation.VisibleForTesting
    fun overrideRepository(repository: DashboardTeamsRepository?) {
        repositoryOverride = repository
    }

    @androidx.annotation.VisibleForTesting
    fun resetForTesting() {
        repositoryOverride = null
        cachedRepository = null
    }
}
