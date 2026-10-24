package org.ole.planet.myplanet.lite

import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument

data class StepDisplay(
    val title: String,
    val description: String,
    val resources: List<CourseItem.LessonResource>,
    val survey: SurveyDocument? = null,
    val exam: SurveyDocument? = null,
    val passingRequired: Boolean = false
)
// A step whose exam must be passed before the next step unlocks
val StepDisplay.requiresPass: Boolean
    get() = passingRequired && exam?.questions?.isNotEmpty() == true

// Next waits for the saved pass, not just an attempt: later steps unlock from stored passes
fun isStepPassPending(
    step: StepDisplay,
    stepIndex: Int,
    passedStepNumbers: Set<Int>,
): Boolean = step.requiresPass && (stepIndex + 1) !in passedStepNumbers
