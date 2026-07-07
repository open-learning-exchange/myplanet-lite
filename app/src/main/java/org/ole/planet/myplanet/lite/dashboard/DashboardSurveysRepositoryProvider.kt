package org.ole.planet.myplanet.lite.dashboard

import androidx.annotation.VisibleForTesting
import okhttp3.OkHttpClient

object DashboardSurveysRepositoryProvider {
    @Volatile
    private var instance: DashboardSurveysRepository? = null

    private val client: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    fun getRepository(): DashboardSurveysRepository {
        return instance ?: synchronized(this) {
            instance ?: DashboardSurveysRepository(client = client).also { instance = it }
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
