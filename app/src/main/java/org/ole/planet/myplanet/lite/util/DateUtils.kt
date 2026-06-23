package org.ole.planet.myplanet.lite.util

import android.os.Build
import androidx.annotation.VisibleForTesting
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS
    private const val MONTH_MILLIS = 30 * DAY_MILLIS
    private const val YEAR_MILLIS = 365 * DAY_MILLIS

    fun formatRelativeTime(context: android.content.Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMillis = kotlin.math.max(0L, now - timestamp)
        val minutes = diffMillis / MINUTE_MILLIS
        val hours = diffMillis / HOUR_MILLIS
        val days = diffMillis / DAY_MILLIS
        val months = diffMillis / MONTH_MILLIS
        val years = diffMillis / YEAR_MILLIS
        return when {
            years >= 1 -> if (years == 1L) {
                context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_year)
            } else {
                context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_years, years)
            }
            months >= 1 -> if (months == 1L) {
                context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_month)
            } else {
                context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_months, months)
            }
            days >= 1 -> if (days == 1L) {
                context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_day)
            } else {
                context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_days, days)
            }
            hours >= 1 -> if (hours == 1L) {
                context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_hour)
            } else {
                context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_hours, hours)
            }
            minutes >= 1 -> if (minutes == 1L) {
                context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_minute)
            } else {
                context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_minutes, minutes)
            }
            else -> context.getString(org.ole.planet.myplanet.lite.R.string.dashboard_relative_time_seconds)
        }
    }

    fun buildMetadata(username: String?, relativeTime: String): String {
        return if (!username.isNullOrBlank()) {
            "@${username.trim()} • $relativeTime"
        } else {
            relativeTime
        }
    }


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
                ?: fallback
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
            } ?: fallback
        }
    }

    fun toDisplayDate(dateLong: Long?): String {
        if (dateLong == null || dateLong <= 0L) return "-"
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return formatter.format(Date(dateLong))
    }
}
