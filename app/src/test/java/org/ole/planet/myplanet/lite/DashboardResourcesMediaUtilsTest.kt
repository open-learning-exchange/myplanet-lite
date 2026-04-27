package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DashboardResourcesMediaUtilsTest(
    private val sourceHeight: Int,
    private val expectedHeight: Int
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "sourceHeight: {0} -> expected: {1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                // sourceHeight >= 1080 -> 720
                arrayOf(1080, 720),
                arrayOf(2160, 720),

                // sourceHeight >= 720 -> 720
                arrayOf(720, 720),
                arrayOf(1079, 720),

                // sourceHeight >= 576 -> 576
                arrayOf(576, 576),
                arrayOf(719, 576),

                // else -> 480
                arrayOf(575, 480),
                arrayOf(480, 480),
                arrayOf(240, 480),
                arrayOf(0, 480),
                arrayOf(-1, 480)
            )
        }
    }

    @Test
    fun defaultVideoHeightSelection_isCorrect() {
        val result = DashboardResourcesMediaUtils.defaultVideoHeightSelection(sourceHeight)
        assertEquals("Failed for source height $sourceHeight", expectedHeight, result)
    }
}
