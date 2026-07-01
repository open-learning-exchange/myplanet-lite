package org.ole.planet.myplanet.lite

import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.ole.planet.myplanet.lite.dashboard.DashboardResourcesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences

internal fun DashboardResourcesPageFragment.performResourceCreateAndUpload(
    payload: JSONObject,
    fileExtension: String,
    mimeType: String,
    credentials: org.ole.planet.myplanet.lite.profile.StoredCredentials?,
    bytesProvider: suspend () -> ByteArray?,
    onSuccess: () -> Unit
) {
    val context = requireContext()
    val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()
    if (isTeamResourcesTab) {
        val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(context)
        if (!teamId.isNullOrBlank()) {
            payload.put("private", true)
            payload.put("privateFor", JSONObject().put("teams", teamId))
        }
    }
    DashboardResourcesMediaUtils.applyWebCompatibleResourceDefaults(payload)
    payload.put("mediaType", DashboardResourcesMediaUtils.normalizeResourceMediaType(mimeType))
    val now = System.currentTimeMillis()
    payload.put("createdDate", now)
    payload.put("updatedDate", now)
    val resolvedBaseUrl = DashboardServerPreferences.getServerBaseUrl(context)
    if (resolvedBaseUrl.isNullOrBlank()) {
        Toast.makeText(context, getString(R.string.dashboard_voices_no_server), Toast.LENGTH_SHORT).show()
        return
    }
    setUploadLoadingVisible(true)
    lifecycleScope.launch {
        val bytes = bytesProvider()
        if (bytes == null) {
            setUploadLoadingVisible(false)
            Toast.makeText(context, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
            return@launch
        }
        val result = repository.createResourceDocument(
            baseUrl = resolvedBaseUrl,
            sessionCookie = sessionCookie,
            username = credentials?.username,
            password = credentials?.password,
            payload = payload
        )
        result.onSuccess { creationResponse ->
            val resourceId = creationResponse.optString("id").orEmpty()
            val creationRevision = creationResponse.optString("rev").orEmpty()
            if (resourceId.isBlank() || creationRevision.isBlank()) {
                setUploadLoadingVisible(false)
                Toast.makeText(context, getString(R.string.dashboard_resources_error_invalid_server_response), Toast.LENGTH_SHORT).show()
                return@onSuccess
            }
            val renamedFileName = "${resourceId}.${fileExtension.lowercase(Locale.ROOT)}"
            val normalizedMediaType = DashboardResourcesMediaUtils.normalizeResourceMediaType(mimeType)
            val updatePayload = JSONObject(payload.toString())
                .put("_id", resourceId)
                .put("_rev", creationRevision)
                .put("filename", renamedFileName)
                .put("mediaType", normalizedMediaType)
            val updateResult = repository.updateResourceDocument(
                baseUrl = resolvedBaseUrl,
                sessionCookie = sessionCookie,
                username = credentials?.username,
                password = credentials?.password,
                resourceId = resourceId,
                payload = updatePayload
            )
            val updateResponse = updateResult.getOrElse { error ->
                setUploadLoadingVisible(false)
                Toast.makeText(context, error.message ?: getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
                return@onSuccess
            }
            val updateRevision = updateResponse.optString("rev").orEmpty().ifBlank { creationRevision }
            val uploadResult = repository.uploadResourceAttachment(
                DashboardResourcesRepository.UploadAttachmentRequest(
                    baseUrl = resolvedBaseUrl,
                    sessionCookie = sessionCookie,
                    username = credentials?.username,
                    password = credentials?.password,
                    resourceId = resourceId,
                    filename = renamedFileName,
                    revision = updateRevision,
                    mimeType = mimeType,
                    bytes = bytes
                )
            )
            uploadResult.onSuccess {
                if (isTeamResourcesTab) {
                    val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(context)
                    if (!teamId.isNullOrBlank()) {
                        val linkPayload = JSONObject()
                        linkPayload.put("resourceId", resourceId)
                        linkPayload.put("sourcePlanet", planetCode)
                        linkPayload.put("title", payload.optString("title"))
                        linkPayload.put("teamId", teamId)
                        linkPayload.put("teamPlanetCode", planetCode)
                        linkPayload.put("teamType", "local")
                        linkPayload.put("docType", "resourceLink")
                        repository.createTeamDocument(
                            baseUrl = resolvedBaseUrl,
                            sessionCookie = sessionCookie,
                            username = credentials?.username,
                            password = credentials?.password,
                            payload = linkPayload
                        )
                    }
                }
                setUploadLoadingVisible(false)
                onSuccess()
            }.onFailure { error ->
                setUploadLoadingVisible(false)
                Toast.makeText(context, error.message ?: getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
            }
        }.onFailure { error ->
            setUploadLoadingVisible(false)
            Toast.makeText(context, error.message ?: getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun DashboardResourcesPageFragment.setUploadLoadingVisible(visible: Boolean) {
    uploadLoadingView?.isVisible = visible
}
