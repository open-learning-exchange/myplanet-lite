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

val StepDisplay.requiresPass: Boolean
    get() = passingRequired && exam?.questions?.isNotEmpty() == true

fun isStepPassPending(
    step: StepDisplay,
    stepIndex: Int,
    passedStepNumbers: Set<Int>,
): Boolean = step.requiresPass && (stepIndex + 1) !in passedStepNumbers
