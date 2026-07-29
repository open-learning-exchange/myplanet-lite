package org.ole.planet.myplanet.lite.survey

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.NetworkUtils


enum class SubmitOutcome {
    SUBMITTED_ONLINE,
    QUEUED_OFFLINE,
    FAILED
}

enum class ResendOutcome {
    SUCCESS,
    REVISION_MISMATCH,
    REVISION_UNVERIFIABLE,
    MISSING_SERVER,
    FAILED,
    OFFLINE
}

class DashboardLocalSurveyRepository(private val context: Context) : Closeable {
    private val offlineStore get() = DashboardOfflineSurveyStore.getInstance(context)
    private val outboxStore get() = DashboardSurveyOutboxStore.getInstance(context)
    private val submissionsRepoDelegate = lazy { DashboardSurveySubmissionsRepository() }
    private val submissionsRepo get() = submissionsRepoDelegate.value

    private val httpClient get() = AuthDependencies.client


    suspend fun getSavedSurveyIds(): Set<String> = offlineStore.getSavedSurveyIds()

    suspend fun getSavedSurveysForTeam(teamId: String): List<SurveyDocument> = offlineStore.getSavedSurveysForTeam(teamId)

    suspend fun getSavedSurveyRevisions(): Map<String, String?> = offlineStore.getSavedSurveyRevisions()

    suspend fun saveSurvey(document: SurveyDocument, fallbackTeamId: String?): Boolean = offlineStore.saveSurvey(document, fallbackTeamId)

    suspend fun getPendingForTeam(teamId: String?): List<OutboxEntry> = outboxStore.getPendingForTeam(teamId)

    suspend fun getEntry(id: Long): OutboxEntry? = outboxStore.getEntry(id)


    suspend fun submitOrQueueSubmission(
        base: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        submission: SurveySubmission,
        surveyId: String?,
        surveyName: String?,
        teamId: String?,
        teamName: String?,
        baseUrlOverride: String?
    ): SubmitOutcome {
        val isOnline = isDeviceOnline()
        if (!isOnline) {
            if (baseUrlOverride != null) return SubmitOutcome.FAILED
            val saved = saveSubmission(submission, surveyId, surveyName, teamId, teamName)
            return if (saved) SubmitOutcome.QUEUED_OFFLINE else SubmitOutcome.FAILED
        }

        val result = submissionsRepo.submitSurvey(base, credentials, sessionCookie, submission)
        if (result.isSuccess) {
            return SubmitOutcome.SUBMITTED_ONLINE
        } else {
            if (baseUrlOverride != null) return SubmitOutcome.FAILED
            val saved = saveSubmission(submission, surveyId, surveyName, teamId, teamName)
            return if (saved) SubmitOutcome.QUEUED_OFFLINE else SubmitOutcome.FAILED
        }
    }

    suspend fun saveSubmission(
        submission: SurveySubmission,
        surveyId: String?,
        surveyName: String?,
        teamId: String?,
        teamName: String?
    ): Boolean = outboxStore.saveSubmission(submission, surveyId, surveyName, teamId, teamName)

    suspend fun deleteEntry(id: Long): Boolean = outboxStore.deleteEntry(id)

    suspend fun flushPendingSurveyOutbox(typeFilter: String? = null) {
        val isOnline = NetworkUtils.isDeviceOnline(context)
        if (!isOnline) return

        val (base, creds) = withContext(Dispatchers.IO) {
            val baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
            val base = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            val creds = ProfileCredentialsStore.getStoredCredentials(context)
            Pair(base, creds)
        }
        if (base == null || creds == null) return

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
            deferredResults.awaitAll().forEach { (entry, result) ->
                if (result.isSuccess) {
                    successfulIds.add(entry.id)
                }
            }
            if (successfulIds.isNotEmpty()) {
                outboxStore.deleteEntries(successfulIds)
            }
        }
    }


    suspend fun resendOutboxEntry(
        entry: OutboxEntry,
        baseUrl: String?,
        username: String?,
        password: String?,
        sessionCookie: String?
    ): ResendOutcome {
        if (!isDeviceOnline()) {
            return ResendOutcome.OFFLINE
        }

        val normalized = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return ResendOutcome.MISSING_SERVER
        val authService = AuthDependencies.provideAuthService(context, normalized)
        val token = sessionCookie ?: withContext(Dispatchers.IO) { authService.getStoredToken() }

        val serverRev = fetchServerRevision(
            normalized,
            entry.submission.parent.id,
            entry.teamId,
            token,
            username,
            password
        ) ?: return ResendOutcome.REVISION_UNVERIFIABLE

        val localRev = entry.submission.parent.rev
        if (localRev != null && localRev != serverRev) {
            return ResendOutcome.REVISION_MISMATCH
        }

        val creds = if (username != null && password != null) StoredCredentials(username, password) else null

        val result = submissionsRepo.submitSurvey(
            normalized,
            credentials = creds,
            sessionCookie = token,
            submission = entry.submission
        )

        return if (result.isSuccess) {
            deleteEntry(entry.id)
            ResendOutcome.SUCCESS
        } else {
            ResendOutcome.FAILED
        }
    }

    private suspend fun fetchServerRevision(
        baseUrl: String,
        surveyId: String?,
        teamId: String?,
        sessionCookie: String?,
        username: String?,
        password: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            val id = surveyId?.takeIf { it.isNotBlank() } ?: return@withContext null
            val selector = JSONObject().apply {
                put("_id", id)
                put("type", "surveys")
                teamId?.takeIf { it.isNotBlank() }?.let { put("teamId", it) }
            }
            val payload = JSONObject().apply {
                put("selector", selector)
                put("limit", 1)
                put("fields", JSONArray().apply { put("_rev") })
            }.toString()
            val url = "$baseUrl/db/exams/_find"
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBuilder = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMediaType))
            if (!sessionCookie.isNullOrBlank()) {
                requestBuilder.addHeader("Cookie", sessionCookie)
            } else if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                val creds = Credentials.basic(username, password)
                requestBuilder.addHeader("Authorization", creds)
            }
            runCatching {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string()
                    if (body.isBlank()) return@use null
                    val docs = JSONObject(body).optJSONArray("docs")
                    val first = docs?.optJSONObject(0)
                    first?.optString("_rev")?.takeIf { it.isNotBlank() }
                }
            }.getOrNull()
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
