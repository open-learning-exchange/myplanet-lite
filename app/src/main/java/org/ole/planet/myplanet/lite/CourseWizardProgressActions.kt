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
    val saved = runCatching { saveStepPassProgress(stepNumber) }.getOrDefault(false)
    if (!saved) {
        passStore.addPendingPass(id, stepNumber)
    }
    passStore.addPassedSteps(id, listOf(stepNumber))
    passedStepNumbers.add(stepNumber)
}

suspend fun CourseWizardActivity.flushPendingStepPasses() {
    val id = courseId?.takeIf { it.isNotBlank() } ?: return
    if (!NetworkUtils.isDeviceOnline(this)) return
    passStore.getPendingPasses(id).sorted().forEach { stepNumber ->
        if (runCatching { saveStepPassProgress(stepNumber) }.getOrDefault(false)) {
            passStore.removePendingPass(id, stepNumber)
        }
    }
}

private suspend fun CourseWizardActivity.saveStepPassProgress(stepNumber: Int): Boolean {
    val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return false
    val creds = credentials ?: return false
    val id = courseId?.takeIf { it.isNotBlank() } ?: return false
    if (!NetworkUtils.isDeviceOnline(this)) return false
    val lookup = coursesRepository.fetchCoursesProgressDocuments(normalizedBase, creds, listOf(id), stepNumber)
    if (lookup.isFailure) return false
    val existingDoc = lookup.getOrNull()?.get(id)
    if (existingDoc?.passed == true) return true
    val now = System.currentTimeMillis()
    val document = DashboardCoursesRepository.CourseProgressUpdateDocument(
        id = existingDoc?.id,
        rev = existingDoc?.rev,
        userId = "org.couchdb.user:${creds.username}",
        courseId = id,
        stepNum = stepNumber,
        passed = true,
        createdOn = existingDoc?.createdOn
            ?: DashboardServerPreferences.getServerCode(applicationContext),
        parentCode = existingDoc?.parentCode
            ?: DashboardServerPreferences.getServerParentCode(applicationContext),
        createdDate = existingDoc?.createdDate ?: now,
        updatedDate = now,
        androidId = existingDoc?.androidId,
        app = resolveCourseProgressApp(existingDoc),
    )
    val result = coursesRepository.saveCourseProgress(normalizedBase, creds, listOf(document))
        .getOrNull() ?: return false
    return result.any { it.ok == true || (!it.id.isNullOrBlank() && !it.rev.isNullOrBlank()) }
}

internal fun CourseWizardActivity.isPassRecordStep(stepNumber: Int?): Boolean =
    stepNumber != null && steps.getOrNull(stepNumber - 1)?.requiresPass == true

internal fun CourseWizardActivity.passedOnReaching(stepNumber: Int): Boolean =
    !isPassRecordStep(stepNumber) || stepNumber in passedStepNumbers

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
    val progressDoc = (existingDoc ?: cachedProgressDocument)?.takeUnless { isPassRecordStep(it.stepNum) }
    val now = System.currentTimeMillis()
    val document = DashboardCoursesRepository.CourseProgressUpdateDocument(
        id = progressDoc?.id,
        rev = progressDoc?.rev,
        userId = "org.couchdb.user:${creds.username}",
        courseId = id,
        stepNum = targetStepNumber,
        passed = passedOnReaching(targetStepNumber),
        createdOn = progressDoc?.createdOn
            ?: DashboardServerPreferences.getServerCode(applicationContext),
        parentCode = progressDoc?.parentCode
            ?: DashboardServerPreferences.getServerParentCode(applicationContext),
        createdDate = progressDoc?.createdDate ?: now,
        updatedDate = now,
        androidId = progressDoc?.androidId,
        app = resolveCourseProgressApp(progressDoc),
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
            parentCode = document.parentCode,
            androidId = document.androidId,
            app = document.app,
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

    val reusableDoc = existingDoc?.takeUnless { isPassRecordStep(it.stepNum) }
    val now = System.currentTimeMillis()
    val document = DashboardCoursesRepository.CourseProgressUpdateDocument(
        id = reusableDoc?.id,
        rev = reusableDoc?.rev,
        userId = "org.couchdb.user:${creds.username}",
        courseId = id,
        stepNum = maxPendingStep,
        passed = passedOnReaching(maxPendingStep),
        createdOn = reusableDoc?.createdOn
            ?: DashboardServerPreferences.getServerCode(applicationContext),
        parentCode = reusableDoc?.parentCode
            ?: DashboardServerPreferences.getServerParentCode(applicationContext),
        createdDate = reusableDoc?.createdDate ?: now,
        updatedDate = now,
        androidId = reusableDoc?.androidId,
        app = resolveCourseProgressApp(reusableDoc),
    )

    val result = coursesRepository.saveCourseProgress(normalizedBase, creds, listOf(document))
    if (result.isSuccess) {
        removePendingProgressBatch(id, pendingSteps.toSet())
        val persistedDoc = result.getOrNull()
            ?.firstOrNull { it.ok == true || (!it.id.isNullOrBlank() && !it.rev.isNullOrBlank()) }
        val resolvedId = persistedDoc?.id ?: reusableDoc?.id
        val resolvedRev = persistedDoc?.rev ?: reusableDoc?.rev
        cachedProgressDocument = DashboardCoursesRepository.CourseProgressDocument(
            id = resolvedId,
            rev = resolvedRev,
            courseId = id,
            stepNum = maxPendingStep,
            passed = document.passed,
            createdDate = document.createdDate,
            updatedDate = now,
            createdOn = document.createdOn,
            parentCode = document.parentCode,
            androidId = document.androidId,
            app = document.app,
        )
    }
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
