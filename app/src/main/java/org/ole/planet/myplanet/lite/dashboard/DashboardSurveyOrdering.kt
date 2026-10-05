package org.ole.planet.myplanet.lite.dashboard

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument

internal fun List<SurveyDocument>.sortedNewestFirst(): List<SurveyDocument> =
    sortedWith(
        compareByDescending<SurveyDocument> { it.createdDate.toSurveyTimestamp() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() }
            .thenBy { it.id.orEmpty() },
    )

private fun String?.toSurveyTimestamp(): Long {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return Long.MIN_VALUE
    return value.toLongOrNull()
        ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching {
            LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        ?: Long.MIN_VALUE
}
