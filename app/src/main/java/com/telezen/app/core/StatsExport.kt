package com.telezen.app.core

import com.telezen.app.TeleZenApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate

/** Screen time per app and blocked feeds per day, as a file the WatchZen laptop dashboard imports. */
object StatsExport {
    private const val DAYS = 30L

    suspend fun json(app: TeleZenApp): String = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val from = today.minusDays(DAYS - 1)
        val blocks = app.db.events().blocksByDay(from.toString(), today.toString()).groupBy { it.day }
        buildJsonObject {
            put("app", "telezen")
            put("type", "stats")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            putJsonArray("days") {
                for (i in 0 until DAYS) {
                    val date = from.plusDays(i)
                    // Android only keeps usage events for about a week, so older days may be empty.
                    val usage = app.usage.day(date).filterValues { it >= 60_000 }
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
        }.toString()
    }
}
