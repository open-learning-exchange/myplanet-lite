package org.ole.planet.myplanet.lite

import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository.CourseDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository.CourseStep
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

data class CourseItem(
    val id: String,
    val title: String,
    val description: String,
    val coverPath: String?,
    val steps: List<LessonStep>,
    val rating: Double,
    val progressPercent: Int,
    val currentStep: Int?,
    val downloadSource: OfflineCourseStorage.DownloadSource = OfflineCourseStorage.DownloadSource.MY_COURSES
) {
    val lessonCount: Int
        get() = steps.size

    data class LessonStep(
        val title: String,
        val description: String,
        val mediaTypes: List<String>,
        val resources: List<LessonResource> = emptyList(),
        val survey: SurveyDocument? = null,
        val exam: SurveyDocument? = null
    ) : java.io.Serializable

    data class LessonResource(
        val id: String,
        val filename: String,
        val mediaType: String
    ) : java.io.Serializable
}

data class CourseCategory(
    val id: String?,
    val name: String
)

fun CourseDocument.toCourseItem(
    defaultTitle: String,
    stepNum: Int?
): CourseItem {
    val mappedSteps = steps.mapNotNull { it.toLessonStep() }
    val random = SecureRandom(id.orEmpty().toByteArray()).asKotlinRandom()
    val completedSteps = if (mappedSteps.isNotEmpty() && stepNum != null) {
        stepNum.coerceAtLeast(0).coerceAtMost(mappedSteps.size)
    } else {
        null
    }
    val progressPercent = if (mappedSteps.isNotEmpty() && completedSteps != null) {
        (completedSteps * 100.0 / mappedSteps.size)
            .toInt()
            .coerceIn(0, 100)
    } else {
        random.nextInt(15, 95)
    }
    return CourseItem(
        id = id.orEmpty(),
        title = courseTitle?.takeIf { it.isNotBlank() } ?: defaultTitle,
        description = description.orEmpty(),
        coverPath = cover?.takeIf { it.isNotBlank() },
        steps = mappedSteps,
        rating = random.nextDouble(3.5, 5.0),
        progressPercent = progressPercent,
        currentStep = completedSteps?.minus(1)?.coerceIn(0, mappedSteps.lastIndex)
    )
}

private fun CourseStep.toLessonStep(): CourseItem.LessonStep? {
    val title = stepTitle?.takeIf { it.isNotBlank() } ?: return null
    val mediaTypes = buildList {
        resources
            ?.mapNotNull { resource -> mapCourseMediaType(resource.mediaType) }
            ?.forEach { add(it) }
        if (survey != null) {
            add("survey")
        }
        if (exam != null) {
            add("exam")
        }
    }
    val mappedResources = resources.orEmpty().flatMap { resource ->
        val resourceId = resource.id?.takeIf { it.isNotBlank() } ?: return@flatMap emptyList()
        val filenames = when {
            resource.attachments.isNotEmpty() -> resource.attachments.keys
            !resource.filename.isNullOrBlank() -> listOf(resource.filename)
            else -> emptyList()
        }
        val mediaType = mapCourseMediaType(resource.mediaType)
            ?: resource.mediaType?.lowercase()?.trim().orEmpty()
        filenames.map { name ->
            CourseItem.LessonResource(
                id = resourceId,
                filename = name,
                mediaType = mediaType
            )
        }
    }
    return CourseItem.LessonStep(
        title = title,
        description = description.orEmpty(),
        mediaTypes = mediaTypes,
        resources = mappedResources,
        survey = survey,
        exam = exam
    )
}

private fun mapCourseMediaType(raw: String?): String? {
    val normalized = raw?.lowercase()?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return when {
        normalized.contains("video") -> "video"
        normalized.contains("pdf") -> "pdf"
        normalized.contains("image") -> "image"
        normalized.contains("audio") -> "audio"
        else -> normalized
    }
}