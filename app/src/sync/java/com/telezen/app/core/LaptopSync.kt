package com.telezen.app.core

import android.content.Context
import android.text.format.DateUtils
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.telezen.app.TeleZenApp
import com.telezen.app.tz
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Sync build only: pushes the stats export to the WatchZen helper running on the user's laptop.
 * The pairing code (address:port/token) is kept here, not in AppSettings, so it never ends up in a settings export.
 */
object LaptopSync {
    const val available = true
    private const val WORK = "laptop_sync"

    data class Pairing(val hostPort: String, val token: String)
    data class Outcome(val ok: Boolean, val message: String)

    private val CODE = Regex("""^([A-Za-z0-9.\-]+:\d{1,5})/([A-Za-z0-9_\-]{16,64})$""")

    /** Reads the code the helper prints, like 192.168.1.5:4747/AbC_dEf... */
    fun parse(code: String): Pairing? =
        CODE.matchEntire(code.trim())?.let { Pairing(it.groupValues[1], it.groupValues[2]) }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("laptop_sync", Context.MODE_PRIVATE)

    fun pairing(ctx: Context): Pairing? = prefs(ctx).getString("code", null)?.let(::parse)

    fun pair(ctx: Context, code: String): Boolean {
        if (parse(code) == null) return false
        prefs(ctx).edit().clear().putString("code", code.trim()).apply()
        schedule(ctx)
        return true
    }

    fun unpair(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
    }

    /** The last result, for the settings screen, e.g. "Synced · 5 minutes ago". */
    fun status(ctx: Context): String? {
        val p = prefs(ctx)
        val message = p.getString("status", null) ?: return null
        val ago = DateUtils.getRelativeTimeSpanString(p.getLong("at", 0), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        return "$message · $ago"
    }

    /** Hourly while paired. An unreachable laptop is normal (you're out), so the job never fails. */
    fun schedule(ctx: Context) {
        if (pairing(ctx) == null) return
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    suspend fun push(app: TeleZenApp): Outcome = withContext(Dispatchers.IO) {
        val p = pairing(app) ?: return@withContext Outcome(false, "Not paired with a laptop yet.")
        val outcome = try {
            val body = StatsExport.json(app).toByteArray()
            val conn = URL("http://${p.hostPort}/phone").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 5_000
                conn.readTimeout = 10_000
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer ${p.token}")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body) }
                when (val code = conn.responseCode) {
                    200 -> Outcome(true, "Synced")
                    401 -> Outcome(false, "The laptop rejected the pairing code. Copy it again from the helper window.")
                    else -> Outcome(false, "The laptop answered with error $code.")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome(false, "Couldn't reach the laptop. Is the helper running, and are both on the same Wi-Fi?")
        }
        prefs(app).edit().putString("status", outcome.message).putLong("at", System.currentTimeMillis()).apply()
        outcome
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        LaptopSync.push(applicationContext.tz)
        return Result.success()
    }
}
