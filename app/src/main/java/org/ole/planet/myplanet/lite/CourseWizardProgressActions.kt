package org.ole.planet.myplanet.lite

import android.content.Intent
import android.app.Activity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.util.NetworkUtils

suspend fun CourseWizardActivity.resolveInitialStepIndex(fallbackIndex: Int): Int {
    if (cachedProgressDocument != null) {
        val progressStep = cachedProgressDocument?.stepNum
        val resolvedIndex = progressStep?.minus(1) ?: fallbackIndex
        return resolvedIndex.coerceIn(0, steps.lastIndex)
    }
    val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return fallbackIndex
    val creds = credentials ?: return fallbackIndex
    val id = courseId?.takeIf { it.isNotBlank() } ?: return fallbackIndex
    getPendingProgress(id).maxOrNull()
    val progressDocuments = coursesRepository.fetchCoursesProgressDocuments(
        normalizedBase,
        creds,
        listOf(id)
    ).getOrNull()
    cachedProgressDocument = progressDocuments?.get(id)
    val progressStep = cachedProgressDocument?.stepNum
    val resolvedIndex = progressStep?.minus(1) ?: fallbackIndex
    return resolvedIndex.coerceIn(0, steps.lastIndex)
}

fun CourseWizardActivity.maybeAutoCompleteFirstStep() {
    if (hasAutoCompletedFirstStep || currentIndex != 0) return
    hasAutoCompletedFirstStep = true
    lifecycleScope.launch {
        runCatching { updateCourseProgressIfNeeded(1) }
    }
}

fun CourseWizardActivity.advanceToNextStep() {
    val targetIndex = (currentIndex + 1).coerceAtMost(steps.lastIndex)
    val targetStepNumber = targetIndex + 1
    lifecycleScope.launch {
        runCatching { updateCourseProgressIfNeeded(targetStepNumber) }
    }
    currentIndex = targetIndex
    bindStep()
}

suspend fun CourseWizardActivity.updateCourseProgressIfNeeded(targetStepNumber: Int) {
    val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return
    val creds = credentials ?: return
    val id = courseId?.takeIf { it.isNotBlank() } ?: return
    if (!NetworkUtils.isDeviceOnline(this)) {
        enqueuePendingProgress(id, targetStepNumber)
        return
    }
    val existingDocuments = coursesRepository.fetchCoursesProgressDocuments(normalizedBase, creds, listOf(id))
        .getOrNull()
    val existingDoc = existingDocuments?.get(id)
    val existingStep = existingDoc?.stepNum ?: 0
    if (existingStep >= targetStepNumber) return
    val progressDoc = existingDoc ?: cachedProgressDocument
    val now = System.currentTimeMillis()
    val document = DashboardCoursesRepository.CourseProgressUpdateDocument(
        id = progressDoc?.id,
        rev = progressDoc?.rev,
        userId = "org.couchdb.user:${creds.username}",
        courseId = id,
        stepNum = targetStepNumber,
        passed = true,
        createdOn = progressDoc?.createdOn
            ?: DashboardServerPreferences.getServerCode(applicationContext),
        parentCode = progressDoc?.parentCode
            ?: DashboardServerPreferences.getServerParentCode(applicationContext),
        createdDate = progressDoc?.createdDate ?: now,
        updatedDate = now
    )
    val saveResult = coursesRepository.saveCourseProgress(normalizedBase, creds, listOf(document))
    if (saveResult.isFailure) {
        enqueuePendingProgress(id, targetStepNumber)
    } else {
        val persistedDoc = saveResult.getOrNull()
            ?.firstOrNull { it.ok == true || (!it.id.isNullOrBlank() && !it.rev.isNullOrBlank()) }
        val resolvedId = persistedDoc?.id ?: document.id
        val resolvedRev = persistedDoc?.rev ?: document.rev
        cachedProgressDocument = DashboardCoursesRepository.CourseProgressDocument(
            id = resolvedId,
            rev = resolvedRev,
            courseId = document.courseId,
            stepNum = document.stepNum,
            passed = document.passed,
            createdDate = document.createdDate,
            updatedDate = document.updatedDate,
            createdOn = document.createdOn,
            parentCode = document.parentCode
        )
    }
}

