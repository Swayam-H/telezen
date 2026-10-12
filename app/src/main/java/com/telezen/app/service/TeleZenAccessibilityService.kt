package com.telezen.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.telezen.app.R
import com.telezen.app.TeleZenApp
import com.telezen.app.core.Apps
import com.telezen.app.core.Day
import com.telezen.app.core.EntryDecision
import com.telezen.app.core.Policy
import com.telezen.app.data.BlockBehavior
import com.telezen.app.data.db.EventType
import com.telezen.app.rules.TargetRule
import com.telezen.app.ui.intervention.InterventionActivity
import com.telezen.app.ui.intervention.Mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDateTime
import java.util.Date

class TeleZenAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TeleZenAccessibilityService? = null
            private set

        private const val TICK_MS = 5_000L
        private const val CHECK_THROTTLE_MS = 250L
        private const val SNAPSHOT_EVERY_MS = 8_000L

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: TeleZenApp

    private var currentPkg: String? = null
    private var ownAppWasInFront = false
    private var ignored: Set<String> = emptySet()
    private var protectedPackages: Set<String> = emptySet()

    private var checkPending = false
    private var cooldownUntil = 0L
    private var lastLaunchKey: String? = null
    private var lastLaunchAt = 0L
    private var lastSnapshotAt = 0L

    private var backTarget: TargetRule? = null
    private var backRetries = 0

    private val checkRunnable = Runnable {
        checkPending = false
        runScreenCheck()
    }

    private val tick = object : Runnable {
        override fun run() {
            ticks++
            if (ticks % 12 == 0L) refreshIgnoredPackages()
            guardClockChanges()
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }
    private var ticks = 0L
    private var lastWall = 0L
    private var lastElapsed = 0L

    /** Moving the system clock forward must not end strict mode early, so push its end back by the jump. */
    private fun guardClockChanges() {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        if (lastElapsed != 0L) {
            val jump = (wall - lastWall) - (elapsed - lastElapsed)
            if (jump > 60_000 && app.settings.current.strictActive(lastWall)) {
                app.settings.update { it.copy(strictUntil = it.strictUntil + jump) }
            }
        }
        lastWall = wall
        lastElapsed = elapsed
    }

    private val recheckAfterBack = Runnable { recheckAfterBack() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        app = application as TeleZenApp
        instance = this
        refreshIgnoredPackages()
        handler.postDelayed(tick, TICK_MS)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) ownAppWasInFront = true
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            pkg !in ignored &&
            event.className?.toString() != "android.inputmethodservice.SoftInputWindow"
        ) {
            onForeground(pkg)
        }
        if (needsScreenCheck(pkg)) scheduleScreenCheck()
    }

    private fun refreshIgnoredPackages() {
        val imes = getSystemService(InputMethodManager::class.java)
            .enabledInputMethodList.map { it.packageName }
        ignored = imes.toSet() + "com.android.systemui"
        protectedPackages = Apps.protectedPackages(this)
    }

    // ---- App entry: pause screens, limits, schedules ----

    private fun onForeground(pkg: String) {
        val changed = pkg != currentPkg || ownAppWasInFront
        ownAppWasInFront = false
        currentPkg = pkg
        if (changed) evaluate(pkg)
    }

    private fun onTick() {
        val pkg = currentPkg ?: return
        val s = app.settings.current
        if (!s.masterEnabled || InterventionActivity.visible || ownAppWasInFront) return
        if (!getSystemService(PowerManager::class.java).isInteractive) return

        val sessionEnd = app.runtime.sessionEnd(pkg)
        if (sessionEnd in 1..System.currentTimeMillis()) {
            app.runtime.endSession(pkg)
            if (s.guarded(pkg) != null || Policy.activeSchedules(s, LocalDateTime.now()).any { pkg in it.packages }) {
                app.log(EventType.SESSION_END, pkg)
                launch(Mode.SESSION_END, pkg)
            }
            return
        }
        evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        val s = app.settings.current
        // Never pause or block the home screen or phone app, even if an imported settings file lists them.
        if (!s.masterEnabled || pkg in protectedPackages) return
        val guarded = s.guarded(pkg)
        val schedules = Policy.activeSchedules(s, LocalDateTime.now())
        if (guarded == null && schedules.none { pkg in it.packages }) return

        scope.launch {
            val opens = if ((guarded?.maxOpensPerDay ?: 0) > 0) {
                app.db.events().count(Day.today(), pkg, EventType.SESSION_START)
            } else 0
            val minutes = if ((guarded?.maxMinutesPerDay ?: 0) > 0) {
                withContext(Dispatchers.IO) { app.usage.minutesToday(pkg) }
            } else 0
            if (currentPkg != pkg) return@launch
            val decision = Policy.onEntry(
                pkg = pkg,
                s = app.settings.current,
                nowMillis = System.currentTimeMillis(),
                at = LocalDateTime.now(),
                sessionEnd = app.runtime.sessionEnd(pkg),
                unlockUntil = app.runtime.unlockUntil(pkg),
                opensToday = opens,
                minutesToday = minutes,
            )
            when (decision) {
                EntryDecision.Allow -> Unit
                is EntryDecision.Pause -> launch(Mode.PAUSE, pkg)
                is EntryDecision.Limit -> {
                    app.runtime.endSession(pkg)
                    if (launch(Mode.LIMIT, pkg, limitKind = decision.kind.name, limit = decision.limit)) {
                        app.log(EventType.LIMIT_HIT, pkg, extra = decision.kind.name)
                    }
                }
                is EntryDecision.ScheduleBlock -> {
                    if (launch(Mode.SCHEDULE_BLOCK, pkg, scheduleId = decision.schedule.id)) {
                        app.log(EventType.SCHEDULE_BLOCK, pkg, extra = decision.schedule.name)
                    }
                }
            }
        }
    }

    private fun launch(
        mode: Mode,
        pkg: String,
        targetId: String? = null,
        scheduleId: String? = null,
        limitKind: String? = null,
        limit: Int = 0,
    ): Boolean {
        val now = System.currentTimeMillis()
        val key = "$mode:$pkg"
        if (key == lastLaunchKey && now - lastLaunchAt < 3_000) return false
        if (InterventionActivity.visible && mode != Mode.SHORT_BLOCK) return false
        lastLaunchKey = key
        lastLaunchAt = now
        InterventionActivity.start(this, mode, pkg, targetId, scheduleId, limitKind, limit)
        return true
    }

    // ---- Screen checks: short-form feeds and strict-mode settings guard ----

    private fun needsScreenCheck(pkg: String): Boolean {
        val s = app.settings.current
        if (s.strictActive() && pkg in SETTINGS_PACKAGES) return true
        return pkg in activePackages()
    }

    private var activePackagesCache: Set<String> = emptySet()
    private var activePackagesAt = 0L

    /** Packages of short-form targets blocked right now; only these screens are ever inspected. */
    private fun activePackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - activePackagesAt > 5_000) {
            val active = Policy.activeTargets(app.settings.current, LocalDateTime.now())
            activePackagesCache = app.rules.current.targets.filter { it.id in active }.flatMap { it.packages }.toSet()
            activePackagesAt = now
        }
        return activePackagesCache
    }

    private fun scheduleScreenCheck() {
        if (checkPending) return
        checkPending = true
        handler.postDelayed(checkRunnable, CHECK_THROTTLE_MS)
    }

    private fun runScreenCheck() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        val s = app.settings.current
        val now = System.currentTimeMillis()

        if (s.strictActive() && pkg in SETTINGS_PACKAGES) {
            guardSettings(root, now, s.strictUntil)
            return
        }
        activePackagesAt = 0L
        if (pkg !in activePackages()) return

        if (now - lastSnapshotAt > SNAPSHOT_EVERY_MS) {
            lastSnapshotAt = now
            app.runtime.saveSnapshot(pkg, NodeMatcher.snapshot(root))
        }
        if (now < cooldownUntil) return

        val active = Policy.activeTargets(s, LocalDateTime.now())
        val target = app.rules.current.targets.firstOrNull {
            it.id in active && pkg in it.packages && NodeMatcher.matches(root, pkg, it)
        } ?: return
        onShortFormDetected(target, pkg, s.blockBehavior)
    }

    private fun onShortFormDetected(target: TargetRule, pkg: String, behavior: BlockBehavior) {
        cooldownUntil = System.currentTimeMillis() + 1_500
        app.runtime.recordMatch(target.id, pkg, Apps.versionName(this, pkg))
        app.log(EventType.SHORT_BLOCK, pkg, targetId = target.id)

        if (target.wholeApp) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            if (behavior == BlockBehavior.OVERLAY) launch(Mode.SHORT_BLOCK, pkg, targetId = target.id)
            else toast("${target.name} is blocked")
            return
        }

        performGlobalAction(GLOBAL_ACTION_BACK)
        backTarget = target
        backRetries = 0
        handler.removeCallbacks(recheckAfterBack)
        handler.postDelayed(recheckAfterBack, 700)
        if (behavior == BlockBehavior.OVERLAY) launch(Mode.SHORT_BLOCK, pkg, targetId = target.id)
        else toast("${target.name} blocked")
    }

    /** Some screens need more than one back press (e.g. a Reel opened from a DM thread). */
    private fun recheckAfterBack() {
        val target = backTarget ?: return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg !in target.packages || !NodeMatcher.matches(root, pkg, target)) {
            backTarget = null
            return
        }
        backRetries++
        if (backRetries >= 3) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            backTarget = null
            return
        }
        cooldownUntil = System.currentTimeMillis() + 1_500
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed(recheckAfterBack, 700)
    }

    private fun guardSettings(root: android.view.accessibility.AccessibilityNodeInfo, now: Long, until: Long) {
        if (now < cooldownUntil) return
        val label = getString(R.string.app_name)
        if (NodeMatcher.findText(root, label, force = true).isEmpty()) return
        cooldownUntil = now + 1_500
        performGlobalAction(GLOBAL_ACTION_HOME)
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(until))
        toast("Strict mode is on until $time")
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
