package com.abdullah.lockapp.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ChangeGuardTest {
    private val zone = ZoneId.of("Australia/Sydney")
    private val now = LocalDateTime.of(2026, 10, 13, 21, 30).atZone(zone).toInstant().toEpochMilli()
    private val midnight = LocalDateTime.of(2026, 10, 14, 0, 0).atZone(zone).toInstant().toEpochMilli()

    private val ctx = GuardContext(
        rules = mapOf("ig" to AppRule("ig", "Instagram", gateEnabled = true, dailyLimitMinutes = 30)),
        settings = GateSettings(questionsRequired = 3, unlockMinutes = 10, wrongCooldownSeconds = 10),
        armed = true,
        focus = null,
    )

    @Test
    fun `tightening is recognised`() {
        listOf(
            Change.AddApp("yt", "YouTube"),
            Change.SetGate("ig", true),
            Change.SetLimit("ig", 15),
            Change.SetQuestionsRequired(5),
            Change.SetUnlockMinutes(5),
            Change.SetWrongCooldown(30),
        ).forEach { assertFalse("$it", ChangeGuard.isLoosening(it, ctx)) }
    }

    @Test
    fun `loosening is recognised`() {
        listOf(
            Change.RemoveApp("ig"),
            Change.SetGate("ig", false),
            Change.SetLimit("ig", 60),
            Change.SetLimit("ig", null),
            Change.SetQuestionsRequired(1),
            Change.SetUnlockMinutes(30),
            Change.SetWrongCooldown(5),
        ).forEach { assertTrue("$it", ChangeGuard.isLoosening(it, ctx)) }
    }

    @Test
    fun `adding a limit to an app without one is tightening`() {
        val noLimit = ctx.copy(rules = mapOf("ig" to AppRule("ig", "Instagram", true, null)))
        assertFalse(ChangeGuard.isLoosening(Change.SetLimit("ig", 90), noLimit))
    }

    @Test
    fun `tightening applies now`() {
        assertEquals(Verdict.ApplyNow, ChangeGuard.evaluate(Change.SetLimit("ig", 15), ctx, now, zone))
    }

    @Test
    fun `loosening waits until midnight`() {
        assertEquals(Verdict.Queue(midnight), ChangeGuard.evaluate(Change.RemoveApp("ig"), ctx, now, zone))
    }

    @Test
    fun `nothing can be loosened during a focus session`() {
        val focused = ctx.copy(focus = FocusState(now - 1, now + 3_600_000))
        assertEquals(Verdict.Refuse(now + 3_600_000), ChangeGuard.evaluate(Change.SetLimit("ig", 60), focused, now, zone))
        assertEquals(Verdict.ApplyNow, ChangeGuard.evaluate(Change.SetLimit("ig", 10), focused, now, zone))
    }

    @Test
    fun `everything applies instantly during first-run setup`() {
        val setup = ctx.copy(armed = false)
        assertEquals(Verdict.ApplyNow, ChangeGuard.evaluate(Change.RemoveApp("ig"), setup, now, zone))
    }

    @Test
    fun `changes survive a round trip through JSON`() {
        val change: Change = Change.SetLimit("ig", null)
        val json = Json.encodeToString(Change.serializer(), change)
        assertEquals(change, Json.decodeFromString(Change.serializer(), json))
    }

    @Test
    fun `add and remove share a key so the latest wins`() {
        assertEquals(Change.AddApp("ig", "Instagram").key, Change.RemoveApp("ig").key)
    }
}
