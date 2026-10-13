package com.abdullah.lockapp.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LockPolicyTest {
    private val zone = ZoneId.of("Australia/Sydney")
    private val now = LocalDateTime.of(2026, 10, 13, 15, 0).atZone(zone).toInstant().toEpochMilli()
    private val midnight = LocalDateTime.of(2026, 10, 14, 0, 0).atZone(zone).toInstant().toEpochMilli()
    private val minute = 60_000L

    private fun snapshot(
        rule: AppRule? = AppRule("ig", "Instagram", gateEnabled = true, dailyLimitMinutes = null),
        focus: FocusState? = null,
        unlocks: Map<String, Long> = emptyMap(),
    ) = PolicySnapshot(
        rules = listOfNotNull(rule).associateBy { it.packageName },
        focus = focus,
        unlocks = unlocks,
        loaded = true,
    )

    private fun decide(snapshot: PolicySnapshot, used: Long? = null, pkg: String = "ig") =
        LockPolicy.decide(pkg, now, snapshot, used, zone)

    @Test
    fun `apps that aren't blocked are always allowed`() {
        assertEquals(Decision.Allow(null), decide(snapshot(), pkg = "com.android.chrome"))
    }

    @Test
    fun `gated app without an unlock shows the gate`() {
        assertEquals(Decision.Gate(null), decide(snapshot()))
    }

    @Test
    fun `recent unlock allows until it expires`() {
        val until = now + 10 * minute
        assertEquals(Decision.Allow(until), decide(snapshot(unlocks = mapOf("ig" to until))))
    }

    @Test
    fun `expired unlock shows the gate again`() {
        assertEquals(Decision.Gate(null), decide(snapshot(unlocks = mapOf("ig" to now - 1))))
    }

    @Test
    fun `focus session beats everything, even an unlock`() {
        val focus = FocusState(now - minute, now + 30 * minute)
        val decision = decide(snapshot(focus = focus, unlocks = mapOf("ig" to now + 10 * minute)))
        assertEquals(Decision.Blocked(BlockReason.FOCUS, focus.endsAt), decision)
    }

    @Test
    fun `finished focus session no longer blocks`() {
        val focus = FocusState(now - 60 * minute, now - 1)
        assertEquals(Decision.Gate(null), decide(snapshot(focus = focus)))
    }

    @Test
    fun `used-up daily limit blocks until midnight even with an unlock`() {
        val rule = AppRule("ig", "Instagram", gateEnabled = true, dailyLimitMinutes = 30)
        val decision = decide(snapshot(rule, unlocks = mapOf("ig" to now + 10 * minute)), used = 30 * minute)
        assertEquals(Decision.Blocked(BlockReason.DAILY_LIMIT, midnight), decision)
    }

    @Test
    fun `gate reports minutes left today`() {
        val rule = AppRule("ig", "Instagram", gateEnabled = true, dailyLimitMinutes = 30)
        assertEquals(Decision.Gate(18), decide(snapshot(rule), used = 12 * minute + 1))
    }

    @Test
    fun `unlock is cut short by the remaining allowance`() {
        val rule = AppRule("ig", "Instagram", gateEnabled = true, dailyLimitMinutes = 30)
        val decision = decide(snapshot(rule, unlocks = mapOf("ig" to now + 10 * minute)), used = 26 * minute)
        assertEquals(Decision.Allow(now + 4 * minute), decision)
    }

    @Test
    fun `limit-only app is allowed until the allowance runs out`() {
        val rule = AppRule("yt", "YouTube", gateEnabled = false, dailyLimitMinutes = 60)
        assertEquals(Decision.Allow(now + 45 * minute), decide(snapshot(rule), used = 15 * minute, pkg = "yt"))
    }

    @Test
    fun `limit that can't be measured falls back to the gate`() {
        val rule = AppRule("yt", "YouTube", gateEnabled = false, dailyLimitMinutes = 60)
        assertEquals(Decision.Gate(null), decide(snapshot(rule), used = null, pkg = "yt"))
    }

    @Test
    fun `focus-only app is allowed outside focus sessions`() {
        val rule = AppRule("x", "X", gateEnabled = false, dailyLimitMinutes = null)
        assertEquals(Decision.Allow(null), decide(snapshot(rule), pkg = "x"))
    }
}
