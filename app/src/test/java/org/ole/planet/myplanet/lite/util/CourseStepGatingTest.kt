package org.ole.planet.myplanet.lite.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CourseStepGatingTest {

    @Test
    fun `a score just under the pass mark fails instead of rounding up`() {
        // 23 / 40 is 57.5%, which rounding would have turned into 58%
        assertFalse(CourseStepGating.isPassingScore(grade = 23, totalMarks = 40, passingPercentage = 58, allAnswersCorrect = false))
    }

    @Test
    fun `no step blocks when none requires a pass`() {
        assertEquals(-1, CourseStepGating.blockingStepIndex(listOf(false, false, false), emptySet()))
    }

    @Test
    fun `first step requiring an unsaved pass blocks`() {
        assertEquals(1, CourseStepGating.blockingStepIndex(listOf(false, true, true), emptySet()))
    }

    @Test
    fun `a stored pass unblocks its step and the next required one blocks`() {
        assertEquals(2, CourseStepGating.blockingStepIndex(listOf(false, true, true), setOf(2)))
        assertEquals(-1, CourseStepGating.blockingStepIndex(listOf(false, true, true), setOf(2, 3)))
    }

    @Test
    fun `reachable step stops at the blocking step`() {
        val requiresPass = listOf(false, true, false, false)
        assertEquals(1, CourseStepGating.reachableStepIndex(3, requiresPass, emptySet()))
        assertEquals(0, CourseStepGating.reachableStepIndex(0, requiresPass, emptySet()))
        assertEquals(3, CourseStepGating.reachableStepIndex(3, requiresPass, setOf(2)))
        assertEquals(3, CourseStepGating.reachableStepIndex(3, listOf(false, false, false, false), emptySet()))
    }
}
