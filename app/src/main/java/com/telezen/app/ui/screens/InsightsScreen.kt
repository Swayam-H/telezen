package com.telezen.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.telezen.app.core.plural
import com.telezen.app.core.Apps
import com.telezen.app.core.Day
import com.telezen.app.core.Permissions
import com.telezen.app.core.Policy
import com.telezen.app.core.Streaks
import com.telezen.app.data.db.DaySummary
import com.telezen.app.data.db.EventType
import com.telezen.app.tz
import com.telezen.app.ui.components.AppIcon
import com.telezen.app.ui.components.Screen
import com.telezen.app.ui.components.SectionCard
import com.telezen.app.ui.components.SettingRow
import com.telezen.app.ui.components.StatTile
import com.telezen.app.ui.components.rememberResumeTick
import com.telezen.app.ui.theme.isDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val BAR_LIGHT = Color(0xFF0F8A74)
private val BAR_DARK = Color(0xFF26A98F)

private data class Usage(val today: Map<String, Long>, val week: Map<String, Long>)

@Composable
fun InsightsScreen() {
    val ctx = LocalContext.current
    val app = ctx.tz
    val settings by app.settings.state.collectAsState()
    val rules by app.rules.rules.collectAsState()
    val tick = rememberResumeTick()
    var weekRange by rememberSaveable { mutableStateOf(false) }

    val todayDate = remember(tick) { LocalDate.now() }
    val today = todayDate.toString()
    val weekStart = todayDate.minusDays(6).toString()
    val from = if (weekRange) weekStart else today

    val hasUsage = remember(tick) { Permissions.usageAccess(ctx) }
    val usage by produceState(Usage(emptyMap(), emptyMap()), tick) {
        value = withContext(Dispatchers.IO) {
            Usage(
                today = app.usage.today(),
                week = app.usage.foregroundByPackage(Day.startMillis(todayDate.minusDays(6)), System.currentTimeMillis()),
            )
        }
    }
    val summaries by remember(todayDate) {
        app.db.summaries().range(todayDate.minusDays(13).toString(), today)
    }.collectAsState(emptyList())
    val allSummaries by remember { app.db.summaries().all() }.collectAsState(emptyList())

    val events = app.db.events()
    val opensByPkg by remember(from) { events.countByPkg(from, today, EventType.SESSION_START) }.collectAsState(emptyList())
    val abandonedByPkg by remember(from) { events.countByPkg(from, today, EventType.PAUSE_ABANDONED) }.collectAsState(emptyList())
    val blocks by remember(from) { events.countRange(from, today, EventType.SHORT_BLOCK) }.collectAsState(0)
    val reasons by remember(from) { events.reasons(from, today) }.collectAsState(emptyList())

    val tracked = remember(settings, rules) { Policy.trackedPackages(settings, rules) }
    val rangeUsage = if (weekRange) usage.week else usage.today
    val trackedMs = tracked.sumOf { rangeUsage[it] ?: 0L }
    val todayTrackedMin = (tracked.sumOf { usage.today[it] ?: 0L } / 60_000).toInt()

    // Live value for today; stored summaries for the past.
    val days = remember(summaries, todayTrackedMin, settings.dailyGoalMinutes) {
        (13 downTo 0).map { i ->
            val d = todayDate.minusDays(i.toLong())
            val minutes = if (i == 0) todayTrackedMin else summaries.firstOrNull { it.day == d.toString() }?.trackedMinutes
            d to minutes
        }
    }
    val thisWeek = days.takeLast(7).sumOf { it.second ?: 0 }
    val lastWeek = days.take(7).sumOf { it.second ?: 0 }
    val lastWeekHasData = days.take(7).any { it.second != null }

    val streak = remember(allSummaries, todayTrackedMin, settings.dailyGoalMinutes) {
        val live = allSummaries.filterNot { it.day == today } +
            DaySummary(today, todayTrackedMin, settings.dailyGoalMinutes, 0, 0, 0)
        Streaks.compute(live, todayDate)
    }

    val opens = opensByPkg.associate { it.name to it.count }
    val abandoned = abandonedByPkg.associate { it.name to it.count }

    Screen("Insights") {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!weekRange, { weekRange = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Today") }
                    SegmentedButton(weekRange, { weekRange = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Last 7 days") }
                }
            }
            if (!hasUsage) {
                item {
                    SectionCard {
                        SettingRow(
                            title = "Usage access is off",
                            subtitle = "Screen time and goals need usage access. Tap to allow it.",
                            onClick = { Permissions.startSafely(ctx, Permissions.usageAccessIntent()) },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("on tracked apps", Day.formatDuration(trackedMs), Modifier.weight(1f))
                    StatTile("sessions", "${opensByPkg.sumOf { it.count }}", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("skipped opens", "${abandonedByPkg.sumOf { it.count }}", Modifier.weight(1f))
                    StatTile("feeds blocked", "$blocks", Modifier.weight(1f))
                }
            }
            item {
                SectionCard(title = "Goal and streak") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${streak.current} day${if (streak.current == 1) "" else "s"}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "under ${settings.dailyGoalMinutes} min on tracked apps in a row · best ${streak.best}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (todayTrackedMin <= settings.dailyGoalMinutes) {
                                "Today: ${todayTrackedMin} min, ${settings.dailyGoalMinutes - todayTrackedMin} min left before your goal"
                            } else "Today: ${todayTrackedMin} min, ${todayTrackedMin - settings.dailyGoalMinutes} min over your goal",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                SectionCard(title = "Daily time on tracked apps, last 14 days") {
                    val change = if (lastWeekHasData && lastWeek > 0) ((thisWeek - lastWeek) * 100 / lastWeek) else null
                    Text(
                        "This week ${Day.formatDuration(thisWeek * 60_000L)}" + when {
                            change == null -> ""
                            change < 0 -> ", ${-change}% less than last week"
                            change > 0 -> ", $change% more than last week"
                            else -> ", same as last week"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    DailyBarChart(days, settings.dailyGoalMinutes, isDark(settings.theme))
                }
            }
            item {
                SectionCard(title = if (weekRange) "Tracked apps, last 7 days" else "Tracked apps today") {
                    val rows = tracked.filter { Apps.isInstalled(ctx, it) }
                        .sortedByDescending { rangeUsage[it] ?: 0L }
                    if (rows.isEmpty()) SettingRow(title = "No tracked apps yet", subtitle = "Guard an app or block a feed to track it.")
                    rows.forEach { pkg ->
                        val o = opens[pkg] ?: 0
                        val a = abandoned[pkg] ?: 0
                        SettingRow(
                            title = Apps.label(ctx, pkg),
                            subtitle = buildList {
                                if (o > 0) add(plural(o, "session"))
                                if (a > 0) add("changed mind $a×")
                            }.joinToString(" · ").ifEmpty { null },
                            leading = { AppIcon(pkg, 32.dp) },
                            trailing = { Text(Day.formatDuration(rangeUsage[pkg] ?: 0L), style = MaterialTheme.typography.bodyMedium) },
                        )
                    }
                }
            }
            if (reasons.isNotEmpty()) {
                item {
                    SectionCard(title = "Why you opened apps") {
                        reasons.take(5).forEach { r ->
                            SettingRow(title = r.name ?: "", trailing = { Text("${r.count}×") })
                        }
                    }
                }
            }
            item {
                val home = remember { Apps.homePackages(ctx) }
                val others = rangeUsage.filterKeys { it !in tracked && it !in home && it != ctx.packageName && it != "com.android.systemui" }
                    .entries.sortedByDescending { it.value }.take(5)
                if (others.isNotEmpty()) {
                    SectionCard(title = "Other apps you used most") {
                        others.forEach { (pkg, ms) ->
                            SettingRow(
                                title = Apps.label(ctx, pkg),
                                leading = { AppIcon(pkg, 32.dp) },
                                trailing = { Text(Day.formatDuration(ms), style = MaterialTheme.typography.bodyMedium) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Single-series bars (one hue, no legend) with a dashed goal line. Tap a bar to read its value. */
@Composable
private fun DailyBarChart(days: List<Pair<LocalDate, Int?>>, goal: Int, dark: Boolean) {
    var selected by remember(days) { mutableIntStateOf(days.lastIndex) }
    val bar = if (dark) BAR_DARK else BAR_LIGHT
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val selectedBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val maxMinutes = maxOf(goal, days.maxOf { it.second ?: 0 }).coerceAtLeast(1)
    val (selDate, selMin) = days[selected]
    val description = days.joinToString("; ") { (d, m) -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.dayOfMonth}: ${m?.let { "$it minutes" } ?: "no data"}" }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "${selDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${selDate.dayOfMonth}: " +
                (selMin?.let { "${Day.formatDuration(it * 60_000L)} (${if (it <= goal) "under" else "over"} goal)" } ?: "no data"),
            style = MaterialTheme.typography.labelLarge,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(top = 8.dp)
                .semantics { contentDescription = "Daily minutes on tracked apps. Goal $goal minutes. $description" }
                .pointerInput(days) {
                    detectTapGestures { pos ->
                        selected = (pos.x / (size.width / days.size)).toInt().coerceIn(0, days.lastIndex)
                    }
                },
        ) {
            val slot = size.width / days.size
            val gap = 2.dp.toPx()
            val barWidth = (slot * 0.6f).coerceAtLeast(4.dp.toPx())
            val radius = 4.dp.toPx()
            val chartH = size.height
            fun y(min: Int) = chartH - chartH * (min / maxMinutes.toFloat())

            drawLine(grid, Offset(0f, chartH), Offset(size.width, chartH), strokeWidth = 1.dp.toPx())
            drawRect(selectedBg, Offset(selected * slot + gap / 2, 0f), Size(slot - gap, chartH))

            days.forEachIndexed { i, (_, m) ->
                if (m == null || m == 0) return@forEachIndexed
                val left = i * slot + (slot - barWidth) / 2
                val top = y(m)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = top, right = left + barWidth, bottom = chartH,
                            topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                        ),
                    )
                }
                drawPath(path, bar)
            }
            val gy = y(goal)
            drawLine(
                muted,
                Offset(0f, gy),
                Offset(size.width, gy),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                days.first().first.let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}" },
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                modifier = Modifier.weight(1f),
            )
            Text("dashed line: ${goal} min goal", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
            Text("Today", style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}
