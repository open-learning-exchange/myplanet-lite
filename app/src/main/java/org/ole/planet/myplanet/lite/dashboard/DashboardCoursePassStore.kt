/*
 * Author: Claude
 * Email: noreply@anthropic.com
 * Creation date: 2026-10-24
 */

package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

// Course steps whose exam pass this device knows about, so the step lock also works offline.
// A pass is never taken away once known.
class DashboardCoursePassStore(
    context: Context,
    private val username: String? = null,
) {
    private val prefs = SecurePreferencesProvider.getEncryptedPreferences(context, PREFS_NAME)

    fun getPassedSteps(courseId: String?): Set<Int> = readSteps(namespacedKey(KEY_PASSED, courseId))

    fun addPassedSteps(
        courseId: String?,
        stepNumbers: Collection<Int>,
    ) {
        addSteps(namespacedKey(KEY_PASSED, courseId), stepNumbers)
    }

    // Passes that couldn't be saved to the server yet; flushed with the course's pending progress
    fun getPendingPasses(courseId: String?): Set<Int> = readSteps(namespacedKey(KEY_PENDING, courseId))

    fun addPendingPass(
        courseId: String?,
        stepNumber: Int,
    ) {
        addSteps(namespacedKey(KEY_PENDING, courseId), listOf(stepNumber))
    }

    fun removePendingPass(
        courseId: String?,
        stepNumber: Int,
    ) {
        val key = namespacedKey(KEY_PENDING, courseId) ?: return
        val remaining = readSteps(key) - stepNumber
        if (remaining.isEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, remaining.sorted().joinToString(",")).apply()
        }
    }

    private fun readSteps(key: String?): Set<Int> {
        key ?: return emptySet()
        val stored = prefs.getString(key, null).orEmpty()
        return stored
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .toSet()
    }

    private fun addSteps(
        key: String?,
        stepNumbers: Collection<Int>,
    ) {
        key ?: return
        val updated = readSteps(key) + stepNumbers.filter { it > 0 }
        if (updated.isEmpty()) return
        prefs.edit().putString(key, updated.sorted().joinToString(",")).apply()
    }

    private fun namespacedKey(
        prefix: String,
        courseId: String?,
    ): String? {
        val course = courseId?.takeIf { it.isNotBlank() } ?: return null
        val user = username?.takeIf { it.isNotBlank() } ?: return "$prefix:$course"
        return "$prefix:$user:$course"
    }

    companion object {
        private const val PREFS_NAME = "encrypted_course_step_passes"
        private const val KEY_PASSED = "passed"
        private const val KEY_PENDING = "pending"
    }
}
