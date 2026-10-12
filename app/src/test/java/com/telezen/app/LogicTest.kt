package com.telezen.app

import com.telezen.app.core.EntryDecision
import com.telezen.app.core.LimitKind
import com.telezen.app.core.Pin
import com.telezen.app.core.Policy
import com.telezen.app.core.ScheduleLogic
import com.telezen.app.core.StrictPolicy
import com.telezen.app.core.Streaks
import com.telezen.app.data.AppSettings
import com.telezen.app.data.GuardedApp
import com.telezen.app.data.Schedule
import com.telezen.app.data.ScheduleMode
import com.telezen.app.data.db.DaySummary
import com.telezen.app.rules.RulesParser
import com.telezen.app.rules.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleLogicTest {
    // 2026-10-12 is a Monday.
    private val monday = LocalDateTime.of(2026, 10, 12, 0, 0)

    @Test
    fun daytimeWindow() {
        val s = Schedule("a", "Work", days = setOf(1), startMinute = 9 * 60, endMinute = 17 * 60)
        assertTrue(ScheduleLogic.isActive(s, monday.withHour(9)))
        assertTrue(ScheduleLogic.isActive(s, monday.withHour(16).withMinute(59)))
        assertFalse(ScheduleLogic.isActive(s, monday.withHour(17)))
        assertFalse(ScheduleLogic.isActive(s, monday.withHour(8).withMinute(59)))
        assertFalse(ScheduleLogic.isActive(s, monday.plusDays(1).withHour(10)))
    }

    @Test
    fun overnightWindowCrossesMidnight() {
        val s = Schedule("b", "Bed", days = setOf(1), startMinute = 23 * 60, endMinute = 7 * 60)
        assertTrue(ScheduleLogic.isActive(s, monday.withHour(23).withMinute(30)))
        assertTrue("early Tuesday belongs to Monday night", ScheduleLogic.isActive(s, monday.plusDays(1).withHour(6)))
        assertFalse(ScheduleLogic.isActive(s, monday.plusDays(1).withHour(7)))
        assertFalse("early Monday belongs to Sunday night", ScheduleLogic.isActive(s, monday.withHour(6)))
        assertFalse(ScheduleLogic.isActive(s, monday.plusDays(1).withHour(23)))
    }

    @Test
    fun equalStartEndMeansAllDay() {
        val s = Schedule("c", "All", days = setOf(1), startMinute = 0, endMinute = 0)
        assertTrue(ScheduleLogic.isActive(s, monday.withHour(13)))
        assertFalse(ScheduleLogic.isActive(s, monday.plusDays(1).withHour(13)))
    }

    @Test
    fun disabledScheduleNeverActive() {
        val s = Schedule("d", "Off", enabled = false, days = (1..7).toSet(), startMinute = 0, endMinute = 0)
        assertFalse(ScheduleLogic.isActive(s, monday))
    }
}

class PolicyTest {
    private val now = 1_000_000L
    private val at = LocalDateTime.of(2026, 10, 12, 10, 0)
    private val ig = "com.instagram.android"

    private fun decide(s: AppSettings, sessionEnd: Long = 0, unlock: Long = 0, opens: Int = 0, minutes: Int = 0) =
        Policy.onEntry(ig, s, now, at, sessionEnd, unlock, opens, minutes)

    @Test
    fun unguardedAppIsAllowed() {
        assertEquals(EntryDecision.Allow, decide(AppSettings()))
    }

    @Test
    fun guardedAppPausesUntilSessionStarts() {
        val s = AppSettings(guardedApps = listOf(GuardedApp(ig, delaySeconds = 15, askReason = false)))
        assertEquals(EntryDecision.Pause(ig, 15, false), decide(s))
        assertEquals(EntryDecision.Allow, decide(s, sessionEnd = now + 1))
        assertEquals(EntryDecision.Pause(ig, 15, false), decide(s, sessionEnd = now))
    }

    @Test
    fun masterSwitchOffAllowsEverything() {
        val s = AppSettings(masterEnabled = false, guardedApps = listOf(GuardedApp(ig)))
        assertEquals(EntryDecision.Allow, decide(s))
    }

