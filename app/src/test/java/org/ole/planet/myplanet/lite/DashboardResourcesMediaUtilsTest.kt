package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardResourcesMediaUtilsTest {

    @Test
    fun testFormatDurationMs() {
        // 0 ms
        assertEquals("00:00", DashboardResourcesMediaUtils.formatDurationMs(0L))

        // < 1 minute (e.g. 45 seconds = 45000 ms)
        assertEquals("00:45", DashboardResourcesMediaUtils.formatDurationMs(45000L))

        // Exactly 1 minute
        assertEquals("01:00", DashboardResourcesMediaUtils.formatDurationMs(60000L))

        // > 1 minute, < 1 hour (e.g. 12 mins 34 secs = 754000 ms)
        assertEquals("12:34", DashboardResourcesMediaUtils.formatDurationMs(754000L))

        // Exactly 1 hour
        assertEquals("1:00:00", DashboardResourcesMediaUtils.formatDurationMs(3600000L))

        // > 1 hour (e.g. 1 hour, 23 mins, 45 secs = 3600000 + 4980000 + 45000 = 5025000 ms)
        assertEquals("1:23:45", DashboardResourcesMediaUtils.formatDurationMs(5025000L))

        // Negative duration (should coerce to 0)
        assertEquals("00:00", DashboardResourcesMediaUtils.formatDurationMs(-1000L))
    }
}
