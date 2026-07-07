package org.ole.planet.myplanet.lite

import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument

data class StepDisplay(
    val title: String,
    val description: String,
    val resources: List<CourseItem.LessonResource>,
    val survey: SurveyDocument? = null,
    val exam: SurveyDocument? = null
)