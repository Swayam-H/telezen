package com.telezen.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class BlockBehavior { OVERLAY, BACK }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class ScheduleMode { BLOCK, PAUSE }

@Serializable
data class GuardedApp(
    val packageName: String,
    val enabled: Boolean = true,
    val delaySeconds: Int = 10,
    val askReason: Boolean = true,
    val maxOpensPerDay: Int = 0,
    val maxMinutesPerDay: Int = 0,
)

@Serializable
data class Schedule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    /** ISO day numbers, 1 = Monday .. 7 = Sunday. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
    val mode: ScheduleMode = ScheduleMode.BLOCK,
    val packages: Set<String> = emptySet(),
    val targetIds: Set<String> = emptySet(),
)

@Serializable
data class AppSettings(
    val onboardingDone: Boolean = false,
    val masterEnabled: Boolean = true,
    val enabledTargets: Set<String> = setOf("instagram_reels", "youtube_shorts"),
    val blockBehavior: BlockBehavior = BlockBehavior.OVERLAY,
    val guardedApps: List<GuardedApp> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val dailyGoalMinutes: Int = 60,
    val summaryNotification: Boolean = true,
    val prompts: List<String> = DEFAULT_PROMPTS,
    val breathingExercise: Boolean = false,
    val strictUntil: Long = 0L,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
) {
    fun strictActive(now: Long = System.currentTimeMillis()) = strictUntil > now
    fun guarded(pkg: String) = guardedApps.firstOrNull { it.packageName == pkg && it.enabled }
}

val DEFAULT_PROMPTS = listOf(
    "Is this what you meant to do right now?",
    "Take one slow breath before you decide.",
    "What would future you want you to do?",
    "You can always come back later.",
    "Notice the urge. You don't have to follow it.",
    "Is there something you've been putting off?",
    "Boredom is fine. It's where ideas come from.",
)

const val DEFAULT_SCHEDULE_PAUSE_SECONDS = 10
