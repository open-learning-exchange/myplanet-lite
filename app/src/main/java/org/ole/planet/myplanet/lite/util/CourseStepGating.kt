package org.ole.planet.myplanet.lite.util

object CourseStepGating {
    const val STATUS_COMPLETE = "complete"

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

    fun blockingStepIndex(
        requiresPass: List<Boolean>,
        passedStepNumbers: Set<Int>,
    ): Int = requiresPass.indices.firstOrNull { requiresPass[it] && (it + 1) !in passedStepNumbers } ?: -1

    fun reachableStepIndex(
        stepIndex: Int,
        requiresPass: List<Boolean>,
        passedStepNumbers: Set<Int>,
    ): Int {
        val blocking = blockingStepIndex(requiresPass, passedStepNumbers)
        return if (blocking > -1) stepIndex.coerceAtMost(blocking) else stepIndex
    }
}
