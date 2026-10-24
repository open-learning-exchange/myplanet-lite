/*
 * Author: Claude
 * Email: noreply@anthropic.com
 * Creation date: 2026-10-24
 */

package org.ole.planet.myplanet.lite.util

object CourseStepGating {
    const val STATUS_COMPLETE = "complete"

    // Compared without dividing: 29 / 50 * 100 is 57.99999999999999 and would miss a 58% pass mark
    fun isPassingScore(
        grade: Int,
        totalMarks: Int,
        passingPercentage: Int,
        allAnswersCorrect: Boolean,
    ): Boolean =
        if (totalMarks > 0) {
            grade.toLong() * 100 >= passingPercentage.toLong() * totalMarks
        } else {
            allAnswersCorrect
        }

    // Index of the first step whose exam must be passed and isn't (-1 when none); every later step is locked behind it
    fun blockingStepIndex(
        requiresPass: List<Boolean>,
        passedStepNumbers: Set<Int>,
    ): Int = requiresPass.indices.firstOrNull { requiresPass[it] && (it + 1) !in passedStepNumbers } ?: -1

    // A start or resume position never lands past the step that must be passed
    fun reachableStepIndex(
        stepIndex: Int,
        requiresPass: List<Boolean>,
        passedStepNumbers: Set<Int>,
    ): Int {
        val blocking = blockingStepIndex(requiresPass, passedStepNumbers)
        return if (blocking > -1) stepIndex.coerceAtMost(blocking) else stepIndex
    }
}
