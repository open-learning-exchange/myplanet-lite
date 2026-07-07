package org.ole.planet.myplanet.lite.dashboard

import androidx.annotation.VisibleForTesting

object DashboardSurveysRepositoryProvider {
    @Volatile
    private var instance: DashboardSurveysRepository? = null

    fun getRepository(): DashboardSurveysRepository {
        return instance ?: synchronized(this) {
            instance ?: DashboardSurveysRepository().also { instance = it }
        }
    }

    @VisibleForTesting
    fun setRepository(repository: DashboardSurveysRepository) {
        instance = repository
    }

    @VisibleForTesting
    fun reset() {
        instance = null
    }
}
