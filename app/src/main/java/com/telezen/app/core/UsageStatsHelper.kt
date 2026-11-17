package com.telezen.app.core

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.LocalDate

/**
 * Foreground time per app, computed from raw usage events. Uses a single-foreground model:
 * a newly resumed activity closes every open interval, which tolerates missing pause events.
 */
class UsageStatsHelper(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private var todayCache: Pair<Long, Map<String, Long>>? = null

    fun foregroundByPackage(start: Long, end: Long): Map<String, Long> {
        if (!Permissions.usageAccess(context)) return emptyMap()
        val events = runCatching { usm.queryEvents(start, end) }.getOrNull() ?: return emptyMap()
        val totals = HashMap<String, Long>()
        var openPkg: String? = null
        var openSince = 0L
        var anyResumed = false
        val e = UsageEvents.Event()

        fun close(at: Long) {
            val p = openPkg ?: return
            if (at > openSince) totals.merge(p, at - openSince, Long::plus)
            openPkg = null
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                RESUMED -> {
                    close(e.timeStamp)
                    openPkg = e.packageName
                    openSince = e.timeStamp
                    anyResumed = true
                }
                PAUSED -> {
                    if (openPkg == e.packageName) close(e.timeStamp)
                    else if (!anyResumed) {
                        // The app was already in the foreground when the window started.
                        totals.merge(e.packageName, e.timeStamp - start, Long::plus)
                        anyResumed = true
                    }
                }
            }
        }
        close(minOf(end, System.currentTimeMillis()))
        return totals
    }

    @Synchronized
    fun today(): Map<String, Long> {
        val now = System.currentTimeMillis()
        todayCache?.let { (at, map) -> if (now - at < 20_000) return map }
        val map = foregroundByPackage(Day.startMillis(LocalDate.now()), now)
        todayCache = now to map
        return map
    }

    fun minutesToday(pkg: String): Int = ((today()[pkg] ?: 0L) / 60_000).toInt()

    fun day(date: LocalDate): Map<String, Long> =
        foregroundByPackage(Day.startMillis(date), Day.startMillis(date.plusDays(1)))

    /**
     * Android's own weekly totals per app (kept about 4 weeks, longer than the ~10 days of daily data).
     * Buckets don't line up with calendar weeks, so each carries its own start and end.
     */
    fun weeks(since: Long): List<Week> {
        if (!Permissions.usageAccess(context)) return emptyList()
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, since, System.currentTimeMillis())
        }.getOrNull().orEmpty()
        val out = HashMap<Long, Week>()
        for (s in stats) {
            val w = out.getOrPut(s.firstTimeStamp) { Week(s.firstTimeStamp, s.lastTimeStamp, HashMap()) }
            w.end = maxOf(w.end, s.lastTimeStamp)
            w.apps.merge(s.packageName, s.totalTimeInForeground, ::maxOf) // one entry per app expected; never double count
        }
        return out.values.sortedBy { it.start }
    }

    class Week(val start: Long, var end: Long, val apps: HashMap<String, Long>)

    private companion object {
        const val RESUMED = 1
        const val PAUSED = 2
    }
}
