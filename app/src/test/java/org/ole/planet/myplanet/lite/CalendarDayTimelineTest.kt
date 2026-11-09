package org.ole.planet.myplanet.lite

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarDayTimelineTest {
    @Test fun `distant events preserve both hours with a single compressed gap`() {
        assertEquals(listOf(CalendarHourBand(9, 9, true), CalendarHourBand(10, 17, false),
            CalendarHourBand(18, 18, true)), calendarHourBands(setOf(18, 9)))
        assertTrue(calendarHourBands(emptySet()).isEmpty())
        assertEquals(listOf(CalendarHourBand(9, 9, true), CalendarHourBand(10, 10, true)),
            calendarHourBands(setOf(9, 10)))
    }

    @Test fun `compact gap labels remain readable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val date = LocalDate.now()
        CalendarDayTimeline.render(container, date, listOf(
            TeamCalendarEvent("a", date, "Morning", "09:00"),
            TeamCalendarEvent("b", date, "Evening", "18:00"))) { TextView(context) }
        val width = (360 * context.resources.displayMetrics.density).toInt()
        container.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
        container.layout(0, 0, width, container.measuredHeight)
        assertEquals(3, container.childCount)
        for (index in 0 until container.childCount) {
            val frame = container.getChildAt(index) as android.widget.FrameLayout
            val content = frame.getChildAt(0) as LinearLayout
            val label = content.getChildAt(0) as TextView
            assertTrue("Hour $index must have visible height", label.height >= label.lineHeight + label.paddingTop)
            assertTrue("Hour $index must have visible width", label.width > label.paddingRight)
            assertTrue(label.text.isNotBlank())
            assertTrue(content.height >= (40 * context.resources.displayMetrics.density).toInt())
        }
    }
    @Test fun `all day reminders precede only the occupied hour`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = LinearLayout(context)
        val date = LocalDate.now()
        val allDay = TeamCalendarEvent("1", date, "Reminder")
        val timed = TeamCalendarEvent("2", date, "Meeting", "09:30 – 10:30")
        CalendarDayTimeline.render(container, date, listOf(timed, allDay)) {
            TextView(context).apply { text = it.title }
        }
        assertEquals(2, container.childCount)
        assertEquals("Reminder", (container.getChildAt(0) as TextView).text)
        val nine = container.getChildAt(1) as android.widget.FrameLayout
        val row = nine.getChildAt(0) as LinearLayout
        val cards = row.getChildAt(1) as LinearLayout
        assertEquals("Meeting", (cards.getChildAt(0) as TextView).text)
        assertEquals(2, nine.childCount) // Content and the clock overlay.
    }
}
