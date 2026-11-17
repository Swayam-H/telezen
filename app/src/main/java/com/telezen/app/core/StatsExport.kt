package com.telezen.app.core

import com.telezen.app.TeleZenApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Screen time per app and blocked feeds per day, as a file the WatchZen laptop dashboard imports. */
object StatsExport {
    private const val DAYS = 30L
    private const val WEEKS_BACK = 42L

    suspend fun json(app: TeleZenApp): String = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val from = today.minusDays(DAYS - 1)
        val blocks = app.db.events().blocksByDay(from.toString(), today.toString()).groupBy { it.day }
        val skip = Apps.protectedPackages(app) // home screen time isn't app time
        buildJsonObject {
            put("app", "telezen")
            put("type", "stats")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            putJsonArray("days") {
                for (i in 0 until DAYS) {
                    val date = from.plusDays(i)
                    // Android only keeps usage events for about a week, so older days may be empty.
                    val usage = app.usage.day(date).filter { (pkg, ms) -> ms >= 60_000 && pkg !in skip }
                    val dayBlocks = blocks[date.toString()].orEmpty()
                    if (usage.isNotEmpty() || dayBlocks.isNotEmpty()) {
                        addJsonObject {
                            put("day", date.toString())
                            putJsonArray("apps") {
                                usage.forEach { (pkg, ms) ->
                                    addJsonObject {
                                        put("pkg", pkg)
                                        put("label", Apps.label(app, pkg))
                                        put("ms", ms)
                                    }
                                }
                            }
                            putJsonObject("blocks") {
                                dayBlocks.forEach { put(it.name ?: "other", it.count) }
                            }
                        }
                    }
                }
            }
            // Daily usage only goes back about 10 days; Android's weekly totals reach about 4 weeks further.
            putJsonArray("weeks") {
                val zone = ZoneId.systemDefault()
                val date = { ms: Long -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString() }
                app.usage.weeks(Day.startMillis(today.minusDays(WEEKS_BACK))).forEach { w ->
                    val apps = w.apps.filter { (pkg, ms) -> ms >= 60_000 && pkg !in skip }
                    if (apps.isNotEmpty()) addJsonObject {
                        put("start", date(w.start))
                        put("end", date(w.end))
                        putJsonArray("apps") {
                            apps.forEach { (pkg, ms) ->
                                addJsonObject {
                                    put("pkg", pkg)
                                    put("label", Apps.label(app, pkg))
                                    put("ms", ms)
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
    }
}
