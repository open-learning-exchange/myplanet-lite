package org.ole.planet.myplanet.lite.dashboard

object DashboardSurveysRepositoryFactory {
    @Volatile
    private var instance: DashboardSurveysRepository? = null

    fun getRepository(): DashboardSurveysRepository {
        return instance ?: synchronized(this) {
            instance ?: DashboardSurveysRepository().also { instance = it }
        }
    }

    fun setRepository(repository: DashboardSurveysRepository) {
        instance = repository
    }

    fun reset() {
        instance = null
    }
}
