package org.ole.planet.myplanet.lite

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.GoogleEventLinkStore
import org.ole.planet.myplanet.lite.dashboard.PlanetGoogleSync
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore

internal class CalendarGoogleSyncRetryController(private val fragment: Fragment, root: View,
    private val requestPermission: () -> Unit, private val onChanged: () -> Unit) {
    private val status = root.findViewById<TextView>(R.id.calendarGoogleSyncStatus)
    private val button = root.findViewById<View>(R.id.calendarGoogleSyncRetry)
    private var job: Job? = null
    private fun hasPermission() = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
        ContextCompat.checkSelfPermission(fragment.requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }
    init { button.setOnClickListener { if (!hasPermission()) requestPermission() else refresh() } }
    fun refresh() {
        if (job?.isActive == true) return
        val context = fragment.requireContext().applicationContext
        val base = DashboardServerPreferences.getServerBaseUrl(context) ?: return
        val user = ProfileCredentialsStore.getStoredCredentials(context)?.username ?: return
        val store = GoogleEventLinkStore(context, base, user)
        val pending = store.jobs().isNotEmpty()
        status.isVisible = pending; button.isVisible = pending
        if (!pending) return
        status.setText(if (hasPermission()) R.string.calendar_google_sync_pending else R.string.calendar_google_sync_permission)
        if (!hasPermission()) return
        button.isEnabled = false
        job = fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val remaining = PlanetGoogleSync(context).retry(base, user)
                status.isVisible = remaining > 0; button.isVisible = remaining > 0
                onChanged()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status.isVisible = true; button.isVisible = true }
            finally { button.isEnabled = true }
        }
    }
}
