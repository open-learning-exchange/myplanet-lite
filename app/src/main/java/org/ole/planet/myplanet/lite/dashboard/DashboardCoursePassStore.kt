package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

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
    }
}
