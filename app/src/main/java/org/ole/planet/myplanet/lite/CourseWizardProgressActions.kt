package org.ole.planet.myplanet.lite

import android.content.Intent
import android.app.Activity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.util.CourseStepGating
import org.ole.planet.myplanet.lite.util.NetworkUtils
import org.ole.planet.myplanet.lite.util.PlanetAppIdentity

suspend fun CourseWizardActivity.resolveInitialStepIndex(fallbackIndex: Int): Int {
    val progressIndex = resolveProgressStepIndex(fallbackIndex)
    loadPassedSteps()
    return CourseStepGating.reachableStepIndex(progressIndex, steps.map { it.requiresPass }, passedStepNumbers)
}

private suspend fun CourseWizardActivity.resolveProgressStepIndex(fallbackIndex: Int): Int {
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

suspend fun CourseWizardActivity.loadPassedSteps() {
    if (steps.none { it.requiresPass }) return
    val id = courseId?.takeIf { it.isNotBlank() } ?: return
    passedStepNumbers.addAll(passStore.getPassedSteps(id))
    val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return
    val creds = credentials ?: return
    val fetched = coursesRepository.fetchCoursesPassedSteps(normalizedBase, creds, listOf(id))
        .getOrNull()?.get(id) ?: return
    passedStepNumbers.addAll(fetched)
    passStore.addPassedSteps(id, fetched)
}

suspend fun CourseWizardActivity.recordStepPass(stepNumber: Int) {
    val id = courseId?.takeIf { it.isNotBlank() } ?: return
    passStore.addPassedSteps(id, listOf(stepNumber))
    passedStepNumbers.add(stepNumber)
    if (!runCatching { saveStepProgress(stepNumber, passed = true) }.getOrDefault(false)) {
        enqueuePendingProgress(id, stepNumber)
    }
}

internal fun progressToWrite(existing: DashboardCoursesRepository.CourseProgressDocument?, passed: Boolean): Boolean? =
    when {
        existing == null -> passed
        existing.passed == true || !passed -> null
        else -> true
    }

private fun CourseWizardActivity.passesOnOpen(stepNumber: Int): Boolean =
    steps.getOrNull(stepNumber - 1)?.passesOnOpen == true

private suspend fun CourseWizardActivity.saveStepProgress(stepNumber: Int, passed: Boolean): Boolean {
    val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return false
    val creds = credentials ?: return false
    val id = courseId?.takeIf { it.isNotBlank() } ?: return false
    if (!NetworkUtils.isDeviceOnline(this)) return false
    val lookup = coursesRepository.fetchCoursesProgressDocuments(normalizedBase, creds, listOf(id), stepNumber)
    if (lookup.isFailure) return false
    val existingDoc = lookup.getOrNull()?.get(id)
    val write = progressToWrite(existingDoc, passed)
    if (write == null) {
        rememberFurthestStep(existingDoc)
        return true
    }
    val now = System.currentTimeMillis()
    val document = DashboardCoursesRepository.CourseProgressUpdateDocument(
        id = existingDoc?.id,
        rev = existingDoc?.rev,
        userId = "org.couchdb.user:${creds.username}",
        courseId = id,
        stepNum = stepNumber,
        passed = write,
        createdOn = existingDoc?.createdOn
            ?: DashboardServerPreferences.getServerCode(applicationContext),
        parentCode = existingDoc?.parentCode
            ?: DashboardServerPreferences.getServerParentCode(applicationContext),
        createdDate = existingDoc?.createdDate ?: now,
        updatedDate = now,
        androidId = existingDoc?.androidId,
        app = resolveCourseProgressApp(existingDoc),
    )
    val persisted = coursesRepository.saveCourseProgress(normalizedBase, creds, listOf(document)).getOrNull()
        ?.firstOrNull { it.ok == true || (!it.id.isNullOrBlank() && !it.rev.isNullOrBlank()) }
        ?: return false
    rememberFurthestStep(
        DashboardCoursesRepository.CourseProgressDocument(
            id = persisted.id ?: document.id,
            rev = persisted.rev ?: document.rev,
            courseId = id,
            stepNum = stepNumber,
            passed = write,
            createdDate = document.createdDate,
            updatedDate = now,
            createdOn = document.createdOn,
            parentCode = document.parentCode,
            androidId = document.androidId,
            app = document.app,
        )
    )
    return true
}

private fun CourseWizardActivity.rememberFurthestStep(document: DashboardCoursesRepository.CourseProgressDocument?) {
    val stepNum = document?.stepNum ?: return
    if (stepNum >= (cachedProgressDocument?.stepNum ?: 0)) {
        cachedProgressDocument = document
    }
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
    if (baseUrl.isNullOrBlank() || credentials == null) return
    val id = courseId?.takeIf { it.isNotBlank() } ?: return
    if (!saveStepProgress(targetStepNumber, passesOnOpen(targetStepNumber))) {
        enqueuePendingProgress(id, targetStepNumber)
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
    if (baseUrl.isNullOrBlank() || credentials == null) return
    val id = courseId?.takeIf { it.isNotBlank() } ?: return
    if (!NetworkUtils.isDeviceOnline(this)) return
    val passedSteps = passStore.getPassedSteps(id)
    val saved = getPendingProgress(id).filter { step ->
        runCatching { saveStepProgress(step, passesOnOpen(step) || step in passedSteps) }.getOrDefault(false)
    }
    removePendingProgressBatch(id, saved.toSet())
}

internal fun resolveCourseProgressApp(existingDocument: DashboardCoursesRepository.CourseProgressDocument?): String? =
    if (existingDocument == null) PlanetAppIdentity.APP_ID else existingDocument.app

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
