package org.ole.planet.myplanet.lite.dashboard

object DashboardTeamsRepositoryProvider {
    @Volatile
    private var repositoryOverride: DashboardTeamsRepository? = null

    fun provideRepository(): DashboardTeamsRepository {
        return repositoryOverride ?: DashboardTeamsRepository()
    }

    @androidx.annotation.VisibleForTesting
    fun overrideRepository(repository: DashboardTeamsRepository?) {
        repositoryOverride = repository
    }

    @androidx.annotation.VisibleForTesting
    fun resetForTesting() {
        repositoryOverride = null
    }
}
