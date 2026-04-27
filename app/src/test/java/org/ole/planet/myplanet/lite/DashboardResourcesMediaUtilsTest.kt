package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardResourcesMediaUtilsTest {

    @Test
    fun testAllowedVideoHeights1080AndAbove() {
        // >= 1080 -> listOf(480, 576, 720, 1080)
        val expected = listOf(480, 576, 720, 1080)
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(1080))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(1920))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(2160)) // 4k
    }

    @Test
    fun testAllowedVideoHeights720To1079() {
        // >= 720 -> listOf(480, 576, 720)
        val expected = listOf(480, 576, 720)
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(720))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(800))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(1079))
    }

    @Test
    fun testAllowedVideoHeights576To719() {
        // >= 576 -> listOf(480, 576)
        val expected = listOf(480, 576)
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(576))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(600))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(719))
    }

    @Test
    fun testAllowedVideoHeightsBelow576() {
        // else -> listOf(480)
        val expected = listOf(480)
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(575))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(480))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(360))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(240))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(0))
        assertEquals(expected, DashboardResourcesMediaUtils.allowedVideoHeights(-10))
    }
}
