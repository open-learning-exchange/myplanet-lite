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
        val teamId = if (isTeamResourcesTab) DashboardTeamSelectionPreferences.getSelectedTeamId(context) else null
        val normalizedMediaType = DashboardResourcesMediaUtils.normalizeResourceMediaType(mimeType)
        val result = repository.createAndUploadResourceSequence(
            DashboardResourcesRepository.CreateAndUploadResourceRequest(
                baseUrl = resolvedBaseUrl,
                sessionCookie = sessionCookie,
                credentials = credentials,
                payload = payload,
                fileExtension = fileExtension,
                mimeType = normalizedMediaType,
                bytes = bytes,
                teamId = teamId,
                planetCode = planetCode
            )
        )
        result.onSuccess {
            setUploadLoadingVisible(false)
            onSuccess()
        }.onFailure { error ->
            setUploadLoadingVisible(false)
            val errorMessage = if (error is DashboardResourcesRepository.InvalidServerResponseException) {
                getString(R.string.dashboard_resources_error_invalid_server_response)
            } else {
                error.message ?: getString(R.string.course_wizard_play_error)
            }
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun DashboardResourcesPageFragment.setUploadLoadingVisible(visible: Boolean) {
    uploadLoadingView?.isVisible = visible
}
