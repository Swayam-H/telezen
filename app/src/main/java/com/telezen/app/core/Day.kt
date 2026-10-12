package com.telezen.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Day {
    fun of(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun today(): String = LocalDate.now().toString()

    fun startMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun formatDuration(millis: Long): String {
        val minutes = millis / 60_000
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }
}

fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"
