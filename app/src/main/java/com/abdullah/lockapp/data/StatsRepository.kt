package com.abdullah.lockapp.data

import com.abdullah.lockapp.core.AppRule
import com.abdullah.lockapp.core.Clock
import com.abdullah.lockapp.core.DayMath
import com.abdullah.lockapp.data.db.AppDatabase
import com.abdullah.lockapp.data.db.DailyUsageEntity
import com.abdullah.lockapp.data.db.GateEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

data class Counts(
    /** Times a blocked or gated app was opened (gate shown or hard-blocked). */
    val attempts: Int = 0,
    val gateShown: Int = 0,
    val turnedBack: Int = 0,
    val unlocks: Int = 0,
    val blocked: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
) {
    val turnedBackPercent: Int? get() = if (gateShown == 0) null else turnedBack * 100 / gateShown
}

data class DayStats(val day: Long, val counts: Counts, val minutes: Int)

data class AppStats(val rule: AppRule, val counts: Counts, val minutesToday: Int?)

data class StatsSummary(
    val today: Counts,
    val minutesToday: Int,
    val perApp: List<AppStats>,
    /** Oldest first, ending today. */
    val week: List<DayStats>,
    val weekCounts: Counts,
    val protectionOffCount: Int,
    val clockChanges: Int,
    val focusSessions: Int,
)

class StatsRepository(
    private val db: AppDatabase,
    private val usage: UsageRepository,
    private val clock: Clock,
) {
    fun observe(rules: Flow<List<AppRule>>): Flow<StatsSummary> {
        val today = DayMath.epochDay(clock.now(), clock.zone())
        val from = today - 6
        return combine(db.events().observeSince(from), db.usage().observeSince(from), rules) { events, usageRows, ruleList ->
            build(events, usageRows, ruleList, today)
        }
    }

    /** Recomputes minutes per app for the last 7 days from UsageStatsManager. */
    suspend fun refreshUsage(packages: Collection<String>) = withContext(Dispatchers.IO) {
        if (packages.isEmpty()) return@withContext
        val now = clock.now()
        val zone = clock.zone()
        val today = DayMath.epochDay(now, zone)
        val rows = ArrayList<DailyUsageEntity>()
        for (day in today - 6..today) {
            val start = DayMath.startOfEpochDay(day, zone)
            val end = minOf(DayMath.startOfEpochDay(day + 1, zone), now)
            val used = usage.usedMs(packages.toSet(), start, end) ?: return@withContext
            used.forEach { (pkg, ms) -> rows += DailyUsageEntity(day, pkg, (ms / 60_000).toInt()) }
        }
        db.usage().upsertAll(rows)
    }

    private fun build(
        events: List<GateEventEntity>,
        usageRows: List<DailyUsageEntity>,
        rules: List<AppRule>,
        today: Long,
    ): StatsSummary {
        val rulePackages = rules.map { it.packageName }.toSet()
        val week = (today - 6..today).map { day ->
            DayStats(
                day = day,
                counts = count(events.filter { it.day == day }),
                minutes = usageRows.filter { it.day == day && it.packageName in rulePackages }.sumOf { it.minutes },
            )
        }
        val todayEvents = events.filter { it.day == today }
        val perApp = rules.map { rule ->
            AppStats(
                rule = rule,
                counts = count(todayEvents.filter { it.packageName == rule.packageName }),
                minutesToday = usageRows.firstOrNull { it.day == today && it.packageName == rule.packageName }?.minutes,
            )
        }
        return StatsSummary(
            today = count(todayEvents),
            minutesToday = week.last().minutes,
            perApp = perApp,
            week = week,
            weekCounts = count(events),
            protectionOffCount = events.count { it.type == EventType.SERVICE_OFF.name },
            clockChanges = events.count { it.type == EventType.TIME_CHANGED.name },
            focusSessions = events.count { it.type == EventType.FOCUS_STARTED.name },
        )
    }

    private fun count(events: List<GateEventEntity>): Counts {
        var c = Counts()
        for (e in events) {
            c = when (e.type) {
                EventType.GATE_SHOWN.name -> c.copy(attempts = c.attempts + 1, gateShown = c.gateShown + 1)
                EventType.BLOCKED_FOCUS.name, EventType.BLOCKED_LIMIT.name ->
                    c.copy(attempts = c.attempts + 1, blocked = c.blocked + 1)
                EventType.TURNED_BACK.name ->
                    c.copy(turnedBack = c.turnedBack + 1, answered = c.answered + e.answered, correct = c.correct + e.correct)
                EventType.UNLOCKED.name ->
                    c.copy(unlocks = c.unlocks + 1, answered = c.answered + e.answered, correct = c.correct + e.correct)
                else -> c
            }
        }
        return c
    }
}
