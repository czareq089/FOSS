package com.foss.app

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun formatUtcToLocal(rawDate: String, pattern: String = "yyyy-MM-dd HH:mm"): String {
    return try {
        val cleanDate = rawDate.replace("T", " ").take(19)
        val serverFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val utcDateTime = LocalDateTime.parse(
            if (cleanDate.length == 10) "$cleanDate 00:00:00" else cleanDate,
            serverFormatter
        )

        val zonedDateTime = utcDateTime.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
        zonedDateTime.format(DateTimeFormatter.ofPattern(pattern))
    } catch (_: Exception) {
        rawDate
    }
}