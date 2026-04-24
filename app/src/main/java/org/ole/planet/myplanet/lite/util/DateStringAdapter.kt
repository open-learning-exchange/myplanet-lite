package org.ole.planet.myplanet.lite.util

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.ToJson
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class BirthDateString

class DateStringAdapter {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    @FromJson
    @BirthDateString
    fun fromJson(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> {
                try {
                    dateFormat.parse(value)?.time
                } catch (_: ParseException) {
                    value.toLongOrNull()
                }
            }
            else -> null
        }
    }

    @ToJson
    fun toJson(@BirthDateString value: Long?): String? {
        return if (value == null) {
            null
        } else {
            dateFormat.format(value)
        }
    }
}
