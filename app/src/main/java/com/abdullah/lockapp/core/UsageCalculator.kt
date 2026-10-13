package com.abdullah.lockapp.core

/** A trimmed copy of `UsageEvents.Event`, so the maths is testable on the JVM. */
data class UsageEventLite(
    val packageName: String,
    val className: String?,
    val type: Int,
    val timestamp: Long,
)

object UsageCalculator {
    // Values of the UsageEvents.Event constants we care about.
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val SCREEN_NON_INTERACTIVE = 16
    const val ACTIVITY_STOPPED = 23
    const val DEVICE_SHUTDOWN = 26

    /**
     * Foreground time per package inside [from, to), from events queried since [queryStart].
     *
     * A package is in the foreground while at least one of its activities is resumed. A pause with
     * no earlier resume means the app was already open when the query window started.
     */
    fun foregroundMs(
        events: List<UsageEventLite>,
        packages: Set<String>,
        queryStart: Long,
        from: Long,
        to: Long,
    ): Map<String, Long> {
        val resumed = HashMap<String, MutableSet<String>>()
        val openSince = HashMap<String, Long>()
        val seen = HashSet<String>()
        val total = HashMap<String, Long>()

        fun close(pkg: String, at: Long) {
            val since = openSince.remove(pkg) ?: return
            resumed.remove(pkg)
            val start = maxOf(since, from)
            val end = minOf(at, to)
            if (end > start) total[pkg] = (total[pkg] ?: 0L) + (end - start)
        }

        for (e in events.sortedBy { it.timestamp }) {
            when (e.type) {
                ACTIVITY_RESUMED -> if (e.packageName in packages) {
                    seen += e.packageName
                    val set = resumed.getOrPut(e.packageName) { HashSet() }
                    if (set.isEmpty()) openSince[e.packageName] = e.timestamp
                    set += e.className.orEmpty()
                }
                ACTIVITY_PAUSED, ACTIVITY_STOPPED -> if (e.packageName in packages) {
                    val set = resumed[e.packageName]
                    if (set != null) {
                        set -= e.className.orEmpty()
                        if (set.isEmpty()) close(e.packageName, e.timestamp)
                    } else if (e.type == ACTIVITY_PAUSED && e.packageName !in seen) {
                        openSince[e.packageName] = queryStart
                        close(e.packageName, e.timestamp)
                    }
                    seen += e.packageName
                }
                SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> openSince.keys.toList().forEach { close(it, e.timestamp) }
            }
        }
        openSince.keys.toList().forEach { close(it, to) }
        return packages.associateWith { total[it] ?: 0L }
    }
}
