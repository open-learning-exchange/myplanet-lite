package org.ole.planet.myplanet.lite.util

import android.os.Build
import androidx.annotation.VisibleForTesting
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

object DateUtils {

    @VisibleForTesting
    var sdkInt: Int = Build.VERSION.SDK_INT

    fun formatBirthDate(value: String?, fallback: String): String {
        if (value.isNullOrBlank()) {
            return fallback
        }

        val targetPattern = "yyyy-MM-dd"

        return if (sdkInt >= Build.VERSION_CODES.O) {
            val date = runCatching { Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDate() }.getOrNull()
                ?: runCatching { LocalDate.parse(value) }.getOrNull()

            date?.format(java.time.format.DateTimeFormatter.ofPattern(targetPattern, Locale.getDefault()))
                ?: value
        } else {
            val inputPatterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd"
            )
            val outputFormat = SimpleDateFormat(targetPattern, Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            inputPatterns.firstNotNullOfOrNull { pattern ->
                runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(value)
                }.getOrNull()?.let { date ->
                    outputFormat.format(date)
                }
            } ?: value
        }
    }
}
