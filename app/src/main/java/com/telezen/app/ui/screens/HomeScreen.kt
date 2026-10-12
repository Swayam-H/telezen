package com.telezen.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.telezen.app.core.plural
import com.telezen.app.core.Apps
import com.telezen.app.core.Day
import com.telezen.app.core.Health
import com.telezen.app.core.HealthAction
import com.telezen.app.core.HealthIssue
import com.telezen.app.core.Permissions
import com.telezen.app.core.Policy
import com.telezen.app.core.ScheduleLogic
import com.telezen.app.core.Severity
import com.telezen.app.data.db.EventType
import com.telezen.app.tz
import com.telezen.app.ui.components.AppIcon
import com.telezen.app.ui.components.Screen
import com.telezen.app.ui.components.SectionCard
import com.telezen.app.ui.components.SettingRow
import com.telezen.app.ui.components.StatTile
import com.telezen.app.ui.components.SwitchRow
import com.telezen.app.ui.components.rememberResumeTick
import com.telezen.app.ui.components.rememberUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDateTime
import java.util.Date

@Composable
fun HomeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val app = ctx.tz
    val settings by app.settings.state.collectAsState()
    val rules by app.rules.rules.collectAsState()
    val update = rememberUpdater()
    val tick = rememberResumeTick()
    val today = remember(tick) { Day.today() }

    val issues by produceState(emptyList<HealthIssue>(), tick, settings, rules) {
        value = withContext(Dispatchers.IO) { Health.check(ctx, app) }
    }
    val blocks by remember(today) { app.db.events().blocksByTarget(today, today) }.collectAsState(emptyList())
    val opens by remember(today) { app.db.events().countRange(today, today, EventType.SESSION_START) }.collectAsState(0)
    val abandoned by remember(today) { app.db.events().countRange(today, today, EventType.PAUSE_ABANDONED) }.collectAsState(0)
    val blockCount = blocks.associate { it.name to it.count }
    val activeSchedules = remember(settings, tick) { Policy.activeSchedules(settings, LocalDateTime.now()) }

    Screen("TeleZen") {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.masterEnabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (settings.masterEnabled) "Protection is on" else "Protection is paused",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                if (settings.masterEnabled) "${blocks.sumOf { it.count }} short-form feeds blocked today"
                                else "Nothing is being blocked right now",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = settings.masterEnabled,
                            onCheckedChange = { on -> update { it.copy(masterEnabled = on) } },
                        )
                    }
                }
            }

            if (settings.strictActive()) {
                item {
                    val until = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.strictUntil))
                    Banner(Icons.Outlined.Lock, MaterialTheme.colorScheme.secondaryContainer, "Strict mode is on until $until", "You can tighten your settings, but not loosen them.")
                }
            }

            items(issues) { issue -> IssueCard(issue, nav) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("feeds blocked", "${blocks.sumOf { it.count }}", Modifier.weight(1f))
                    StatTile("app sessions", "$opens", Modifier.weight(1f))
                    StatTile("skipped opens", "$abandoned", Modifier.weight(1f))
                }
            }

            item {
                SectionCard(title = "Short-form blocking") {
                    rules.targets
                        .filter { !it.experimental || it.id in settings.enabledTargets }
                        .forEach { t ->
                            val installedPkg = t.packages.firstOrNull { Apps.isInstalled(ctx, it) }
                            val count = blockCount[t.id] ?: 0
                            SwitchRow(
                                title = t.name,
                                subtitle = when {
                                    installedPkg == null && t.id != "browser_short_form" -> "Not installed"
                                    t.id in settings.enabledTargets -> "Blocked $count time${if (count == 1) "" else "s"} today"
                                    else -> "Not blocked"
                                },
                                checked = t.id in settings.enabledTargets,
                                enabled = settings.masterEnabled,
                                leading = { AppIcon(installedPkg ?: t.packages.first(), 36.dp) },
                            ) { on ->
                                update { s -> s.copy(enabledTargets = if (on) s.enabledTargets + t.id else s.enabledTargets - t.id) }
                            }
                        }
                    TextButton(onClick = { nav.navigate("rules") }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("More apps and detection rules")
                    }
                }
            }

            item {
                SectionCard(title = "Schedules") {
                    if (activeSchedules.isEmpty()) {
                        SettingRow(
                            title = if (settings.schedules.isEmpty()) "No schedules yet" else "No schedule active right now",
                            subtitle = "Block apps or feeds at set times, like work hours or bedtime.",
                            onClick = { nav.navigate("schedules") },
                        )
                    } else {
                        activeSchedules.forEach { s ->
                            SettingRow(
                                title = "${s.name} is active",
                                subtitle = "${ScheduleLogic.describe(s)} · ${plural(s.packages.size, "app")}",
                                onClick = { nav.navigate("schedule/${s.id}") },
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Guarded apps") {
                    val guarded = settings.guardedApps.filter { it.enabled }
                    if (guarded.isEmpty()) {
                        SettingRow(
                            title = "Add a pause before distracting apps",
                            subtitle = "A short countdown makes you choose instead of opening on autopilot.",
                            onClick = { nav.navigate("pick_app") },
                        )
                    } else {
                        guarded.take(5).forEach { g ->
                            SettingRow(
                                title = Apps.label(ctx, g.packageName),
                                subtitle = "${g.delaySeconds}s pause",
                                leading = { AppIcon(g.packageName, 32.dp) },
                                onClick = { nav.navigate("app/${g.packageName}") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, body: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun IssueCard(issue: HealthIssue, nav: NavHostController) {
    val ctx = LocalContext.current
    val (icon, color) = when (issue.severity) {
        Severity.ERROR -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.errorContainer
        Severity.WARNING -> Icons.Outlined.WarningAmber to MaterialTheme.colorScheme.secondaryContainer
        Severity.INFO -> Icons.Outlined.Info to MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.titleSmall)
                Text(issue.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val action = issue.action
        if (action != null) {
            TextButton(
                onClick = {
                    when (action) {
                        HealthAction.ACCESSIBILITY -> Permissions.startSafely(ctx, Permissions.accessibilitySettingsIntent())
                        HealthAction.USAGE_ACCESS -> Permissions.startSafely(ctx, Permissions.usageAccessIntent(), Permissions.appDetailsIntent(ctx))
                        HealthAction.NOTIFICATIONS -> Permissions.startSafely(
                            ctx,
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                            Permissions.appDetailsIntent(ctx),
                        )
                        HealthAction.BATTERY -> nav.navigate("reliability")
                        HealthAction.RULES -> nav.navigate("rules")
                    }
                },
                modifier = Modifier.padding(start = 44.dp, bottom = 4.dp),
            ) {
                Text(
                    when (action) {
                        HealthAction.ACCESSIBILITY -> "Open accessibility settings"
                        HealthAction.USAGE_ACCESS -> "Allow usage access"
                        HealthAction.NOTIFICATIONS -> "Allow notifications"
                        HealthAction.BATTERY -> "Fix"
                        HealthAction.RULES -> "Detection rules"
                    },
                )
            }
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}
