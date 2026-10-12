package com.telezen.app

import android.app.Application
import android.content.Context
import com.telezen.app.core.Day
import com.telezen.app.core.Notifications
import com.telezen.app.core.UsageStatsHelper
import com.telezen.app.data.RuntimeState
import com.telezen.app.data.SettingsStore
import com.telezen.app.data.db.EventEntity
import com.telezen.app.data.db.TeleZenDatabase
import com.telezen.app.rules.RulesRepository
import com.telezen.app.work.SummaryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TeleZenApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var runtime: RuntimeState
        private set
    lateinit var db: TeleZenDatabase
        private set
    lateinit var rules: RulesRepository
        private set
    lateinit var usage: UsageStatsHelper
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        runtime = RuntimeState(this)
        db = TeleZenDatabase.build(this)
        rules = RulesRepository(this)
        usage = UsageStatsHelper(this)
        runtime.ensureFirstRun()
        Notifications.createChannels(this)
        SummaryWorker.schedule(this)
    }

    fun log(type: String, pkg: String, targetId: String? = null, extra: String? = null) {
        val now = System.currentTimeMillis()
        scope.launch {
            db.events().insert(EventEntity(ts = now, day = Day.of(now), pkg = pkg, type = type, targetId = targetId, extra = extra))
        }
    }
}

val Context.tz: TeleZenApp get() = applicationContext as TeleZenApp
