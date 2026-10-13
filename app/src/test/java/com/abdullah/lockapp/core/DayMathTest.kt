package com.abdullah.lockapp.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DayMathTest {
    private val sydney = ZoneId.of("Australia/Sydney")

    @Test
    fun `next midnight on the night clocks go forward`() {
        // Daylight saving started at 02:00 on 4 Oct 2026; that day had 23 hours.
        val evening = LocalDateTime.of(2026, 10, 3, 22, 0).atZone(sydney).toInstant().toEpochMilli()
        val expected = LocalDateTime.of(2026, 10, 4, 0, 0).atZone(sydney).toInstant().toEpochMilli()
        assertEquals(expected, DayMath.nextMidnight(evening, sydney))
        assertEquals(23 * 3_600_000L, DayMath.nextMidnight(expected, sydney) - expected)
    }

    @Test
    fun `start of day and epoch day agree`() {
        val t = LocalDateTime.of(2026, 10, 13, 9, 41).atZone(sydney).toInstant().toEpochMilli()
        val day = DayMath.epochDay(t, sydney)
        assertEquals(DayMath.startOfDay(t, sydney), DayMath.startOfEpochDay(day, sydney))
    }
}
