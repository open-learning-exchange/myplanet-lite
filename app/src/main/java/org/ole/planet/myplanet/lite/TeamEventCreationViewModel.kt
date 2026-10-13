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
    val saving: Boolean = false,
    val loading: Boolean = false,
    val saved: TeamMeetup? = null,
    val editable: EditableTeamEvent? = null,
    val errorRes: Int? = null,
)

internal class TeamEventCreationViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(EventCreationState())
    val state = mutableState.asStateFlow()

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

    fun save(teamId: String, draft: TeamEventDraft, eventId: String? = null) {
        if (state.value.saving || state.value.loading || state.value.saved != null) return
        val original = state.value.editable
        if (eventId != null && original == null) return
        mutableState.value = state.value.copy(saving = true, errorRes = null)
        viewModelScope.launch {
            val result = catching {
                val session = session()
                if (eventId != null) {
                    TeamEventEditorRepository().save(session.base, session.credentials, session.cookie, teamId,
                        eventId, original!!.meetup.revision, draft).getOrThrow()
                } else {
                    val sourcePlanet = requireNotNull(DashboardServerPreferences.getServerCode(getApplication()))
                    TeamCalendarRepository().create(session.base, session.credentials, session.cookie,
                        teamId, session.credentials.username, sourcePlanet, draft).getOrThrow()
                }
            }
            mutableState.value = result.fold(
                { state.value.copy(saving = false, saved = it) },
                { state.value.copy(saving = false, editable = (it as? CalendarEditConflictException)?.latest ?: original,
                    errorRes = errorMessage(it)) },
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
        is EventValidationException -> error.messageRes
        else -> R.string.calendar_create_error
    }
    private suspend fun <T> catching(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
}
