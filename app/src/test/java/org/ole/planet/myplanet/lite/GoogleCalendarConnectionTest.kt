package org.ole.planet.myplanet.lite

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.provider.CalendarContract
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.GoogleCalendarSelectionStore
import org.ole.planet.myplanet.lite.dashboard.GoogleDeviceCalendarRepository
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GoogleCalendarConnectionTest {
    private lateinit var application: Application
    private lateinit var provider: CalendarProviderFixture
    private lateinit var store: GoogleCalendarSelectionStore

    class HostFragment : Fragment(R.layout.fragment_team_calendar) {
        internal lateinit var connection: GoogleCalendarConnection
        internal var events: List<TeamCalendarEvent> = emptyList()
        var permissionRequested = false
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            connection = GoogleCalendarConnection(this, view, { permissionRequested = true }, { events = it })
            connection.showRange("team-1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        }
    }

    @Before fun setup() {
        application = ApplicationProvider.getApplicationContext()
        val prefs = application.getSharedPreferences("google_calendar_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("server_url", "https://planet.example").commit()
        SecurePreferencesProvider.injectedPreferences = prefs
        ProfileCredentialsStore.setSessionCredentials(StoredCredentials("ana", "password"))
        provider = CalendarProviderFixture()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        store = GoogleCalendarSelectionStore(application)
    }

    @After fun teardown() {
        ProfileCredentialsStore.setSessionCredentials(null)
        SecurePreferencesProvider.resetForTesting()
    }

    private fun await(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Calendar did not update")
    }

    @Test fun `calendar updates edits and deletions from provider and unlinks without writes`() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
        store.save(store.scope("team-1")!!, setOf("1:ana@gmail.com"))
        launchFragmentInContainer<HostFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            lateinit var host: HostFragment
            scenario.onFragment { host = it }
            await { host.events.singleOrNull()?.title == "Google meeting" }
            assertTrue(provider.instanceSelection.contains(CalendarContract.Events.DELETED))
            assertTrue(provider.instanceSelection.contains(CalendarContract.Instances.STATUS))
            assertEquals(listOf("1"), provider.instanceArguments)
            assertEquals(4, provider.lastInstanceUri!!.pathSegments.size)
            provider.title = "Edited in Google"
            application.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
            await { host.events.singleOrNull()?.title == "Edited in Google" }
            provider.title = null
            application.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
            await { host.events.isEmpty() }
            provider.title = "Restored"
            application.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
            await { host.events.singleOrNull()?.title == "Restored" }
            store.save(store.scope("team-1")!!, emptySet())
            scenario.onFragment { it.connection.refresh() }
            assertTrue(host.events.isEmpty())
        }
    }

    @Test fun `no read occurs before user grants permission`() {
        shadowOf(application).denyPermissions(Manifest.permission.READ_CALENDAR)
        store.save(store.scope("team-1")!!, setOf("1:ana@gmail.com"))
        launchFragmentInContainer<HostFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            scenario.onFragment {
                it.requireView().findViewById<View>(R.id.calendarGoogleConnect).performClick()
                assertTrue(it.permissionRequested)
                assertTrue(it.events.isEmpty())
                assertEquals(0, provider.queries)
            }
        }
    }

    @Test fun `selection is isolated by user planet and team and revalidates account identity`() {
        val first = store.scope("team-1")!!
        store.save(first, setOf("1:ana@gmail.com"))
        assertTrue(store.selected(store.scope("team-2")!!).isEmpty())
        ProfileCredentialsStore.setSessionCredentials(StoredCredentials("juan", "password"))
        assertTrue(store.selected(store.scope("team-1")!!).isEmpty())
        SecurePreferencesProvider.injectedPreferences!!.edit().putString("server_url", "https://another.example").commit()
        assertTrue(store.selected(store.scope("team-1")!!).isEmpty())
        val repository = GoogleDeviceCalendarRepository(application.contentResolver)
        provider.calendarAccount = "another@gmail.com"
        assertTrue(repository.events(setOf("1:ana@gmail.com"), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).isEmpty())
        assertNull(provider.lastInstanceUri)
        provider.visible = false
        assertTrue(repository.calendars().isEmpty())
    }
}
