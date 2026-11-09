package org.ole.planet.myplanet.lite

import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.PlanetCalendarExport
import org.ole.planet.myplanet.lite.dashboard.PlanetGoogleDuplicateChecker
import org.ole.planet.myplanet.lite.dashboard.planetGoogleMarker
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlanetGoogleDuplicateCheckerTest {
    private lateinit var provider: CalendarProviderFixture
    private lateinit var checker: PlanetGoogleDuplicateChecker
    private val marker = planetGoogleMarker("https://planet.example", "team-1", "event-1", "2026-10-13")
    @Before fun setup() {
        provider = CalendarProviderFixture()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        checker = PlanetGoogleDuplicateChecker(ApplicationProvider.getApplicationContext<Context>().contentResolver)
    }
    private fun export() = PlanetCalendarExport("Google meeting", "Personal event", "Online", provider.begin, provider.end, false, "UTC")

    @Test fun `previously exported copy matches all original details including hidden calendars`() {
        provider.visible = false
        assertTrue(checker.exists(export(), marker))
        assertTrue(provider.instanceSelection.contains(CalendarContract.Events.DELETED))
        assertTrue(provider.instanceSelection.contains(CalendarContract.Events.STATUS))
        assertFalse(checker.exists(export().copy(location = "Other room"), marker))
        assertFalse(checker.exists(export().copy(description = "Different event"), marker))
        assertFalse(checker.exists(export().copy(beginMillis = provider.begin + 60000), marker))
    }
    @Test fun `identified copy remains linked after changes inside Google`() {
        provider.description = "Edited description\n\n$marker"
        provider.title = "Edited in Google"
        assertTrue(checker.exists(export().copy(beginMillis = provider.begin + 86400000), marker))
        val other = planetGoogleMarker("https://another.example", "team-1", "event-1", "2026-10-13")
        assertFalse(checker.exists(export(), other))
    }
    @Test fun `cancelled export is not treated as saved and a later actual copy is detected`() {
        val export = export()
        provider.title = null
        assertFalse(checker.exists(export, marker))
        provider.title = export.title
        assertTrue(checker.exists(export, marker))
        assertNotEquals(marker, planetGoogleMarker("https://planet.example", "team-1", "event-1", "2026-10-14"))
    }
    @Test fun `Google HTML descriptions still identify linked copies and older exports`() {
        provider.description = "<b>Personal event</b>"
        assertTrue(checker.exists(export(), marker))
        val url = marker.substringAfter("Planet: ").replace("&", "&amp;")
        val original = export()
        provider.description = "<p>Changed text</p><p>Planet: <a href=\"$url\">$url</a></p>"
        provider.title = "Changed title"
        provider.begin += 86400000
        assertTrue(checker.exists(original, marker))
    }

}
