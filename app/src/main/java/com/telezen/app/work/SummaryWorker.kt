package com.telezen.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.telezen.app.TeleZenApp
import com.telezen.app.core.Day
import com.telezen.app.core.Notifications
import com.telezen.app.core.Permissions
import com.telezen.app.core.Policy
import com.telezen.app.data.db.DaySummary
import com.telezen.app.data.db.EventType
import com.telezen.app.tz
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Hourly: records per-day summaries (for stats and streaks) and posts the evening summary. */
class SummaryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.tz
        val today = LocalDate.now()
        if (Permissions.usageAccess(app)) {
            for (i in 0..13) {
                val date = today.minusDays(i.toLong())
                if (i <= 1 || app.db.summaries().get(date.toString()) == null) {
                    summarize(app, date)?.let { app.db.summaries().upsert(it) }
                }
            }
        }
        app.db.events().prune(System.currentTimeMillis() - 120L * 24 * 60 * 60 * 1000)
        maybeNotify(app, today)
        return Result.success()
    }

    private suspend fun summarize(app: TeleZenApp, date: LocalDate): DaySummary? {
        val usage = app.usage.day(date)
        if (usage.isEmpty()) return null
        val s = app.settings.current
        val tracked = Policy.trackedPackages(s, app.rules.current)
        val minutes = (tracked.sumOf { usage[it] ?: 0L } / 60_000).toInt()
        val day = date.toString()
        val events = app.db.events()
        val existingGoal = app.db.summaries().get(day)?.goalMinutes
        return DaySummary(
            day = day,
            trackedMinutes = minutes,
            goalMinutes = if (date == LocalDate.now()) s.dailyGoalMinutes else existingGoal ?: s.dailyGoalMinutes,
            opens = events.countDay(day, EventType.SESSION_START),
            abandoned = events.countDay(day, EventType.PAUSE_ABANDONED),
            blocks = events.countDay(day, EventType.SHORT_BLOCK),
        )
    }

    private suspend fun maybeNotify(app: TeleZenApp, today: LocalDate) {
        val s = app.settings.current
        val now = LocalTime.now()
        if (!s.summaryNotification || now.hour < 21 || app.runtime.lastSummaryDay == today.toString()) return
        val summary = app.db.summaries().get(today.toString())
        val blocks = app.db.events().countDay(today.toString(), EventType.SHORT_BLOCK)
        val abandoned = app.db.events().countDay(today.toString(), EventType.PAUSE_ABANDONED)
        val title = if (summary == null) "Today with TeleZen" else {
            val time = Day.formatDuration(summary.trackedMinutes * 60_000L)
            if (summary.trackedMinutes <= s.dailyGoalMinutes) "$time today: under your goal" else "$time today: over your ${s.dailyGoalMinutes}m goal"
        }
        val parts = buildList {
            if (blocks > 0) add("$blocks short-form feeds blocked")
            if (abandoned > 0) add("changed your mind $abandoned times")
        }
        val text = if (parts.isEmpty()) "Open TeleZen to see your day." else parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
        Notifications.showSummary(app, title, text)
        app.runtime.lastSummaryDay = today.toString()
    }

    companion object {
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            wm.enqueueUniquePeriodicWork(
                "summary",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SummaryWorker>(1, TimeUnit.HOURS).build(),
            )
        }

        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "summary_now",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SummaryWorker>().build(),
            )
        }
    }
}
