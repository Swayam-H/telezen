package com.telezen.app.core

import com.telezen.app.data.AppSettings
import com.telezen.app.data.DEFAULT_SCHEDULE_PAUSE_SECONDS
import com.telezen.app.data.Schedule
import com.telezen.app.data.ScheduleMode
import com.telezen.app.rules.RulesFile
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ScheduleLogic {
    fun isActive(s: Schedule, at: LocalDateTime): Boolean {
        if (!s.enabled || s.days.isEmpty()) return false
        val minute = at.hour * 60 + at.minute
        val today = at.dayOfWeek.value
        val yesterday = if (today == 1) 7 else today - 1
        return when {
            s.startMinute == s.endMinute -> today in s.days
            s.startMinute < s.endMinute -> today in s.days && minute >= s.startMinute && minute < s.endMinute
            else -> (today in s.days && minute >= s.startMinute) || (yesterday in s.days && minute < s.endMinute)
        }
    }

    fun formatMinute(minute: Int): String =
        LocalTime.of((minute / 60) % 24, minute % 60).format(DateTimeFormatter.ofPattern("HH:mm"))

    fun describe(s: Schedule): String {
        val days = when (s.days) {
            setOf(1, 2, 3, 4, 5, 6, 7) -> "Every day"
            setOf(1, 2, 3, 4, 5) -> "Weekdays"
            setOf(6, 7) -> "Weekends"
            else -> s.days.sorted().joinToString(" ") { DAY_SHORT[it - 1] }
        }
        val time = if (s.startMinute == s.endMinute) "all day" else "${formatMinute(s.startMinute)}–${formatMinute(s.endMinute)}"
        return "$days, $time"
    }

    val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}

enum class LimitKind { OPENS, MINUTES }

sealed interface EntryDecision {
    data object Allow : EntryDecision
    data class Pause(val pkg: String, val delaySeconds: Int, val askReason: Boolean) : EntryDecision
    data class Limit(val pkg: String, val kind: LimitKind, val limit: Int) : EntryDecision
    data class ScheduleBlock(val pkg: String, val schedule: Schedule) : EntryDecision
}

object Policy {
    fun activeSchedules(s: AppSettings, at: LocalDateTime): List<Schedule> =
        s.schedules.filter { ScheduleLogic.isActive(it, at) }

    /** Short-form targets blocked right now: the always-on toggles plus any from active schedules. */
    fun activeTargets(s: AppSettings, at: LocalDateTime): Set<String> =
        if (!s.masterEnabled) emptySet()
        else s.enabledTargets + activeSchedules(s, at).flatMap { it.targetIds }

    /**
     * What should happen when [pkg] is (or is still) in the foreground.
     * Called on app entry and again periodically while the app stays open.
     */
    fun onEntry(
        pkg: String,
        s: AppSettings,
        nowMillis: Long,
        at: LocalDateTime,
        sessionEnd: Long,
        unlockUntil: Long,
        opensToday: Int,
        minutesToday: Int,
    ): EntryDecision {
        if (!s.masterEnabled) return EntryDecision.Allow
        if (unlockUntil > nowMillis) return EntryDecision.Allow

        val schedules = activeSchedules(s, at).filter { pkg in it.packages }
        schedules.firstOrNull { it.mode == ScheduleMode.BLOCK }?.let {
            return EntryDecision.ScheduleBlock(pkg, it)
        }

        val guarded = s.guarded(pkg)
        if (guarded == null && schedules.isEmpty()) return EntryDecision.Allow

        val inSession = sessionEnd > nowMillis
        if (guarded != null) {
            if (!inSession && guarded.maxOpensPerDay > 0 && opensToday >= guarded.maxOpensPerDay) {
                return EntryDecision.Limit(pkg, LimitKind.OPENS, guarded.maxOpensPerDay)
            }
            if (guarded.maxMinutesPerDay > 0 && minutesToday >= guarded.maxMinutesPerDay) {
                return EntryDecision.Limit(pkg, LimitKind.MINUTES, guarded.maxMinutesPerDay)
            }
        }
        if (inSession) return EntryDecision.Allow
        return EntryDecision.Pause(
            pkg = pkg,
            delaySeconds = guarded?.delaySeconds ?: DEFAULT_SCHEDULE_PAUSE_SECONDS,
            askReason = guarded?.askReason ?: false,
        )
    }

    /** Apps whose screen time counts toward the daily goal. */
    fun trackedPackages(s: AppSettings, rules: RulesFile): Set<String> {
        val fromTargets = rules.targets.filter { it.id in s.enabledTargets }.flatMap { it.packages }
        return s.guardedApps.map { it.packageName }.toSet() +
            s.schedules.flatMap { it.packages } +
            fromTargets
    }
}

object StrictPolicy {
    /** Returns a reason when [new] loosens protection while strict mode is on, otherwise null. */
    fun violation(old: AppSettings, new: AppSettings, now: Long): String? {
        if (!old.strictActive(now)) return null
        if (new.strictUntil < old.strictUntil) return "Strict mode can't be shortened."
        if (old.masterEnabled && !new.masterEnabled) return "Protection can't be turned off during strict mode."
        if (!new.enabledTargets.containsAll(old.enabledTargets)) return "Blocked feeds can't be unblocked during strict mode."
        for (o in old.guardedApps.filter { it.enabled }) {
            val n = new.guardedApps.firstOrNull { it.packageName == o.packageName }
            if (n == null || !n.enabled) return "Guarded apps can't be removed or turned off during strict mode."
            if (n.delaySeconds < o.delaySeconds) return "Pause delays can't be shortened during strict mode."
            if (o.askReason && !n.askReason) return "The reason prompt can't be turned off during strict mode."
            if (looser(o.maxOpensPerDay, n.maxOpensPerDay)) return "Open limits can't be raised during strict mode."
            if (looser(o.maxMinutesPerDay, n.maxMinutesPerDay)) return "Time limits can't be raised during strict mode."
        }
        for (o in old.schedules.filter { it.enabled }) {
            if (new.schedules.firstOrNull { it.id == o.id } != o) return "Existing schedules are locked during strict mode."
        }
        if (old.pinHash != null && new.pinHash != old.pinHash) return "The PIN can't be changed during strict mode."
        return null
    }

    private fun looser(old: Int, new: Int) = old > 0 && (new == 0 || new > old)
}
