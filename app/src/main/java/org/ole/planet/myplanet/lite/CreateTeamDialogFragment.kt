package org.ole.planet.myplanet.lite

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.CreateTeamRequest
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseSelectionPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsDependencies
import org.ole.planet.myplanet.lite.dashboard.DuplicateTeamNameException
import org.ole.planet.myplanet.lite.dashboard.IncompleteTeamCreationException
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase

class CreateTeamDialogFragment : DialogFragment() {
    private var listener: Listener? = null
    private lateinit var content: View
    private lateinit var nameLayout: TextInputLayout
    private lateinit var nameInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var servicesInput: EditText
    private lateinit var rulesInput: EditText
    private lateinit var publicSwitch: MaterialSwitch
    private lateinit var progress: ProgressBar
    private var incompleteTeamId: String? = null
    private var incompleteRequestData: RequestData? = null
    private val enterpriseMode get() = arguments?.getBoolean(ARG_ENTERPRISE_MODE) == true

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_team, null)
        bindViews()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (enterpriseMode) R.string.create_enterprise_title else R.string.create_team_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.create_team_action, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit(dialog) }
                }
            }
    }

    override fun onDetach() {
        listener = null
        super.onDetach()
    }

    private fun bindViews() {
        nameLayout = content.findViewById(R.id.createTeamNameLayout)
        nameInput = content.findViewById(R.id.createTeamName)
        descriptionInput = content.findViewById(R.id.createTeamDescription)
        if (enterpriseMode) {
            content.findViewById<TextInputLayout>(R.id.createTeamDescriptionLayout)
                .hint = getString(R.string.create_enterprise_mission_label)
        }
        servicesInput = content.findViewById(R.id.createEnterpriseServices)
        rulesInput = content.findViewById(R.id.createEnterpriseRules)
        content.findViewById<View>(R.id.createEnterpriseServicesLayout).isVisible = enterpriseMode
        content.findViewById<View>(R.id.createEnterpriseRulesLayout).isVisible = enterpriseMode
        publicSwitch = content.findViewById(R.id.createTeamPublic)
        progress = content.findViewById(R.id.createTeamProgress)
    }

    private fun submit(dialog: AlertDialog) {
        val name = nameInput.text?.toString()?.trim().orEmpty()
        nameLayout.error = null
        if (name.isBlank()) {
            nameLayout.error = getString(R.string.create_team_name_required)
            return
        }

        lifecycleScope.launch {
            setLoading(dialog, true)
            val requestData = incompleteRequestData ?: buildRequest(name)
            val result = requestData?.let {
                val repository = DashboardTeamsDependencies.provideRepository()
                incompleteTeamId?.let { teamId ->
                    repository.retryTeamLeaderMembership(
                        it.baseUrl,
                        it.credentials,
                        it.sessionCookie,
                        teamId,
                        it.request,
                    )
                } ?: repository.createTeam(
                    it.baseUrl,
                    it.credentials,
                    it.sessionCookie,
                    it.request,
                )
            }
            if (!isAdded) return@launch
            setLoading(dialog, false)
            if (result == null) {
                Toast.makeText(requireContext(), R.string.create_team_missing_context, Toast.LENGTH_LONG).show()
                return@launch
            }
            result.onSuccess { team ->
                if (enterpriseMode) {
                    DashboardEnterpriseSelectionPreferences.setSelectedEnterprise(
                        requireContext(),
                        team.id,
                        requestData.request.name,
                        requestData.request.teamType,
                        requestData.request.planetCode,
                    )
                }
                Toast.makeText(
                    requireContext(),
                    if (enterpriseMode) R.string.create_enterprise_success else R.string.create_team_success,
                    Toast.LENGTH_SHORT,
                ).show()
                listener?.onTeamCreated(team.id)
                dismiss()
            }.onFailure { error ->
                when (error) {
                    is DuplicateTeamNameException -> nameLayout.error = getString(
                        if (enterpriseMode) R.string.create_enterprise_duplicate_name else R.string.create_team_duplicate_name,
                    )
                    is IncompleteTeamCreationException -> {
                        incompleteTeamId = error.teamId
                        incompleteRequestData = requestData
                        setFormEnabled(false)
                        Toast.makeText(
                            requireContext(),
                            if (enterpriseMode) R.string.create_enterprise_incomplete else R.string.create_team_incomplete,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    else -> Toast.makeText(
                        requireContext(),
                        if (enterpriseMode) R.string.create_enterprise_error else R.string.create_team_error,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private suspend fun buildRequest(name: String): RequestData? {
        val context = requireContext().applicationContext
        val baseUrl = DashboardServerPreferences.getServerBaseUrl(context) ?: return null
        val planetCode = DashboardServerPreferences.getServerCode(context) ?: return null
        val parentCode = DashboardServerPreferences.getServerParentCode(context) ?: return null
        val credentials = ProfileCredentialsStore.getStoredCredentials(context) ?: return null
        val profile = withContext(Dispatchers.IO) { UserProfileDatabase.getInstance(context).getProfile() }
        val rawUserId = profile?.rawDocument?.let { raw ->
            runCatching { JSONObject(raw).optString("_id") }.getOrNull()
        }?.takeIf(String::isNotBlank)
        val username = profile?.username?.takeIf(String::isNotBlank) ?: credentials.username
        val userId = rawUserId ?: username.takeIf(String::isNotBlank)?.let { "org.couchdb.user:$it" } ?: return null
        val sessionCookie = AuthDependencies.provideAuthService(context, baseUrl).getStoredToken()
        val request = CreateTeamRequest(
            name = name,
            description = descriptionInput.text?.toString().orEmpty(),
            services = servicesInput.text?.toString().orEmpty(),
            rules = rulesInput.text?.toString().orEmpty(),
            isPublic = publicSwitch.isChecked,
            planetCode = planetCode,
            parentCode = parentCode,
            userId = userId,
            entityType = if (enterpriseMode) "enterprise" else "team",
            teamType = if (enterpriseMode) "sync" else "local",
        )
        return RequestData(baseUrl, credentials, sessionCookie, request)
    }

    private fun setLoading(dialog: AlertDialog, loading: Boolean) {
        progress.isVisible = loading
        setFormEnabled(!loading && incompleteTeamId == null)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !loading
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !loading
        isCancelable = !loading
    }

    private fun setFormEnabled(enabled: Boolean) {
        nameInput.isEnabled = enabled
        descriptionInput.isEnabled = enabled
        servicesInput.isEnabled = enabled
        rulesInput.isEnabled = enabled
        publicSwitch.isEnabled = enabled
    }

    private data class RequestData(
        val baseUrl: String,
        val credentials: org.ole.planet.myplanet.lite.profile.StoredCredentials,
        val sessionCookie: String?,
        val request: CreateTeamRequest,
    )

    interface Listener {
        fun onTeamCreated(teamId: String)
    }

    companion object {
        const val TAG = "create_team_dialog"
        private const val ARG_ENTERPRISE_MODE = "enterprise_mode"

        fun newEnterpriseInstance() = CreateTeamDialogFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_ENTERPRISE_MODE, true) }
        }
    }
}
