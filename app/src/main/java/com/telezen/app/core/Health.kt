package com.telezen.app.core

import android.content.Context
import com.telezen.app.TeleZenApp

enum class Severity { ERROR, WARNING, INFO }

enum class HealthAction { ACCESSIBILITY, USAGE_ACCESS, NOTIFICATIONS, BATTERY, RULES }

data class HealthIssue(
    val severity: Severity,
    val title: String,
    val detail: String,
    val action: HealthAction?,
)

object Health {
    private const val STALE_AFTER_MS = 3 * 24 * 60 * 60 * 1000L
    private const val HEAVY_USE_MS = 60 * 60 * 1000L

    fun check(ctx: Context, app: TeleZenApp): List<HealthIssue> {
        val s = app.settings.current
        val issues = mutableListOf<HealthIssue>()

        if (!Permissions.accessibilityEnabled(ctx)) {
            issues += HealthIssue(
                Severity.ERROR,
                "Blocking is off",
                "The TeleZen accessibility service is disabled, so nothing is being blocked.",
                HealthAction.ACCESSIBILITY,
            )
        } else if (!Permissions.accessibilityRunning()) {
            issues += HealthIssue(
                Severity.ERROR,
                "Blocking service isn't running",
                "Android stopped the service. Turn TeleZen off and on again in Accessibility settings.",
                HealthAction.ACCESSIBILITY,
            )
        }
        val usage = Permissions.usageAccess(ctx)
        if (!usage) {
            issues += HealthIssue(
                Severity.WARNING,
                "Usage access is off",
                "Time limits, screen-time stats and goals need usage access.",
                HealthAction.USAGE_ACCESS,
            )
        }
        if (s.summaryNotification && !Permissions.notifications(ctx)) {
            issues += HealthIssue(
                Severity.INFO,
                "Notifications are off",
                "Allow notifications to get your end-of-day summary.",
                HealthAction.NOTIFICATIONS,
            )
        }
        if (!Permissions.batteryExempt(ctx)) {
            issues += HealthIssue(
                Severity.INFO,
                "Battery optimization is on",
                "Some phones stop background services. Exempting TeleZen keeps blocking reliable.",
                HealthAction.BATTERY,
            )
        }

        if (s.masterEnabled) {
            val now = System.currentTimeMillis()
            val today = if (usage) app.usage.today() else emptyMap()
            // App updates are shown on the Detection rules screen instead: YouTube and Instagram update
            // weekly, and a Home warning after each one would be noise.
            for (t in app.rules.current.targets.filter { it.id in s.enabledTargets && !it.wholeApp && it.staleCheck }) {
                val installed = t.packages.filter { Apps.isInstalled(ctx, it) }
                val lastMatch = app.runtime.lastMatch(t.id).takeIf { it > 0 } ?: app.runtime.firstRun
                val heavyUse = installed.sumOf { today[it] ?: 0L } > HEAVY_USE_MS
                if (heavyUse && now - lastMatch > STALE_AFTER_MS) {
                    issues += HealthIssue(
                        Severity.INFO,
                        "Is ${t.name} still being blocked?",
                        "You've used ${installed.joinToString { Apps.label(ctx, it) }} for over an hour today and " +
                            "${t.name} hasn't been blocked in 3 days. If it's getting through, the app may have changed. " +
                            "Use Report not working.",
                        HealthAction.RULES,
                    )
                }
            }
        }
        return issues
    }
}
