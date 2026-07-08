package org.ole.planet.myplanet.lite

import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository.CourseDocument
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
    val steps = steps.mapNotNull { step ->
        val title = step.stepTitle?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val mediaTypes = buildList {
            step.resources
                ?.mapNotNull { resource -> mapCourseMediaType(resource.mediaType) }
                ?.forEach { add(it) }
            if (step.survey != null) {
                add("survey")
            }
            if (step.exam != null) {
                add("exam")
            }
        }
        val resources = step.resources.orEmpty().flatMap { resource ->
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
        CourseItem.LessonStep(
            title = title,
            description = step.description.orEmpty(),
            mediaTypes = mediaTypes,
            resources = resources,
            survey = step.survey,
            exam = step.exam
        )
    }
    val random = SecureRandom(id.orEmpty().toByteArray()).asKotlinRandom()
    val completedSteps = if (steps.isNotEmpty() && stepNum != null) {
        stepNum.coerceAtLeast(0).coerceAtMost(steps.size)
    } else {
        null
    }
    val progressPercent = if (steps.isNotEmpty() && completedSteps != null) {
        (completedSteps * 100.0 / steps.size)
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
        steps = steps,
        rating = random.nextDouble(3.5, 5.0),
        progressPercent = progressPercent,
        currentStep = completedSteps?.minus(1)?.coerceIn(0, steps.lastIndex)
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