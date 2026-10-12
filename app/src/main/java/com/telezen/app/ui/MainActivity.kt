package com.telezen.app.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.telezen.app.tz
import com.telezen.app.ui.screens.MainNav
import com.telezen.app.ui.screens.OnboardingScreen
import com.telezen.app.ui.screens.PinLockScreen
import com.telezen.app.ui.theme.TeleZenTheme
import com.telezen.app.work.SummaryWorker

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ROUTE = "route"
        private const val RELOCK_AFTER_MS = 30_000L
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_STOPPED_AT = "stopped_at"
    }

    private var unlocked by mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // With no PIN at launch, setting one shouldn't lock the user out on the spot.
        unlocked = savedInstanceState?.getBoolean(KEY_UNLOCKED) ?: (tz.settings.current.pinHash == null)
        stoppedAt = savedInstanceState?.getLong(KEY_STOPPED_AT) ?: 0L
        enableEdgeToEdge()
        val startRoute = intent.getStringExtra(EXTRA_ROUTE)
        SummaryWorker.runNow(this)
        setContent {
            val settings by tz.settings.state.collectAsState()
            TeleZenTheme(settings.theme) {
                when {
                    settings.pinHash != null && !unlocked -> PinLockScreen(settings) { unlocked = true }
                    !settings.onboardingDone -> OnboardingScreen()
                    else -> MainNav(startRoute)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS) unlocked = false
        stoppedAt = 0L
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked)
        outState.putLong(KEY_STOPPED_AT, stoppedAt)
    }
}
