package org.ole.planet.myplanet.lite.survey

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardOfflineSurveyStore
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyOutboxStore
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyOutboxStore.OutboxEntry
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SurveySubmission
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.util.NetworkUtils

class DashboardLocalSurveyRepository(private val context: Context) : Closeable {
    private val offlineStore get() = DashboardOfflineSurveyStore.getInstance(context)
    private val outboxStore get() = DashboardSurveyOutboxStore.getInstance(context)
    private val submissionsRepoDelegate = lazy { DashboardSurveySubmissionsRepository() }
    private val submissionsRepo get() = submissionsRepoDelegate.value

    suspend fun getSavedSurveyIds(): Set<String> = offlineStore.getSavedSurveyIds()

    suspend fun getSavedSurveysForTeam(teamId: String): List<SurveyDocument> = offlineStore.getSavedSurveysForTeam(teamId)

    suspend fun getSavedSurveyRevisions(): Map<String, String?> = offlineStore.getSavedSurveyRevisions()

    suspend fun saveSurvey(document: SurveyDocument, fallbackTeamId: String?): Boolean = offlineStore.saveSurvey(document, fallbackTeamId)

    suspend fun getPendingForTeam(teamId: String?): List<OutboxEntry> = outboxStore.getPendingForTeam(teamId)

    suspend fun getEntry(id: Long): OutboxEntry? = outboxStore.getEntry(id)

    suspend fun saveSubmission(
        submission: SurveySubmission,
        surveyId: String?,
        surveyName: String?,
        teamId: String?,
        teamName: String?
    ): Boolean = outboxStore.saveSubmission(submission, surveyId, surveyName, teamId, teamName)

    suspend fun deleteEntry(id: Long): Boolean = outboxStore.deleteEntry(id)

    suspend fun flushPendingSurveyOutbox(typeFilter: String? = null) {
        if (!NetworkUtils.isDeviceOnline(context)) return
        val baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        val base = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return
        val creds = ProfileCredentialsStore.getStoredCredentials(context) ?: return

        val authService = AuthDependencies.provideAuthService(context.applicationContext, base)
        val sessionCookie = withContext(Dispatchers.IO) { authService.getStoredToken() }

        val pendingEntries = outboxStore.getPendingForTeam(null)
            .filter { typeFilter == null || it.submission.type.equals(typeFilter, ignoreCase = true) }
            .sortedBy { it.createdAt }

        if (pendingEntries.isEmpty()) return

        coroutineScope {
            val deferredResults = pendingEntries.map { entry ->
                async(Dispatchers.IO) {
                    val result = submissionsRepo.submitSurvey(base, creds, sessionCookie, entry.submission)
                    entry to result
                }
            }
            val successfulIds = mutableListOf<Long>()
            deferredResults.forEach { deferred ->
                val (entry, result) = deferred.await()
                if (result.isSuccess) {
                    successfulIds.add(entry.id)
                }
            }
            if (successfulIds.isNotEmpty()) {
                outboxStore.deleteEntries(successfulIds)
            }
        }
    }

    private fun isDeviceOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun close() {
        // Managed by singletons
    }
}
