package com.abdullah.lockapp.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface Clock {
    fun now(): Long
    fun zone(): ZoneId
}

object WallClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Local-calendar helpers. Days are `LocalDate.toEpochDay()` values in the given zone. */
object DayMath {
    fun epochDay(ms: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

    fun startOfDay(ms: Long, zone: ZoneId): Long = startOfEpochDay(epochDay(ms, zone), zone)

    fun nextMidnight(ms: Long, zone: ZoneId): Long = startOfEpochDay(epochDay(ms, zone) + 1, zone)

    fun startOfEpochDay(day: Long, zone: ZoneId): Long =
        LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
}
