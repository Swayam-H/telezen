package com.telezen.app.service

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.telezen.app.tz
import com.telezen.app.work.SummaryWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SummaryWorker.schedule(context)
        SummaryWorker.runNow(context)
    }
}

class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val strict = context.tz.settings.current.strictActive()
        return if (strict) "Strict mode is on. TeleZen will block this until strict mode ends."
        else "Turning this off lets TeleZen be uninstalled."
    }
}
