package com.abdullah.lockapp.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.abdullah.lockapp.core.Clock
import com.abdullah.lockapp.core.DayMath
import com.abdullah.lockapp.core.UsageCalculator
import com.abdullah.lockapp.core.UsageEventLite

/** Foreground time per app from UsageStatsManager, which needs the Usage access special permission. */
class UsageRepository(private val context: Context, private val clock: Clock) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val cache = HashMap<String, Pair<Long, Long>>() // pkg -> (computedAt, usedMs)

    fun hasAccess(): Boolean =
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED

    /** Time [pkg] has spent in the foreground today, or null without Usage access. Cached for 2 s. */
    @Synchronized
    fun usedTodayMs(pkg: String): Long? {
        val now = clock.now()
        cache[pkg]?.let { (at, value) -> if (now - at in 0..2_000) return value }
        val used = usedMs(setOf(pkg), DayMath.startOfDay(now, clock.zone()), now)?.get(pkg) ?: return null
        cache[pkg] = now to used
        return used
    }

    /** Foreground time per package inside [from, to), or null without Usage access. */
    fun usedMs(packages: Set<String>, from: Long, to: Long): Map<String, Long>? {
        if (packages.isEmpty()) return emptyMap()
        if (!hasAccess()) return null
        val queryStart = from - LOOKBACK_MS
        val events = try {
            usageStats.queryEvents(queryStart, to)
        } catch (_: SecurityException) {
            return null
        } ?: return null
        val list = ArrayList<UsageEventLite>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageCalculator.ACTIVITY_RESUMED,
                UsageCalculator.ACTIVITY_PAUSED,
                UsageCalculator.ACTIVITY_STOPPED,
                UsageCalculator.SCREEN_NON_INTERACTIVE,
                UsageCalculator.DEVICE_SHUTDOWN,
                -> list += UsageEventLite(event.packageName, event.className, event.eventType, event.timeStamp)
            }
        }
        return UsageCalculator.foregroundMs(list, packages, queryStart, from, to)
    }

    private companion object {
        /** Catches apps that were already open when the window started. */
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }
}
