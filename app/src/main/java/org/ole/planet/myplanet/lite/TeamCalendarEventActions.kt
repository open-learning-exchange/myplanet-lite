package org.ole.planet.myplanet.lite

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.*
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore

internal class TeamCalendarEventActions(
    private val fragment: Fragment,
    private val selectedTeam: () -> String?,
    private val requestPermission: () -> Unit,
    private val plainDescription: (String) -> String,
) {
    private var pendingExport: Pair<String, TeamCalendarEvent>? = null
    private var menuJob: Job? = null
    private var exporting = false
    private val context get() = fragment.requireContext()
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun canRead() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun header(event: TeamCalendarEvent): LinearLayout = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = event.title; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        if (event.planetMeetup != null) addView(AppCompatImageButton(context).apply {
            tag = "calendarEventActions"
            setImageResource(R.drawable.ic_more_vert_24)
            contentDescription = context.getString(R.string.calendar_event_actions)
            val background = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, background, true)
            setBackgroundResource(background.resourceId)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { openMenu(this, event) }
        })
    }

    private fun export(event: TeamCalendarEvent): PlanetCalendarExport = event.planetMeetup!!.exportOccurrence(
        event.occurrenceStart!!, plainDescription(event.planetMeetup.description))

    private fun marker(team: String, event: TeamCalendarEvent): String = planetGoogleMarker(
        DashboardServerPreferences.getServerBaseUrl(context).orEmpty(), team, event.id, event.occurrenceStart.toString())

    private fun openMenu(anchor: View, event: TeamCalendarEvent) {
        val team = selectedTeam() ?: return
        val appContext = context.applicationContext
        val base = DashboardServerPreferences.getServerBaseUrl(appContext)
        val credentials = ProfileCredentialsStore.getStoredCredentials(appContext)
        val isCreator = credentials?.username?.let { event.planetMeetup?.createdBy == it } == true
        val export = runCatching { export(event) }.getOrNull()
        val marker = marker(team, event)
        menuJob?.cancel()
        menuJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            anchor.isEnabled = false
            try {
                val permissions = if (base != null && credentials != null) {
                    val cookie = withContext(Dispatchers.IO) { AuthDependencies.provideAuthService(appContext, base).getStoredToken() }
                    TeamEventEditorRepository().permissions(base, credentials, cookie, team, event.id)
                        .getOrDefault(EventActionPermissions(isCreator, isCreator))
                } else EventActionPermissions(isCreator, isCreator)
                var duplicateCheckFailed = false
                val alreadyAdded = if (export != null && canRead()) {
                    try {
                        withContext(Dispatchers.IO) { PlanetGoogleDuplicateChecker(appContext.contentResolver).exists(export, marker) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { duplicateCheckFailed = true; false }
                } else false
                if (!anchor.isAttachedToWindow || team != selectedTeam()) return@launch
                val popup = PopupMenu(ContextThemeWrapper(context, R.style.Widget_MyPlanet_CalendarActions), anchor, Gravity.END)
                popup.menuInflater.inflate(R.menu.menu_team_calendar_event, popup.menu)
                configureCalendarActions(popup.menu, permissions, alreadyAdded, export != null && !duplicateCheckFailed)
                (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.calendarActionEdit -> {
                            if (fragment.childFragmentManager.findFragmentByTag("create_team_event") == null) {
                                CreateTeamEventDialogFragment.editInstance(team, event.id)
                                    .show(fragment.childFragmentManager, "create_team_event")
                            }
                        }
                        R.id.calendarActionDelete -> {
                            if (fragment.childFragmentManager.findFragmentByTag("delete_team_event") == null) {
                                DeleteTeamEventDialogFragment.newInstance(team, event.planetMeetup!!)
                                    .show(fragment.childFragmentManager, "delete_team_event")
                            }
                        }
                        R.id.calendarActionGoogle -> addToGoogle(team, event)
                    }
                    true
                }
                if (duplicateCheckFailed) message(R.string.calendar_google_error)
                if (popup.menu.hasVisibleItems()) popup.show()
                else if (!duplicateCheckFailed) message(R.string.calendar_google_already_added)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message(R.string.calendar_load_error) }
            finally { anchor.isEnabled = true }
        }
    }

    private fun addToGoogle(team: String, event: TeamCalendarEvent) {
        if (exporting) return
        if (!canRead()) {
            pendingExport = team to event
            requestPermission()
            return
        }
        exporting = true
        val marker = marker(team, event)
        val export = runCatching { export(event) }.getOrElse {
            exporting = false; message(R.string.calendar_google_export_error); return
        }
        val resolver = context.applicationContext.contentResolver
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val exists = withContext(Dispatchers.IO) { PlanetGoogleDuplicateChecker(resolver).exists(export, marker) }
                if (team != selectedTeam()) return@launch
                if (exists) { message(R.string.calendar_google_already_added); return@launch }
                val overlaps = withContext(Dispatchers.IO) {
                    GoogleExportOverlapChecker(context.applicationContext).overlaps(team, export, marker)
                }
                if (team != selectedTeam() || !fragment.isAdded) return@launch
                val identified = export.copy(description = listOf(export.description, marker).filter(String::isNotBlank).joinToString("\n\n"))
                if (overlaps) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                        .setMessage(R.string.calendar_overlap_google)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.calendar_add_anyway) { _, _ ->
                            if (fragment.isAdded && selectedTeam() == team) launchExport(identified)
                        }.show()
                    return@launch
                }
                launchExport(identified)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message(R.string.calendar_google_export_error) }
            finally { exporting = false }
        }
    }

    private fun launchExport(export: PlanetCalendarExport) {
        try { fragment.startActivity(export.appIntent()) }
        catch (_: ActivityNotFoundException) { fragment.startActivity(export.webIntent()) }
    }

    fun permissionResult(granted: Boolean) {
        val pending = pendingExport
        pendingExport = null
        if (!granted) { message(R.string.calendar_google_permission); return }
        if (pending != null && pending.first == selectedTeam()) addToGoogle(pending.first, pending.second)
    }

    private fun message(resource: Int) { if (fragment.isAdded) Toast.makeText(context, resource, Toast.LENGTH_LONG).show() }
}

internal fun configureCalendarActions(menu: android.view.Menu, permissions: EventActionPermissions, alreadyAdded: Boolean, exportable: Boolean) {
    menu.findItem(R.id.calendarActionEdit).isVisible = permissions.canEdit
    menu.findItem(R.id.calendarActionGoogle).isVisible = exportable && !alreadyAdded
    menu.findItem(R.id.calendarActionDelete).isVisible = permissions.canDelete
}
