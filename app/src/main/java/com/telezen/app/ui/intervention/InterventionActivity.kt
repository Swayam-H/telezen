package com.telezen.app.ui.intervention

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.telezen.app.core.Apps
import com.telezen.app.core.Day
import com.telezen.app.core.EntryDecision
import com.telezen.app.core.Policy
import com.telezen.app.data.db.EventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import com.telezen.app.tz
import com.telezen.app.ui.theme.TeleZenTheme

enum class Mode { PAUSE, SHORT_BLOCK, LIMIT, SCHEDULE_BLOCK, SESSION_END }

data class InterventionRequest(
    val mode: Mode,
    val pkg: String,
    val targetId: String?,
    val scheduleId: String?,
    val limitKind: String?,
    val limit: Int,
    val nonce: Long = System.nanoTime(),
)

class InterventionActivity : ComponentActivity() {

    companion object {
        @Volatile
        var visible = false
            private set

        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_SCHEDULE = "schedule"
        private const val EXTRA_LIMIT_KIND = "limit_kind"
        private const val EXTRA_LIMIT = "limit"

        fun start(
            ctx: Context,
            mode: Mode,
            pkg: String,
            targetId: String? = null,
            scheduleId: String? = null,
            limitKind: String? = null,
            limit: Int = 0,
        ) {
            val intent = Intent(ctx, InterventionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_TARGET, targetId)
                .putExtra(EXTRA_SCHEDULE, scheduleId)
                .putExtra(EXTRA_LIMIT_KIND, limitKind)
                .putExtra(EXTRA_LIMIT, limit)
            ctx.startActivity(intent)
        }
    }

    private var request by mutableStateOf<InterventionRequest?>(null)
    private var resolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        request = parse(intent)
        if (request == null) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this) { onBack() }
        setContent {
            val settings by tz.settings.state.collectAsState()
            TeleZenTheme(settings.theme) {
                val r = request
                if (r != null) {
                    key(r.nonce) {
                        InterventionScreen(r, settings, actions)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parse(intent)?.let {
            request = it
            resolved = false
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onPause() {
        super.onPause()
        visible = false
    }

    override fun onStop() {
        super.onStop()
        // A pause screen left unanswered is re-shown by the service if the user returns to the app.
        if (!isChangingConfigurations && !isFinishing) {
            val r = request
            if (!resolved && r?.mode == Mode.PAUSE) tz.log(EventType.PAUSE_ABANDONED, r.pkg)
            finish()
        }
    }

    private fun parse(intent: Intent?): InterventionRequest? {
        val mode = intent?.getStringExtra(EXTRA_MODE)?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: return null
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return null
        return InterventionRequest(
            mode = mode,
            pkg = pkg,
            targetId = intent.getStringExtra(EXTRA_TARGET),
            scheduleId = intent.getStringExtra(EXTRA_SCHEDULE),
            limitKind = intent.getStringExtra(EXTRA_LIMIT_KIND),
            limit = intent.getIntExtra(EXTRA_LIMIT, 0),
        )
    }

    private fun onBack() {
        val r = request ?: return finish()
        when (r.mode) {
            Mode.PAUSE -> actions.neverMind(r.pkg)
            Mode.SHORT_BLOCK -> actions.dismissBlock(r)
            else -> actions.goHome()
        }
    }

    private val actions = object : InterventionActions {
        override fun continueSession(pkg: String, minutes: Int, reason: String?) {
            resolved = true
            tz.runtime.startSession(pkg, minutes)
            tz.log(EventType.SESSION_START, pkg, extra = reason)
            Apps.launch(this@InterventionActivity, pkg)
            finish()
        }

        override fun neverMind(pkg: String) {
            tz.log(EventType.PAUSE_ABANDONED, pkg)
            goHome()
        }

        override fun goHome() {
            resolved = true
            Apps.goHome(this@InterventionActivity)
            finish()
        }

        override fun dismissBlock(r: InterventionRequest) {
            resolved = true
            val wholeApp = r.targetId?.let { tz.rules.current.target(it)?.wholeApp } == true
            if (!wholeApp) Apps.launch(this@InterventionActivity, r.pkg)
            finish()
        }

        override fun emergencyUnlock(pkg: String, minutes: Int) {
            resolved = true
            tz.runtime.grantUnlock(pkg, minutes)
            tz.log(EventType.EMERGENCY_UNLOCK, pkg)
            Apps.launch(this@InterventionActivity, pkg)
            finish()
        }

        override fun extend(r: InterventionRequest) {
            val app = tz
            lifecycleScope.launch {
                val opens = app.db.events().count(Day.today(), r.pkg, EventType.SESSION_START)
                val minutes = withContext(Dispatchers.IO) { app.usage.minutesToday(r.pkg) }
                val decision = Policy.onEntry(
                    pkg = r.pkg,
                    s = app.settings.current,
                    nowMillis = System.currentTimeMillis(),
                    at = LocalDateTime.now(),
                    sessionEnd = 0L,
                    unlockUntil = app.runtime.unlockUntil(r.pkg),
                    opensToday = opens,
                    minutesToday = minutes,
                )
                request = when (decision) {
                    is EntryDecision.Limit -> {
                        app.log(EventType.LIMIT_HIT, r.pkg, extra = decision.kind.name)
                        r.copy(mode = Mode.LIMIT, limitKind = decision.kind.name, limit = decision.limit, nonce = System.nanoTime())
                    }
                    is EntryDecision.ScheduleBlock ->
                        r.copy(mode = Mode.SCHEDULE_BLOCK, scheduleId = decision.schedule.id, nonce = System.nanoTime())
                    else -> r.copy(mode = Mode.PAUSE, nonce = System.nanoTime())
                }
            }
        }
    }
}

interface InterventionActions {
    fun continueSession(pkg: String, minutes: Int, reason: String?)
    fun neverMind(pkg: String)
    fun goHome()
    fun dismissBlock(r: InterventionRequest)
    fun emergencyUnlock(pkg: String, minutes: Int)
    fun extend(r: InterventionRequest)
}
