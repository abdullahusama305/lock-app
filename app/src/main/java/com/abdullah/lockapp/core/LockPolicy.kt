package com.abdullah.lockapp.core

import java.time.ZoneId
import kotlin.math.ceil

enum class BlockReason { FOCUS, DAILY_LIMIT }

sealed interface Decision {
    /** The app may stay on screen. [recheckAt] is when that can change (unlock or allowance ends). */
    data class Allow(val recheckAt: Long?) : Decision

    /** Show the question gate. [minutesLeftToday] is the remaining daily allowance, if limited. */
    data class Gate(val minutesLeftToday: Int?) : Decision

    /** No way in until [until]. */
    data class Blocked(val reason: BlockReason, val until: Long) : Decision
}

object LockPolicy {
    /**
     * Decides what to do with [pkg] coming to the foreground.
     *
     * Priority: focus session > used-up daily limit > question gate (unless recently unlocked).
     * [usedTodayMs] is null when usage can't be measured; a limited app then falls back to the gate.
     */
    fun decide(
        pkg: String,
        now: Long,
        snapshot: PolicySnapshot,
        usedTodayMs: Long?,
        zone: ZoneId,
    ): Decision {
        val rule = snapshot.rules[pkg] ?: return Decision.Allow(null)

        snapshot.focus?.let { if (it.isActive(now)) return Decision.Blocked(BlockReason.FOCUS, it.endsAt) }

        val limitMs = rule.dailyLimitMinutes?.let { it * 60_000L }
        var allowanceEndsAt: Long? = null
        var minutesLeft: Int? = null
        if (limitMs != null && usedTodayMs != null) {
            val remaining = limitMs - usedTodayMs
            if (remaining <= 0) {
                return Decision.Blocked(BlockReason.DAILY_LIMIT, DayMath.nextMidnight(now, zone))
            }
            allowanceEndsAt = now + remaining
            minutesLeft = ceil(remaining / 60_000.0).toInt()
        }

        val gated = rule.gateEnabled || (limitMs != null && usedTodayMs == null)
        if (!gated) return Decision.Allow(allowanceEndsAt)

        val unlockedUntil = snapshot.unlocks[pkg]
        if (unlockedUntil == null || unlockedUntil <= now) return Decision.Gate(minutesLeft)
        return Decision.Allow(listOfNotNull(unlockedUntil, allowanceEndsAt).min())
    }
}