    @Test
    fun openLimitBlocksNewSessionsButNotTheCurrentOne() {
        val s = AppSettings(guardedApps = listOf(GuardedApp(ig, maxOpensPerDay = 3)))
        assertEquals(EntryDecision.Limit(ig, LimitKind.OPENS, 3), decide(s, opens = 3))
        assertEquals(EntryDecision.Allow, decide(s, opens = 3, sessionEnd = now + 1))
        assertTrue(decide(s, opens = 2) is EntryDecision.Pause)
    }

    @Test
    fun timeLimitBlocksEvenMidSession() {
        val s = AppSettings(guardedApps = listOf(GuardedApp(ig, maxMinutesPerDay = 30)))
        assertEquals(EntryDecision.Limit(ig, LimitKind.MINUTES, 30), decide(s, minutes = 30, sessionEnd = now + 1))
    }

    @Test
    fun emergencyUnlockOverridesLimits() {
        val s = AppSettings(guardedApps = listOf(GuardedApp(ig, maxMinutesPerDay = 30)))
        assertEquals(EntryDecision.Allow, decide(s, minutes = 99, unlock = now + 1))
    }

    @Test
    fun blockScheduleWinsOverSession() {
        val sched = Schedule("w", "Work", days = setOf(1), startMinute = 9 * 60, endMinute = 17 * 60, packages = setOf(ig))
        val s = AppSettings(schedules = listOf(sched))
        assertEquals(EntryDecision.ScheduleBlock(ig, sched), decide(s, sessionEnd = now + 1))
    }

    @Test
    fun pauseScheduleAppliesToUnguardedApp() {
        val sched = Schedule("w", "Work", mode = ScheduleMode.PAUSE, days = setOf(1), startMinute = 9 * 60, endMinute = 17 * 60, packages = setOf(ig))
        val d = decide(AppSettings(schedules = listOf(sched)))
        assertTrue(d is EntryDecision.Pause)
    }

    @Test
    fun scheduleAddsShortFormTargets() {
        val sched = Schedule("w", "Work", days = setOf(1), startMinute = 9 * 60, endMinute = 17 * 60, targetIds = setOf("tiktok"))
        val s = AppSettings(enabledTargets = setOf("instagram_reels"), schedules = listOf(sched))
        assertEquals(setOf("instagram_reels", "tiktok"), Policy.activeTargets(s, at))
        assertEquals(setOf("instagram_reels"), Policy.activeTargets(s, at.withHour(18)))
        assertEquals(emptySet<String>(), Policy.activeTargets(s.copy(masterEnabled = false), at))
    }
}

class StrictPolicyTest {
    private val now = 1_000L
    private val base = AppSettings(
        strictUntil = now + 10_000,
        guardedApps = listOf(GuardedApp("a", delaySeconds = 10, maxOpensPerDay = 5, maxMinutesPerDay = 30)),
        schedules = listOf(Schedule("s", "Work", packages = setOf("a"))),
    )

    @Test
    fun noRestrictionsWhenStrictOff() {
        assertNull(StrictPolicy.violation(base.copy(strictUntil = 0), AppSettings(masterEnabled = false), now))
    }

    @Test
    fun looseningIsRejected() {
        assertNotNull(StrictPolicy.violation(base, base.copy(masterEnabled = false), now))
        assertNotNull(StrictPolicy.violation(base, base.copy(enabledTargets = emptySet()), now))
        assertNotNull(StrictPolicy.violation(base, base.copy(guardedApps = emptyList()), now))
        assertNotNull(StrictPolicy.violation(base, base.copy(guardedApps = listOf(base.guardedApps[0].copy(delaySeconds = 5))), now))
        assertNotNull(StrictPolicy.violation(base, base.copy(guardedApps = listOf(base.guardedApps[0].copy(maxOpensPerDay = 0))), now))
        assertNotNull(StrictPolicy.violation(base, base.copy(guardedApps = listOf(base.guardedApps[0].copy(maxMinutesPerDay = 60))), now))
        assertNotNull(StrictPolicy.violation(base, base.copy(schedules = emptyList()), now))
        assertNotNull(StrictPolicy.violation(base, base.copy(strictUntil = now + 1), now))
    }

