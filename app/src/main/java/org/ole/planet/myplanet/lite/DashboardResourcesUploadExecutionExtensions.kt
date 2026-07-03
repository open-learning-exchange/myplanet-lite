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
    val teamId = if (isTeamResourcesTab) DashboardTeamSelectionPreferences.getSelectedTeamId(context) else null
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

        val request = DashboardResourcesRepository.UploadResourceRequest(
            baseUrl = resolvedBaseUrl,
            sessionCookie = sessionCookie,
            username = credentials?.username,
            password = credentials?.password,
            payload = payload,
            fileExtension = fileExtension,
            mimeType = mimeType,
            bytes = bytes,
            teamId = teamId,
            planetCode = planetCode
        )

        val result = repository.uploadNewResource(request)

        setUploadLoadingVisible(false)
        result.onSuccess {
            onSuccess()
        }.onFailure { error ->
            Toast.makeText(context, error.message ?: getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun DashboardResourcesPageFragment.setUploadLoadingVisible(visible: Boolean) {
    uploadLoadingView?.isVisible = visible
}
