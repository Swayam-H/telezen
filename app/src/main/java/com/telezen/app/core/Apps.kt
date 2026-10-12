package com.telezen.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val packageName: String, val label: String)

object Apps {
    private val icons = LruCache<String, ImageBitmap>(80)
    private val labels = HashMap<String, String>()

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun label(ctx: Context, pkg: String): String = synchronized(labels) {
        labels.getOrPut(pkg) {
            try {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
            }
        }
    }

    fun versionName(ctx: Context, pkg: String): String? = try {
        ctx.packageManager.getPackageInfo(pkg, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun icon(ctx: Context, pkg: String): ImageBitmap? {
        icons.get(pkg)?.let { return it }
        return try {
            val bmp = ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            icons.put(pkg, bmp)
            bmp
        } catch (e: Exception) {
            null
        }
    }

    /** Apps the user can guard or schedule. Protected apps are excluded: guarding one could trap the user. */
    fun launchable(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val excluded = protectedPackages(ctx)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != ctx.packageName && it !in excluded }
            .map { AppEntry(it, label(ctx, it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun homePackages(ctx: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    /** Home screens and the default phone app are never paused or blocked. */
    fun protectedPackages(ctx: Context): Set<String> {
        val dialer = runCatching { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        return homePackages(ctx) + listOfNotNull(dialer)
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent) }.isSuccess
    }

    fun goHome(ctx: Context) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }
}
