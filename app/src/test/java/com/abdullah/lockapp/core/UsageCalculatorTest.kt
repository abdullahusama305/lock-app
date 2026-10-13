package com.abdullah.lockapp.core

import com.abdullah.lockapp.core.UsageCalculator.ACTIVITY_PAUSED
import com.abdullah.lockapp.core.UsageCalculator.ACTIVITY_RESUMED
import com.abdullah.lockapp.core.UsageCalculator.ACTIVITY_STOPPED
import com.abdullah.lockapp.core.UsageCalculator.SCREEN_NON_INTERACTIVE
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageCalculatorTest {
    private val min = 60_000L
    private val queryStart = 0L
    private val from = 100 * min // "midnight"
    private val to = 200 * min // "now"

    private fun e(pkg: String, cls: String, type: Int, atMin: Long) = UsageEventLite(pkg, cls, type, atMin * min)

    private fun used(events: List<UsageEventLite>, pkg: String = "ig") =
        UsageCalculator.foregroundMs(events, setOf(pkg), queryStart, from, to)[pkg]!! / min

    @Test
    fun `sums resume-pause pairs`() {
        val events = listOf(
            e("ig", "Main", ACTIVITY_RESUMED, 110),
            e("ig", "Main", ACTIVITY_PAUSED, 120),
            e("ig", "Main", ACTIVITY_RESUMED, 150),
            e("ig", "Main", ACTIVITY_PAUSED, 155),
        )
        assertEquals(15, used(events))
    }

    @Test
    fun `counts a session that is still open up to now`() {
        assertEquals(10, used(listOf(e("ig", "Main", ACTIVITY_RESUMED, 190))))
    }

    @Test
    fun `clips a session that started before midnight`() {
        val events = listOf(
            e("ig", "Main", ACTIVITY_RESUMED, 90),
            e("ig", "Main", ACTIVITY_PAUSED, 105),
        )
        assertEquals(5, used(events))
    }

    @Test
    fun `a pause with no resume means the app was already open`() {
        assertEquals(5, used(listOf(e("ig", "Main", ACTIVITY_PAUSED, 105))))
    }

    @Test
    fun `switching between activities of one app is continuous`() {
        val events = listOf(
            e("ig", "Feed", ACTIVITY_RESUMED, 110),
            e("ig", "Reels", ACTIVITY_RESUMED, 115),
            e("ig", "Feed", ACTIVITY_STOPPED, 116),
            e("ig", "Reels", ACTIVITY_PAUSED, 130),
        )
        assertEquals(20, used(events))
    }

    @Test
    fun `screen off ends the session`() {
        val events = listOf(
            e("ig", "Main", ACTIVITY_RESUMED, 110),
            e("android", "", SCREEN_NON_INTERACTIVE, 112),
        )
        assertEquals(2, used(events))
    }

    @Test
    fun `other apps are ignored`() {
        val events = listOf(
            e("yt", "Watch", ACTIVITY_RESUMED, 110),
            e("yt", "Watch", ACTIVITY_PAUSED, 140),
        )
        assertEquals(0, used(events))
    }
}