    @Test
    fun tighteningIsAllowed() {
        assertNull(StrictPolicy.violation(base, base.copy(guardedApps = listOf(base.guardedApps[0].copy(delaySeconds = 30))), now))
        assertNull(StrictPolicy.violation(base, base.copy(guardedApps = listOf(base.guardedApps[0].copy(maxOpensPerDay = 3))), now))
        assertNull(StrictPolicy.violation(base, base.copy(guardedApps = base.guardedApps + GuardedApp("b")), now))
        assertNull(StrictPolicy.violation(base, base.copy(enabledTargets = base.enabledTargets + "tiktok"), now))
        assertNull(StrictPolicy.violation(base, base.copy(schedules = base.schedules + Schedule("t", "Bed")), now))
        assertNull(StrictPolicy.violation(base, base.copy(strictUntil = now + 99_999), now))
        assertNull(StrictPolicy.violation(base, base.copy(breathingExercise = true), now))
    }
}

class StreaksTest {
    private val today = LocalDate.of(2026, 10, 12)
    private fun day(offset: Long, minutes: Int) = DaySummary(today.minusDays(offset).toString(), minutes, 60, 0, 0, 0)

    @Test
    fun countsConsecutiveDaysEndingYesterday() {
        val s = listOf(day(1, 30), day(2, 50), day(3, 90), day(4, 10))
        val r = Streaks.compute(s, today)
        assertEquals(2, r.current)
        assertEquals(2, r.best)
    }

    @Test
    fun includesTodayWhenUnderGoal() {
        val r = Streaks.compute(listOf(day(0, 10), day(1, 10)), today)
        assertEquals(2, r.current)
    }

    @Test
    fun missingDayBreaksStreak() {
        val r = Streaks.compute(listOf(day(1, 10), day(3, 10), day(4, 10), day(5, 10)), today)
        assertEquals(1, r.current)
        assertEquals(3, r.best)
    }
}

class RulesTest {
    @Test
    fun builtInRulesParse() {
        val text = File("src/main/assets/rules.json").readText()
        val rules = RulesParser.parse(text)
        assertNotNull(rules.target("instagram_reels"))
        assertNotNull(rules.target("youtube_shorts"))
        assertTrue(rules.target("tiktok")!!.wholeApp)
        assertTrue("com.instagram.android" in rules.packages)
    }

    @Test
    fun browserRegexMatchesShortFormUrlsOnly() {
        val rules = RulesParser.parse(File("src/main/assets/rules.json").readText())
        val re = Regex(rules.target("browser_short_form")!!.match.first().regex!!, RegexOption.IGNORE_CASE)
        listOf(
            "m.youtube.com/shorts/abc",
            "https://www.youtube.com/shorts/abc",
            "youtube.com/shorts",
            "instagram.com/reels/",
            "www.instagram.com/reel/xyz",
            "tiktok.com/@someone/video/1",
            "https://vm.tiktok.com/abc",
            "m.facebook.com/reel/123",
        ).forEach { assertTrue(it, re.containsMatchIn(it)) }
        listOf(
            "google.com/search?q=tiktok.com",
            "m.youtube.com/watch?v=abc",
            "instagram.com/reelsmith",
            "en.wikipedia.org/wiki/TikTok",
            "notyoutube.com/shorts",
        ).forEach { assertFalse(it, re.containsMatchIn(it)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownMatcherType() {
        RulesParser.parse("""{"version":1,"targets":[{"id":"x","name":"X","packages":["p"],"match":[{"type":"nope"}]}]}""")
    }

    @Test
    fun fullViewIdKeepsAbsoluteIds() {
        assertEquals("p:id/a", RulesParser.fullViewId("p", "a"))
        assertEquals("q:id/a", RulesParser.fullViewId("p", "q:id/a"))
    }

    @Test
    fun versionComparison() {
        assertTrue(Versions.isNewerThanKnown("351.0.0.1", listOf("350.2", null)))
        assertFalse(Versions.isNewerThanKnown("350.9", listOf("350.0")))
        assertFalse("unknown baseline never warns", Versions.isNewerThanKnown("351", listOf(null, null)))
        assertFalse(Versions.isNewerThanKnown("19.45.38", listOf("20.01")))
    }
}

class PinTest {
    @Test
    fun verifiesOnlyTheRightPin() {
        val salt = Pin.newSalt()
        val hash = Pin.hash("4821", salt)
        assertTrue(Pin.verify("4821", salt, hash))
        assertFalse(Pin.verify("4822", salt, hash))
        assertFalse(Pin.verify("4821", null, hash))
    }
}
