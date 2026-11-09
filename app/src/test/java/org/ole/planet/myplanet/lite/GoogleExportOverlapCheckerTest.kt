package org.ole.planet.myplanet.lite

import android.app.Application
import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.*
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class GoogleExportOverlapCheckerTest {
    private lateinit var app: Application
    private lateinit var provider: LinkedGoogleProviderFixture
    private lateinit var export: PlanetCalendarExport
    private val marker = "Planet: https://planet.gt/spa/meetups/view/event-1?team=team-1&date=2027-01-04"
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        val prefs = app.getSharedPreferences("export_overlap", Context.MODE_PRIVATE)
        prefs.edit().clear().commit(); SecurePreferencesProvider.injectedPreferences = prefs
        provider = LinkedGoogleProviderFixture()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        val begin = LocalDate.of(2027, 1, 4).atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        export = PlanetCalendarExport("Event", "Description", "", begin, begin + 3_600_000, false, "UTC")
    }
    @After fun cleanup() = SecurePreferencesProvider.resetForTesting()
    @Test fun `new synchronization detects different overlapping Google event`() {
        provider.add(1, export.copy(title = "Other"))
        assertTrue(GoogleExportOverlapChecker(app).overlaps("team-1", export, marker))
    }
    @Test fun `own linked occurrence never creates an overlap alert`() {
        provider.add(1, export, marker)
        assertFalse(GoogleExportOverlapChecker(app).overlaps("team-1", export, marker))
        provider.add(2, export.copy(title = "Other"))
        assertTrue(GoogleExportOverlapChecker(app).overlaps("team-1", export, marker))
    }
    @Test fun `adjacent Google appointment allows new synchronization`() {
        provider.add(1, export.copy(beginMillis = export.endMillis!!, endMillis = export.endMillis!! + 3_600_000))
        assertFalse(GoogleExportOverlapChecker(app).overlaps("team-1", export, marker))
    }
}
