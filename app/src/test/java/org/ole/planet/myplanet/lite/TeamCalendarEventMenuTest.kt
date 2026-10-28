package org.ole.planet.myplanet.lite

import android.content.Context
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.EventActionPermissions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TeamCalendarEventMenuTest {
    private fun visible(permissions: EventActionPermissions, alreadyAdded: Boolean): List<Int> {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MyPlanetLite)
        val popup = PopupMenu(context, View(context))
        popup.menuInflater.inflate(R.menu.menu_team_calendar_event, popup.menu)
        configureCalendarActions(popup.menu, permissions, alreadyAdded, true)
        return (0 until popup.menu.size()).map { popup.menu.getItem(it) }.filter { it.isVisible }.map { it.itemId }
    }
    @Test fun `creator sees edit Google and delete in order`() {
        assertEquals(listOf(R.id.calendarActionEdit, R.id.calendarActionGoogle, R.id.calendarActionDelete),
            visible(EventActionPermissions(true, true), false))
    }
    @Test fun `existing Google event is omitted without affecting edit and delete`() {
        assertEquals(listOf(R.id.calendarActionEdit, R.id.calendarActionDelete),
            visible(EventActionPermissions(true, true), true))
    }
    @Test fun `owner or admin sees delete but cannot edit someone elses event`() {
        assertEquals(listOf(R.id.calendarActionGoogle, R.id.calendarActionDelete),
            visible(EventActionPermissions(false, true), false))
        assertEquals(listOf(R.id.calendarActionGoogle), visible(EventActionPermissions(false, false), false))
    }
}
