package org.ole.planet.myplanet.lite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.dashboard.GoogleCalendarSelectionStore
import org.ole.planet.myplanet.lite.dashboard.GoogleDeviceCalendarRepository

internal class GoogleCalendarConnection(
    private val fragment: Fragment,
    private val root: View,
    private val requestPermission: () -> Unit,
    private val onEvents: (List<TeamCalendarEvent>) -> Unit,
) : DefaultLifecycleObserver {
    private val context = fragment.requireContext().applicationContext
    private val resolver = context.contentResolver
    private val repository = GoogleDeviceCalendarRepository(resolver)
    private val store = GoogleCalendarSelectionStore(context)
    private val owner = fragment.viewLifecycleOwner
    private val button = root.findViewById<MaterialButton>(R.id.calendarGoogleConnect)
    private val status = root.findViewById<TextView>(R.id.calendarGoogleStatus)
    private var scope: String? = null
    private var from: LocalDate? = null
    private var through: LocalDate? = null
    private var job: Job? = null
    private var chooserJob: Job? = null
    private var chooser: androidx.appcompat.app.AlertDialog? = null
    private var observing = false
    private var started = false
    private var revision = 0
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh(debounce = true) }
    }

    init {
        button.setOnClickListener {
            if (hasPermission()) chooseCalendars() else requestPermission()
        }
        owner.lifecycle.addObserver(this)
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun showRange(teamId: String?, first: LocalDate, last: LocalDate) {
        val nextScope = store.scope(teamId)
        if (scope == nextScope && from == first && through == last) return
        val changedScope = scope != nextScope
        scope = nextScope; from = first; through = last
        if (changedScope) { chooser?.dismiss(); chooserJob?.cancel() }
        refresh()
    }

    fun permissionResult(granted: Boolean) {
        if (granted) {
            observe()
            chooseCalendars()
        } else {
            refresh()
            chooser = MaterialAlertDialogBuilder(fragment.requireContext())
                .setMessage(R.string.calendar_google_permission)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.calendar_google_settings) { _, _ ->
                    fragment.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")))
                }.show()
        }
    }

    override fun onStart(owner: LifecycleOwner) { started = true; observe(); refresh() }
    override fun onResume(owner: LifecycleOwner) { observe(); refresh() }
    override fun onStop(owner: LifecycleOwner) {
        started = false
        if (observing) resolver.unregisterContentObserver(observer)
        observing = false
        revision++
        job?.cancel(); chooserJob?.cancel(); chooser?.dismiss()
    }
    override fun onDestroy(owner: LifecycleOwner) { owner.lifecycle.removeObserver(this) }

    private fun observe() {
        if (!started || observing || !hasPermission()) return
        try {
            resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
        } catch (_: SecurityException) { status.setText(R.string.calendar_google_permission) }
    }

    fun refresh(debounce: Boolean = false) {
        val selectedScope = scope
        val first = from ?: return
        val last = through ?: return
        revision++
        val requestRevision = revision
        job?.cancel()
        button.isVisible = selectedScope != null
        status.isVisible = selectedScope != null
        val selected = selectedScope?.let(store::selected).orEmpty()
        button.setText(if (selected.isEmpty()) R.string.calendar_google_connect else R.string.calendar_google_manage)
        if (selectedScope == null || selected.isEmpty() || !hasPermission()) {
            status.setText(if (selected.isNotEmpty() && !hasPermission()) R.string.calendar_google_permission else R.string.calendar_google_hint)
            onEvents(emptyList())
            return
        }
        if (!started) return
        onEvents(emptyList())
        status.setText(R.string.calendar_google_loading)
        job = owner.lifecycleScope.launch {
            if (debounce) delay(250)
            val result = try {
                withContext(Dispatchers.IO) {
                    repository.events(selected, first, last).flatMap { it.occurrences(first, last) }
                        .distinctBy { it.id to it.date }
                }.let { Result.success(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
            if (scope != selectedScope || revision != requestRevision) return@launch
            result.onSuccess {
                status.setText(R.string.calendar_google_linked)
                onEvents(it)
            }.onFailure {
                status.setText(if (it is SecurityException) R.string.calendar_google_permission else R.string.calendar_google_error)
                onEvents(emptyList())
            }
        }
    }

    private fun chooseCalendars() {
        val selectedScope = scope ?: return
        chooserJob?.cancel()
        chooserJob = owner.lifecycleScope.launch {
            val available = try {
                withContext(Dispatchers.IO) { repository.calendars() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                status.setText(R.string.calendar_google_error)
                return@launch
            }
            if (scope != selectedScope || !started) return@launch
            if (available.isEmpty()) {
                status.setText(R.string.calendar_google_no_calendars)
                chooser = MaterialAlertDialogBuilder(fragment.requireContext())
                    .setMessage(R.string.calendar_google_no_calendars)
                    .setPositiveButton(R.string.calendar_google_settings) { _, _ ->
                        fragment.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
                    }.setNegativeButton(android.R.string.cancel, null).show()
                return@launch
            }
            val chosen = store.selected(selectedScope).toMutableSet()
            chooser?.dismiss()
            chooser = MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.calendar_google_choose)
                .setMultiChoiceItems(available.map { "${it.name}\n${it.account}" }.toTypedArray(),
                    available.map { it.key in chosen }.toBooleanArray()) { _, index, checked ->
                    if (checked) chosen += available[index].key else chosen -= available[index].key
                }.setPositiveButton(android.R.string.ok) { _, _ ->
                    if (scope == selectedScope) {
                        store.save(selectedScope, chosen.intersect(available.map { it.key }.toSet())); refresh()
                    }
                }.setNeutralButton(R.string.calendar_google_disconnect) { _, _ ->
                    if (scope == selectedScope) { store.save(selectedScope, emptySet()); refresh() }
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
    }
}
