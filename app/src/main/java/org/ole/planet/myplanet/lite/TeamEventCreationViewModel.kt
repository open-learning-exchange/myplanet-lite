package org.ole.planet.myplanet.lite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.*
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal data class EventCreationState(
    val overlapRes: Int? = null,
    val saving: Boolean = false,
    val loading: Boolean = false,
    val saved: TeamMeetup? = null,
    val editable: EditableTeamEvent? = null,
    val errorRes: Int? = null,
    val permissions: Array<String>? = null,
    val permissionId: String? = null,
    val syncWarning: Boolean = false,
)

internal class TeamEventCreationViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(EventCreationState())
    val state = mutableState.asStateFlow()

    private var overlapSave: (() -> Unit)? = null
    fun resolveOverlap(proceed: Boolean) {
        mutableState.value = state.value.copy(overlapRes = null)
        val action = overlapSave
        overlapSave = null
        if (proceed) action?.invoke()
    }
    private var pendingSave: (() -> Unit)? = null
    fun permissionResult(granted: Boolean) {
        mutableState.value = state.value.copy(permissions = null, permissionId = null,
            errorRes = if (granted) null else R.string.calendar_google_sync_permission)
        if (granted) pendingSave?.invoke()
    }

    fun load(teamId: String, eventId: String, force: Boolean = false) {
        if (state.value.loading || (!force && state.value.editable != null)) return
        mutableState.value = state.value.copy(loading = true, errorRes = null)
        viewModelScope.launch {
            val result = catching {
                val session = session()
                TeamEventEditorRepository().load(session.base, session.credentials, session.cookie, teamId, eventId).getOrThrow()
            }
            mutableState.value = result.fold(
                { state.value.copy(loading = false, editable = it) },
                { state.value.copy(loading = false, errorRes = errorMessage(it)) },
            )
        }
    }

    fun save(teamId: String, draft: TeamEventDraft, eventId: String? = null, allowOverlaps: Boolean = false) {
        if (state.value.saving || state.value.loading || state.value.saved != null) return
        val original = state.value.editable
        if (eventId != null && original == null) return
        pendingSave = { save(teamId, draft, eventId, allowOverlaps) }
        mutableState.value = state.value.copy(saving = true, errorRes = null, permissions = null, permissionId = null)
        viewModelScope.launch {
            val result = catching {
                val session = session()
                if (eventId != null) {
                    val sync = PlanetGoogleSync(getApplication())
                    val plan = sync.prepare(session.base, session.credentials.username, teamId, original!!.meetup)
                    val saved = TeamEventEditorRepository().save(session.base, session.credentials, session.cookie, teamId,
                        eventId, original.meetup.revision, draft).getOrThrow()
                    var syncWarning = false
                    try {
                        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                            sync.enqueue(plan, saved)
                            syncWarning = sync.retry(session.base, session.credentials.username) > 0
                        }
                    } catch (_: Exception) { syncWarning = true }
                    mutableState.value = state.value.copy(syncWarning = syncWarning)
                    // Notifications are secondary: never turn a successful event save into an update retry.
                    try {
                        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                            CalendarNotificationOutbox(getApplication()).apply {
                                enqueue(session.base, session.credentials.username, saved.id, saved.title)
                                sendInBackground(session.base, session.credentials, session.cookie)
                            }
                        }
                    } catch (_: Exception) { /* The event has already been saved. */ }
                    saved
                } else {
                    CalendarOverlapValidator(getApplication()).validate(session.base, session.credentials, session.cookie, teamId, draft, allowOverlaps)
                    val sourcePlanet = requireNotNull(DashboardServerPreferences.getServerCode(getApplication()))
                    TeamCalendarRepository().create(session.base, session.credentials, session.cookie,
                        teamId, session.credentials.username, sourcePlanet, draft).getOrThrow()
                }
            }
            val failure = result.exceptionOrNull() as? EventValidationException
            if (failure?.messageRes in listOf(R.string.calendar_overlap_planet, R.string.calendar_overlap_google)) {
                overlapSave = { save(teamId, draft, eventId, allowOverlaps = true) }
                mutableState.value = state.value.copy(saving = false, overlapRes = failure!!.messageRes)
                return@launch
            }
            mutableState.value = result.fold(
                { state.value.copy(saving = false, saved = it) },
                { state.value.copy(saving = false, editable = (it as? CalendarEditConflictException)?.latest ?: original,
                    permissions = (it as? GoogleSyncPermissionRequired)?.permissions,
                    permissionId = if (it is GoogleSyncPermissionRequired) java.util.UUID.randomUUID().toString() else null,
                    errorRes = if (it is GoogleSyncPermissionRequired) null else errorMessage(it)) },
            )
        }
    }

    private data class Session(val base: String, val credentials: StoredCredentials, val cookie: String?)
    private suspend fun session(): Session {
        val context = getApplication<Application>()
        val base = requireNotNull(DashboardServerPreferences.getServerBaseUrl(context))
        val credentials = requireNotNull(ProfileCredentialsStore.getStoredCredentials(context))
        val cookie = withContext(Dispatchers.IO) { AuthDependencies.provideAuthService(context, base).getStoredToken() }
        return Session(base, credentials, cookie)
    }
    private fun errorMessage(error: Throwable) = when (error) {
        is CalendarEditConflictException -> R.string.calendar_edit_conflict
        is CalendarAccessDeniedException -> R.string.calendar_edit_denied
        is GoogleSyncPendingException -> R.string.calendar_google_sync_pending
        is EventValidationException -> error.messageRes
        else -> R.string.calendar_create_error
    }
    private suspend fun <T> catching(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
}
