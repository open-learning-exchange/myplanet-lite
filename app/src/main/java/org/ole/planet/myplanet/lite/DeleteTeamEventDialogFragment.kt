package org.ole.planet.myplanet.lite

import android.app.Application
import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.*
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore

internal data class EventDeleteState(val busy: Boolean = false, val deleted: Boolean = false,
    val ready: Boolean = false, val linkedGoogle: Boolean = false, val deleteGoogle: Boolean = true,
    val revision: String? = null, val title: String? = null, val error: Int? = null,
    val permissions: Array<String>? = null, val permissionId: String? = null, val syncWarning: Boolean = false)

internal class TeamEventDeletionViewModel(application: Application) : AndroidViewModel(application) {
    val state = MutableStateFlow(EventDeleteState())
    private var confirmedPlan: GoogleSyncPlan? = null
    private var pendingDelete: (() -> Unit)? = null
    fun selectGoogle(selected: Boolean) {
        if (!state.value.busy) state.value = state.value.copy(deleteGoogle = selected)
    }
    fun permissionResult(granted: Boolean) {
        state.value = state.value.copy(permissions = null, permissionId = null,
            error = if (granted) null else R.string.calendar_google_sync_permission)
        if (granted) pendingDelete?.invoke()
    }
    fun delete(team: String, id: String, revision: String, confirm: Boolean = false) {
        if (state.value.busy || state.value.deleted) return
        pendingDelete = { delete(team, id, revision, false) }
        state.value = state.value.copy(busy = true, error = null, permissions = null, permissionId = null)
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val base = requireNotNull(DashboardServerPreferences.getServerBaseUrl(context))
                val credentials = requireNotNull(ProfileCredentialsStore.getStoredCredentials(context))
                val cookie = withContext(Dispatchers.IO) { AuthDependencies.provideAuthService(context, base).getStoredToken() }
                val original = TeamEventEditorRepository().loadForDeletion(base, credentials, cookie, team, id).getOrThrow()
                if (original.meetup.revision != (state.value.revision ?: revision)) throw CalendarEditConflictException(original)
                val sync = PlanetGoogleSync(context)
                val plan = sync.prepare(base, credentials.username, team, original.meetup, preview = true)
                if (!confirm || !state.value.ready || confirmedPlan != plan) {
                    confirmedPlan = plan
                    state.value = state.value.copy(busy = false, ready = true,
                        linkedGoogle = plan.links.isNotEmpty(), title = original.meetup.title,
                        revision = original.meetup.revision)
                    return@launch
                }
                val deleteGoogle = state.value.deleteGoogle
                if (deleteGoogle && plan.links.isNotEmpty() &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    throw GoogleSyncPermissionRequired(arrayOf(android.Manifest.permission.WRITE_CALENDAR))
                }
                TeamEventEditorRepository().delete(base, credentials, cookie, team, id,
                    state.value.revision ?: revision).getOrThrow()
                var warning = false
                try {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                        if (deleteGoogle) {
                            sync.enqueue(plan, null)
                            warning = sync.retry(base, credentials.username) > 0
                        }
                    }
                } catch (_: Exception) { warning = true }
                state.value = state.value.copy(busy = false, deleted = true, syncWarning = warning)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val latest = (error as? CalendarEditConflictException)?.latest?.meetup
                state.value = state.value.copy(busy = false, ready = false, revision = latest?.revision ?: state.value.revision,
                    title = latest?.title ?: state.value.title,
                    permissions = (error as? GoogleSyncPermissionRequired)?.permissions,
                    permissionId = if (error is GoogleSyncPermissionRequired) java.util.UUID.randomUUID().toString() else null,
                    error = when (error) {
                        is GoogleSyncPermissionRequired -> null
                        is GoogleSyncPendingException -> R.string.calendar_google_sync_pending
                        is CalendarEditConflictException -> R.string.calendar_delete_conflict
                        is CalendarAccessDeniedException -> R.string.calendar_delete_denied
                        else -> R.string.calendar_delete_error
                    })
            }
        }
    }
}

class DeleteTeamEventDialogFragment : DialogFragment() {
    private lateinit var model: TeamEventDeletionViewModel
    private var observer: Job? = null
    private var launchedPermission: String? = null
    private lateinit var googleCheckbox: com.google.android.material.checkbox.MaterialCheckBox
    private val googleWritePermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> model.permissionResult(result.isNotEmpty() && result.values.all { it }) }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        launchedPermission = savedInstanceState?.getString("launchedPermission")
        model = ViewModelProvider(this)[TeamEventDeletionViewModel::class.java]
        googleCheckbox = com.google.android.material.checkbox.MaterialCheckBox(requireContext()).apply {
            setText(R.string.calendar_delete_google_checkbox)
            isChecked = model.state.value.deleteGoogle
            visibility = android.view.View.GONE
            setOnCheckedChangeListener { _, checked -> model.selectGoogle(checked) }
        }
        val container = android.widget.FrameLayout(requireContext()).apply {
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(googleCheckbox)
        }
        return MaterialAlertDialogBuilder(requireContext()).setView(container).setTitle(R.string.calendar_delete_event)
            .setMessage(getString(R.string.calendar_delete_confirm, requireArguments().getString("title")))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.calendar_delete_event, null).create()
    }
    override fun onStart() {
        super.onStart()
        val dialog = requireDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            model.delete(requireArguments().getString("team")!!, requireArguments().getString("id")!!,
                requireArguments().getString("revision")!!, confirm = model.state.value.ready)
        }
        if (!model.state.value.ready && model.state.value.error == null) {
            model.delete(requireArguments().getString("team")!!, requireArguments().getString("id")!!,
                requireArguments().getString("revision")!!)
        }
        observer?.cancel()
        observer = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    if (state.permissions != null && state.permissionId != launchedPermission) {
                        launchedPermission = state.permissionId
                        googleWritePermission.launch(state.permissions)
                    }
                    isCancelable = !state.busy
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !state.busy
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(
                        if (state.ready && state.linkedGoogle && state.deleteGoogle) R.string.calendar_delete_both
                        else R.string.calendar_delete_event)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !state.busy
                    googleCheckbox.visibility = if (state.ready && state.linkedGoogle) android.view.View.VISIBLE else android.view.View.GONE
                    googleCheckbox.isEnabled = !state.busy
                    googleCheckbox.isChecked = state.deleteGoogle
                    val title = state.title ?: requireArguments().getString("title")
                    dialog.setMessage((if (state.ready) getString(
                        if (state.linkedGoogle && state.deleteGoogle) R.string.calendar_delete_both_confirm
                        else R.string.calendar_delete_confirm, title)
                        else getString(R.string.calendar_delete_checking)) +
                        (state.error?.let { "\n\n" + getString(it) } ?: ""))
                    if (state.deleted) {
                        if (state.syncWarning) android.widget.Toast.makeText(requireContext(), R.string.calendar_google_sync_pending,
                            android.widget.Toast.LENGTH_LONG).show()
                        parentFragmentManager.setFragmentResult(CreateTeamEventDialogFragment.RESULT_KEY, Bundle().apply {
                            putString("teamId", requireArguments().getString("team"))
                            putString("meetupId", requireArguments().getString("id"))
                        })
                        dismiss()
                    }
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("launchedPermission", launchedPermission)
    }
    override fun onStop() { observer?.cancel(); super.onStop() }
    companion object {
        internal fun newInstance(team: String, event: TeamMeetup) = DeleteTeamEventDialogFragment().apply {
            arguments = Bundle().apply {
                putString("team", team); putString("id", event.id)
                putString("revision", event.revision); putString("title", event.title)
            }
        }
    }
}
