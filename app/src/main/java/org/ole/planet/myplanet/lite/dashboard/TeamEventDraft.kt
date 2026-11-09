package org.ole.planet.myplanet.lite.dashboard

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.R

internal class EventValidationException(val messageRes: Int) : IllegalArgumentException()

internal val meetupCategoryLabels = linkedMapOf(
    "" to R.string.calendar_optional,
    "ICT" to R.string.calendar_category_ict,
    "First Time" to R.string.calendar_category_first_time,
    "Mothers" to R.string.calendar_category_mothers,
    "General" to R.string.calendar_category_general,
    "E Learning" to R.string.calendar_category_e_learning,
    "Farming" to R.string.calendar_category_farming,
    "Academic Discussion" to R.string.calendar_category_academic_discussion,
    "Academic Help" to R.string.calendar_category_academic_help,
    "Awareness" to R.string.calendar_category_awareness,
)
internal val meetupCategories = meetupCategoryLabels.keys.toList()
internal val meetupWeekdays = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

internal data class TeamEventDraft(
    val title: String,
    val description: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val startTime: String,
    val endTime: String,
    val recurring: String = "none",
    val recurringNumber: String = "10",
    val days: Set<DayOfWeek> = emptySet(),
    val category: String = "",
    val location: String = "",
    val link: String = "",
) {
    fun toCreationJson(
        zone: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = LocalDateTime.now(zone),
    ): JSONObject {
        val json = toJson(zone)
        if (startDate < now.toLocalDate()) throw EventValidationException(R.string.calendar_past_date)
        val time = json.getString("startTime")
        if (time.isNotEmpty() && startDate.atTime(LocalTime.parse(time)) < now.withSecond(0).withNano(0)) {
            throw EventValidationException(R.string.calendar_past_time)
        }
        return json
    }

    fun toJson(zone: ZoneId = ZoneId.systemDefault()): JSONObject {
        fun check(valid: Boolean, message: Int) {
            if (!valid) throw EventValidationException(message)
        }
        check(title.isNotBlank() && description.isNotBlank(), R.string.calendar_required_fields)
        val end = endDate ?: startDate
        check(end >= startDate, R.string.calendar_invalid_dates)
        fun time(raw: String): String {
            if (raw.isBlank()) return ""
            check(Regex("\\d{1,2}:\\d{2}").matches(raw.trim()), R.string.calendar_invalid_times)
            val parts = raw.trim().split(":").map(String::toInt)
            check(parts[0] in 0..23 && parts[1] in 0..59, R.string.calendar_invalid_times)
            return String.format(Locale.ROOT, "%02d:%02d", parts[0], parts[1])
        }
        val startHour = time(startTime)
        val endHour = time(endTime)
        check(endHour.isEmpty() || startHour.isNotEmpty(), R.string.calendar_invalid_times)
        check(end == startDate || startHour.isEmpty() || endHour.isNotEmpty(), R.string.calendar_invalid_times)
        if (startHour.isNotEmpty() && endHour.isNotEmpty()) {
            check(end.atTime(LocalTime.parse(endHour)) >= startDate.atTime(LocalTime.parse(startHour)),
                R.string.calendar_invalid_times)
        }
        check(recurring in listOf("none", "daily", "weekly"), R.string.calendar_invalid_recurrence)
        val count = if (recurring == "none") 10 else recurringNumber.toIntOrNull()
        check(count != null && (recurring == "none" || count >= 2), R.string.calendar_invalid_recurrence)
        check(recurring != "weekly" || days.isNotEmpty(), R.string.calendar_invalid_recurrence)
        val url = link.trim()
        if (url.isNotEmpty()) {
            val parsed = url.toHttpUrlOrNull()
            check((url.startsWith("https://") || url.startsWith("http://")) &&
                url.none(Char::isWhitespace) && parsed != null &&
                parsed.host.contains('.') && parsed.host.split('.').all {
                    it.isNotEmpty() && it.length <= 63 && !it.startsWith('-') && !it.endsWith('-') &&
                        it.all { char -> char.isLetterOrDigit() || char == '-' }
                }, R.string.calendar_invalid_link)
        }
        check(category in meetupCategories, R.string.calendar_invalid_category)
        val storedDays = when (recurring) {
            "daily" -> meetupWeekdays
            "weekly" -> meetupWeekdays.filter { it in days }
            else -> emptyList()
        }
        return JSONObject().put("title", title.trim()).put("description", description.trim())
            .put("startDate", startDate.atStartOfDay(zone).toInstant().toEpochMilli())
            .put("endDate", endDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: JSONObject.NULL)
            .put("startTime", startHour).put("endTime", endHour).put("recurring", recurring)
            .put("recurringNumber", count).put("day", JSONArray(storedDays.map {
                it.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercaseChar)
            })).put("category", category).put("meetupLocation", location.trim()).put("meetupLink", url)
    }
}
