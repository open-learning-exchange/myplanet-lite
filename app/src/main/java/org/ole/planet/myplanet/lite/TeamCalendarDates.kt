package org.ole.planet.myplanet.lite

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

internal fun calendarMonthDays(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate> {
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val start = first.minusDays(offset.toLong())
    return (0L until 42L).map(start::plusDays)
}
