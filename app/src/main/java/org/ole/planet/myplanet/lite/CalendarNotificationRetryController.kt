package org.ole.planet.myplanet.lite

import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.CalendarNotificationOutbox
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore

internal class CalendarNotificationRetryController(private val fragment: Fragment, root: View) {
    private val status = root.findViewById<View>(R.id.calendarNotificationsStatus)
    private val retry = root.findViewById<View>(R.id.calendarNotificationsRetry)
    private var job: Job? = null
    init { retry.setOnClickListener { refresh() } }
    fun refresh() {
        if (job?.isActive == true) return
        val context = fragment.requireContext().applicationContext
        val base = DashboardServerPreferences.getServerBaseUrl(context) ?: return
        val credentials = ProfileCredentialsStore.getStoredCredentials(context) ?: return
        val queue = CalendarNotificationOutbox(context)
        val pending = queue.pending(base, credentials.username)
        status.isVisible = pending > 0; retry.isVisible = pending > 0
        if (pending == 0) return
        retry.isEnabled = false
        job = fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cookie = withContext(Dispatchers.IO) { AuthDependencies.provideAuthService(context, base).getStoredToken() }
                val remaining = queue.retry(base, credentials, cookie)
                status.isVisible = remaining > 0; retry.isVisible = remaining > 0
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status.isVisible = true; retry.isVisible = true }
            finally { retry.isEnabled = true }
        }
    }
}
