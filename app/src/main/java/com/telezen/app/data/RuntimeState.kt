package com.telezen.app.data

import android.content.Context
import androidx.core.content.edit

/** Small, frequently-changing state that must survive the service or process restarting. */
class RuntimeState(context: Context) {
    private val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)

    fun sessionEnd(pkg: String): Long = prefs.getLong("session_$pkg", 0L)

    fun startSession(pkg: String, minutes: Int) = prefs.edit {
        putLong("session_$pkg", System.currentTimeMillis() + minutes * 60_000L)
    }

    fun endSession(pkg: String) = prefs.edit { remove("session_$pkg") }

    fun unlockUntil(pkg: String): Long = prefs.getLong("unlock_$pkg", 0L)

    fun grantUnlock(pkg: String, minutes: Int) = prefs.edit {
        putLong("unlock_$pkg", System.currentTimeMillis() + minutes * 60_000L)
    }

    fun lastMatch(targetId: String): Long = prefs.getLong("match_$targetId", 0L)

    fun lastWorkingVersion(pkg: String): String? = prefs.getString("works_$pkg", null)

    fun recordMatch(targetId: String, pkg: String, installedVersion: String?) = prefs.edit {
        putLong("match_$targetId", System.currentTimeMillis())
        if (installedVersion != null) putString("works_$pkg", installedVersion)
    }

    fun snapshot(pkg: String): String? = prefs.getString("snap_$pkg", null)

    fun snapshotTime(pkg: String): Long = prefs.getLong("snaptime_$pkg", 0L)

    fun saveSnapshot(pkg: String, text: String) = prefs.edit {
        putString("snap_$pkg", text)
        putLong("snaptime_$pkg", System.currentTimeMillis())
    }

    val firstRun: Long get() = prefs.getLong("first_run", 0L)

    fun ensureFirstRun() {
        if (firstRun == 0L) prefs.edit { putLong("first_run", System.currentTimeMillis()) }
    }

    var lastSummaryDay: String?
        get() = prefs.getString("last_summary_day", null)
        set(value) = prefs.edit { putString("last_summary_day", value) }
}
