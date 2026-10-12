package com.telezen.app.core

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.telezen.app.R
import com.telezen.app.ui.MainActivity

object Notifications {
    private const val SUMMARY_CHANNEL = "daily_summary"
    private const val SUMMARY_ID = 1

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(SUMMARY_CHANNEL, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your end-of-day screen time summary"
            },
        )
    }

    @SuppressLint("MissingPermission")
    fun showSummary(ctx: Context, title: String, text: String) {
        if (!Permissions.notifications(ctx)) return
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "insights"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, SUMMARY_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(SUMMARY_ID, n)
    }
}