suspend fun CourseWizardActivity.finishCourse() {
    runCatching { updateCourseProgressIfNeeded(steps.size) }
    setResult(
        Activity.RESULT_OK,
        Intent().apply {
            putExtra(CourseWizardActivity.EXTRA_RESULT_COURSE_ID, courseId)
            putExtra(CourseWizardActivity.EXTRA_RESULT_PROGRESS_PERCENT, 100)
            putExtra(CourseWizardActivity.EXTRA_RESULT_CURRENT_STEP, steps.lastIndex)
        }
    )
    finish()
}

suspend fun CourseWizardActivity.flushPendingCourseProgress() {
    val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return
    val creds = credentials ?: return
    val id = courseId?.takeIf { it.isNotBlank() } ?: return
    if (!NetworkUtils.isDeviceOnline(this)) return
    val pendingSteps = getPendingProgress(id)
    if (pendingSteps.isEmpty()) return

    val existingDoc = coursesRepository.fetchCoursesProgressDocuments(normalizedBase, creds, listOf(id))
        .getOrNull()?.get(id)
    val existingStep = existingDoc?.stepNum ?: 0
    val maxPendingStep = pendingSteps.maxOrNull() ?: 0

    if (maxPendingStep <= existingStep) {
        removePendingProgressBatch(id, pendingSteps.toSet())
        return
    }

    val now = System.currentTimeMillis()
    val document = DashboardCoursesRepository.CourseProgressUpdateDocument(
        id = existingDoc?.id,
        rev = existingDoc?.rev,
        userId = "org.couchdb.user:${creds.username}",
        courseId = id,
        stepNum = maxPendingStep,
        passed = true,
        createdOn = existingDoc?.createdOn
            ?: DashboardServerPreferences.getServerCode(applicationContext),
        parentCode = existingDoc?.parentCode
            ?: DashboardServerPreferences.getServerParentCode(applicationContext),
        createdDate = existingDoc?.createdDate ?: now,
        updatedDate = now
    )

    val result = coursesRepository.saveCourseProgress(normalizedBase, creds, listOf(document))
    if (result.isSuccess) {
        removePendingProgressBatch(id, pendingSteps.toSet())
        val persistedDoc = result.getOrNull()
            ?.firstOrNull { it.ok == true || (!it.id.isNullOrBlank() && !it.rev.isNullOrBlank()) }
        val resolvedId = persistedDoc?.id ?: existingDoc?.id
        val resolvedRev = persistedDoc?.rev ?: existingDoc?.rev
        cachedProgressDocument = DashboardCoursesRepository.CourseProgressDocument(
            id = resolvedId,
            rev = resolvedRev,
            courseId = id,
            stepNum = maxPendingStep,
            passed = true,
            createdDate = document.createdDate,
            updatedDate = now,
            createdOn = document.createdOn,
            parentCode = document.parentCode
        )
    }
}

suspend fun CourseWizardActivity.flushPendingExamSubmissions() {
    localSurveyRepository.flushPendingSurveyOutbox("exam")
}

fun CourseWizardActivity.enqueuePendingProgress(courseId: String, stepNumber: Int) {
    val updated = (getPendingProgress(courseId) + stepNumber).distinct().sorted()
    val array = JSONArray()
    updated.forEach { array.put(it) }
    pendingProgressPrefs.edit { putString(progressKey(courseId), array.toString()) }
}

fun CourseWizardActivity.getPendingProgress(courseId: String): List<Int> {
    val raw = pendingProgressPrefs.getString(progressKey(courseId), null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val step = array.optInt(index)
            if (step > 0) step else null
        }
    }.getOrDefault(emptyList())
}

fun CourseWizardActivity.removePendingProgressBatch(courseId: String, stepNumbers: Set<Int>) {
    if (stepNumbers.isEmpty()) return
    val remaining = getPendingProgress(courseId).filterNot { stepNumbers.contains(it) }
    if (remaining.isEmpty()) {
        pendingProgressPrefs.edit { remove(progressKey(courseId)) }
        return
    }
    val array = JSONArray()
    remaining.forEach { array.put(it) }
    pendingProgressPrefs.edit { putString(progressKey(courseId), array.toString()) }
}

fun CourseWizardActivity.progressKey(courseId: String): String = "course_progress_$courseId"
