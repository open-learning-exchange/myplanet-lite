package org.ole.planet.myplanet.lite

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.*
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlanetGoogleSyncTest {
    private lateinit var application: Application
    private lateinit var provider: LinkedGoogleProviderFixture
    private lateinit var sync: PlanetGoogleSync
    private val base = "https://planet.example"
    private val date = LocalDate.of(2026, 10, 12)
    private fun event() = TeamMeetup("event-1", "1-old", "Meeting", "Agenda",
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), null, "09:00", "10:00",
        "none", 10, emptySet(), "Room", "", "ana")
    private fun add(event: TeamMeetup = event(), ordinal: Int = 0, id: Long = 10) {
        val day = event.occurrenceAt(ordinal)!!
        provider.add(id, event.exportOccurrence(day, "Agenda"), planetGoogleMarker(base, "team-1", event.id, day.toString()))
    }
    @Before fun setup() {
        application = ApplicationProvider.getApplicationContext()
        val prefs = application.getSharedPreferences("google_sync_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit(); SecurePreferencesProvider.injectedPreferences = prefs
        shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        provider = LinkedGoogleProviderFixture()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        sync = PlanetGoogleSync(application)
    }
    @After fun cleanup() = SecurePreferencesProvider.resetForTesting()

    @Test fun `edit replaces linked copy in same calendar and preserves unrelated Google events`() = runTest {
        add()
        provider.add(99, event().exportOccurrence(date, "Different").copy(title = "Private Google event"))
        val plan = sync.prepare(base, "ana", "team-1", event())
        assertEquals(1, plan.links.size)
        val updated = event().copy(revision = "2-new", title = "Updated", startDate =
            date.plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        sync.enqueue(plan, updated)
        assertEquals(0, sync.retry(base, "ana"))
        assertEquals(1, provider.batches)
        assertEquals(2, provider.active().size)
        assertEquals("Private Google event", provider.events[99]!!.getAsString(Events.TITLE))
        val saved = provider.active().filterKeys { it != 99L }.values.single()
        assertEquals("Updated", saved.getAsString(Events.TITLE))
        assertEquals(1L, saved.getAsLong(Events.CALENDAR_ID).toLong())
        assertEquals(updated.exportOccurrence(date.plusDays(2), "Agenda").beginMillis, saved.getAsLong(Events.DTSTART).toLong())
        val binding = GoogleEventLinkStore(application, base, "ana").links("team-1", "event-1").single()
        assertNotEquals(10L, binding.googleId)
        assertEquals(date.plusDays(2).toString(), binding.occurrence)
        assertEquals(0, sync.retry(base, "ana")); assertEquals(1, provider.batches)
    }

    @Test fun `permission is required before changing a linked event`() = runTest {
        add(); shadowOf(application).denyPermissions(Manifest.permission.WRITE_CALENDAR)
        val failure = runCatching { sync.prepare(base, "ana", "team-1", event()) }.exceptionOrNull() as GoogleSyncPermissionRequired
        assertEquals(Manifest.permission.WRITE_CALENDAR, failure.permissions.single())
        assertEquals(0, provider.deleted); assertEquals(0, provider.inserted)
        shadowOf(application).denyPermissions(Manifest.permission.READ_CALENDAR)
        val read = runCatching { sync.prepare(base, "ana", "team-1", event()) }.exceptionOrNull() as GoogleSyncPermissionRequired
        assertEquals(Manifest.permission.READ_CALENDAR, read.permissions.single())
    }

    @Test fun `delete retries Google independently after permission revocation and process recreation`() = runTest {
        add(); val plan = sync.prepare(base, "ana", "team-1", event())
        sync.enqueue(plan, null)
        shadowOf(application).denyPermissions(Manifest.permission.WRITE_CALENDAR)
        assertEquals(1, sync.retry(base, "ana")); assertEquals(1, provider.active().size)
        shadowOf(application).grantPermissions(Manifest.permission.WRITE_CALENDAR)
        assertEquals(0, PlanetGoogleSync(application).retry(base, "ana"))
        assertTrue(provider.active().isEmpty())
        assertTrue(GoogleEventLinkStore(application, base, "ana").links("team-1", "event-1").isEmpty())
    }

    @Test fun `partial failure retries only unfinished copies and atomic replacement leaves no duplicate`() = runTest {
        val original = event().copy(recurring = "daily", recurringNumber = 3)
        add(original, 0, 10); add(original, 1, 11)
        val plan = sync.prepare(base, "ana", "team-1", original)
        provider.failId = 11
        sync.enqueue(plan, original.copy(title = "Updated", revision = "2-new"))
        assertEquals(1, sync.retry(base, "ana"))
        assertEquals(2, provider.active().size); assertEquals(1, provider.inserted)
        assertEquals(setOf("11"), GoogleEventLinkStore(application, base, "ana").hiddenIds())
        provider.failId = null
        assertEquals(0, sync.retry(base, "ana"))
        assertEquals(2, provider.active().size); assertEquals(2, provider.inserted)
        assertTrue(provider.active().values.all { it.getAsString(Events.TITLE) == "Updated" })
    }

    @Test fun `lost committed replacement is recovered without inserting twice`() = runTest {
        add(); val plan = sync.prepare(base, "ana", "team-1", event())
        sync.enqueue(plan, event().copy(title = "Updated", revision = "2-new"))
        provider.loseReply = true
        assertEquals(1, sync.retry(base, "ana")); assertEquals(1, provider.inserted)
        assertEquals(0, sync.retry(base, "ana"))
        assertEquals(1, provider.inserted); assertEquals(1, provider.active().size)
    }

    @Test fun `shortening recurrence deletes exported appearances no longer in the series`() = runTest {
        val original = event().copy(recurring = "daily", recurringNumber = 3)
        add(original, 2)
        sync.enqueue(sync.prepare(base, "ana", "team-1", original), original.copy(recurring = "none", revision = "2-new"))
        assertEquals(0, sync.retry(base, "ana")); assertTrue(provider.active().isEmpty()); assertEquals(0, provider.inserted)
        val weekly = event().copy(recurring = "weekly", recurringNumber = 6, days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertEquals(listOf(12, 14, 19, 21, 26, 28), (0..5).map { weekly.occurrenceAt(it)!!.dayOfMonth })
        for (index in 0..5) assertEquals(index, weekly.occurrenceOrdinal(weekly.occurrenceAt(index)!!))
    }

    @Test fun `temporarily missing Google account keeps pending work without touching other events`() = runTest {
        add(); val plan = sync.prepare(base, "ana", "team-1", event())
        sync.enqueue(plan, event().copy(revision = "2-new"))
        provider.account = "someone-else@gmail.com"
        assertEquals(1, sync.retry(base, "ana")); assertEquals(0, provider.deleted)
        assertEquals(1, GoogleEventLinkStore(application, base, "ana").jobs().size)
    }
}
