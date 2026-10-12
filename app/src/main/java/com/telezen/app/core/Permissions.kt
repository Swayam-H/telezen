package com.telezen.app.core

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.telezen.app.service.AdminReceiver
import com.telezen.app.service.TeleZenAccessibilityService

object Permissions {
    fun accessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, TeleZenAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun accessibilityRunning(): Boolean = TeleZenAccessibilityService.instance != null

    fun usageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    fun notifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun batteryExempt(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun adminActive(ctx: Context): Boolean =
        ctx.getSystemService(DevicePolicyManager::class.java)
            .isAdminActive(ComponentName(ctx, AdminReceiver::class.java))

    fun accessibilitySettingsIntent() =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun usageAccessIntent() =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun appDetailsIntent(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    @Suppress("BatteryLife")
    fun batteryIntent(ctx: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))

    fun addAdminIntent(ctx: Context) =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(ctx, AdminReceiver::class.java))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Prevents TeleZen from being uninstalled until you turn uninstall protection off in TeleZen.",
            )

    fun removeAdmin(ctx: Context) {
        ctx.getSystemService(DevicePolicyManager::class.java)
            .removeActiveAdmin(ComponentName(ctx, AdminReceiver::class.java))
    }

    fun startSafely(ctx: Context, intent: Intent, fallback: Intent? = null) {
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            fallback?.let { runCatching { ctx.startActivity(it) } }
        }
    }
}
