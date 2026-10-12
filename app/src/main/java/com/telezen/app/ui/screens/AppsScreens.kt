package com.telezen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.telezen.app.core.AppEntry
import com.telezen.app.core.Apps
import com.telezen.app.core.plural
import com.telezen.app.data.GuardedApp
import com.telezen.app.tz
import com.telezen.app.ui.components.AppIcon
import com.telezen.app.ui.components.Screen
import com.telezen.app.ui.components.SectionCard
import com.telezen.app.ui.components.SettingRow
import com.telezen.app.ui.components.SwitchRow
import com.telezen.app.ui.components.rememberUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

fun describeGuard(g: GuardedApp): String = buildList {
    add(if (g.delaySeconds == 0) "No pause" else "${g.delaySeconds}s pause")
    if (g.maxOpensPerDay > 0) add("${g.maxOpensPerDay} opens/day")
    if (g.maxMinutesPerDay > 0) add("${g.maxMinutesPerDay} min/day")
}.joinToString(" · ")

@Composable
fun AppsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val settings by ctx.tz.settings.state.collectAsState()
    val update = rememberUpdater()

    Screen("Guarded apps") {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Guarded apps show a pause screen before they open. You can also set a daily limit on opens or minutes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (settings.guardedApps.isEmpty()) {
                    item {
                        SectionCard {
                            SettingRow(
                                title = "No guarded apps yet",
                                subtitle = "Tap Add app to choose one.",
                            )
                        }
                    }
                } else {
                    item {
                        SectionCard {
                            settings.guardedApps.forEach { g ->
                                SettingRow(
                                    title = Apps.label(ctx, g.packageName),
                                    subtitle = describeGuard(g),
                                    leading = { AppIcon(g.packageName) },
                                    onClick = { nav.navigate("app/${g.packageName}") },
                                    trailing = {
                                        Switch(
                                            checked = g.enabled,
                                            onCheckedChange = { on ->
                                                update { s ->
                                                    s.copy(guardedApps = s.guardedApps.map {
                                                        if (it.packageName == g.packageName) it.copy(enabled = on) else it
                                                    })
                                                }
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { nav.navigate("pick_app") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add app") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
fun AppPickerScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val settings by ctx.tz.settings.state.collectAsState()
    val update = rememberUpdater()
    var query by rememberSaveable { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { Apps.launchable(ctx) }
    }
    val existing = settings.guardedApps.map { it.packageName }.toSet()

    Screen("Choose an app", onBack = { nav.popBackStack() }) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val filtered = list.filter { it.packageName !in existing && it.label.contains(query.trim(), ignoreCase = true) }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(filtered, key = { it.packageName }) { entry ->
                    SettingRow(
                        title = entry.label,
                        leading = { AppIcon(entry.packageName) },
                        onClick = {
                            if (update { s -> s.copy(guardedApps = s.guardedApps + GuardedApp(entry.packageName)) }) {
                                nav.navigate("app/${entry.packageName}") {
                                    popUpTo("pick_app") { inclusive = true }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun AppEditScreen(nav: NavHostController, pkg: String) {
    val ctx = LocalContext.current
    val settings by ctx.tz.settings.state.collectAsState()
    val update = rememberUpdater()
    val g = settings.guardedApps.firstOrNull { it.packageName == pkg }
    var confirmDelete by remember { mutableStateOf(false) }

    if (g == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    fun change(t: (GuardedApp) -> GuardedApp) = update { s ->
        s.copy(guardedApps = s.guardedApps.map { if (it.packageName == pkg) t(it) else it })
    }

    Screen(Apps.label(ctx, pkg), onBack = { nav.popBackStack() }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    SwitchRow(
                        title = "Guard this app",
                        subtitle = "Show the pause screen and apply limits",
                        checked = g.enabled,
                        leading = { AppIcon(pkg) },
                    ) { on -> change { it.copy(enabled = on) } }
                }
            }
            item {
                SectionCard(title = "Pause screen") {
                    SliderSetting(
                        title = "Countdown",
                        value = g.delaySeconds,
                        range = 0..60,
                        step = 5,
                        format = { if (it == 0) "No countdown" else "$it seconds" },
                    ) { v -> change { it.copy(delaySeconds = v) } }
                    SwitchRow(
                        title = "Ask why I'm opening it",
                        subtitle = "Pick a reason on the pause screen. It's saved for your stats.",
                        checked = g.askReason,
                    ) { on -> change { it.copy(askReason = on) } }
                }
            }
            item {
                SectionCard(title = "Daily limits") {
                    StepperSetting(
                        title = "Opens per day",
                        value = g.maxOpensPerDay,
                        range = 0..50,
                        format = { if (it == 0) "No limit" else plural(it, "open") },
                    ) { v -> change { it.copy(maxOpensPerDay = v) } }
                    SliderSetting(
                        title = "Time per day",
                        value = g.maxMinutesPerDay,
                        range = 0..240,
                        step = 5,
                        format = { if (it == 0) "No limit" else "$it minutes" },
                    ) { v -> change { it.copy(maxMinutesPerDay = v) } }
                    Text(
                        "When a limit is reached, the app stays blocked until midnight. Emergency unlock is available unless strict mode is on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop guarding this app")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Stop guarding ${Apps.label(ctx, pkg)}?") },
            text = { Text("Its pause screen and limits will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // On success the screen sees the app is gone and pops itself.
                    update { s -> s.copy(guardedApps = s.guardedApps.filterNot { it.packageName == pkg }) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SliderSetting(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    format: (Int) -> String,
    onCommit: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val snapped = ((local / step).roundToInt() * step).coerceIn(range)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(format(snapped), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = {
                onCommit(snapped)
                // If the change was accepted, the new value re-keys this state; if strict mode
                // rejected it, snap the thumb back to the stored value.
                local = value.toFloat()
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
        )
    }
}

@Composable
fun StepperSetting(
    title: String,
    value: Int,
    range: IntRange,
    format: (Int) -> String,
    onCommit: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(format(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        FilledTonalIconButton(onClick = { onCommit((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = "Less")
        }
        Text("$value", modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = { onCommit((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = "More")
        }
    }
}
