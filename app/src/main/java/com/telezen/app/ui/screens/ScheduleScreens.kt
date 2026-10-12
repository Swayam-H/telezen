package com.telezen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.telezen.app.core.plural
import com.telezen.app.core.AppEntry
import com.telezen.app.core.Apps
import com.telezen.app.core.ScheduleLogic
import com.telezen.app.data.Schedule
import com.telezen.app.data.ScheduleMode
import com.telezen.app.tz
import com.telezen.app.ui.components.AppIcon
import com.telezen.app.ui.components.Screen
import com.telezen.app.ui.components.SectionCard
import com.telezen.app.ui.components.SettingRow
import com.telezen.app.ui.components.rememberResumeTick
import com.telezen.app.ui.components.rememberSnack
import com.telezen.app.ui.components.rememberUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

@Composable
fun SchedulesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val settings by ctx.tz.settings.state.collectAsState()
    val update = rememberUpdater()
    val tick = rememberResumeTick()
    val now = remember(tick, settings) { LocalDateTime.now() }

    Screen("Schedules") {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Schedules block apps or short-form feeds at set times, like a focus block during work or a bedtime wind-down.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    SectionCard {
                        if (settings.schedules.isEmpty()) {
                            SettingRow(title = "No schedules yet", subtitle = "Tap New schedule to create one.")
                        }
                        settings.schedules.forEach { s ->
                            val active = ScheduleLogic.isActive(s, now)
                            SettingRow(
                                title = s.name + if (active) " · active now" else "",
                                subtitle = "${ScheduleLogic.describe(s)} · " +
                                    (if (s.mode == ScheduleMode.BLOCK) "Block" else "Pause") +
                                    " ${plural(s.packages.size, "app")}" +
                                    if (s.targetIds.isNotEmpty()) " + ${plural(s.targetIds.size, "feed")}" else "",
                                onClick = { nav.navigate("schedule/${s.id}") },
                                trailing = {
                                    Switch(checked = s.enabled, onCheckedChange = { on ->
                                        update { st -> st.copy(schedules = st.schedules.map { if (it.id == s.id) it.copy(enabled = on) else it }) }
                                    })
                                },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { nav.navigate("schedule/new") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New schedule") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(nav: NavHostController, id: String) {
    val ctx = LocalContext.current
    val app = ctx.tz
    val update = rememberUpdater()
    val snack = rememberSnack()
    val rules by app.rules.rules.collectAsState()
    val existing = remember(id) { app.settings.current.schedules.firstOrNull { it.id == id } }
    var draft by remember(id) {
        mutableStateOf(existing ?: Schedule(id = UUID.randomUUID().toString(), name = "Focus"))
    }
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val apps by produceState<List<AppEntry>>(emptyList()) {
        value = withContext(Dispatchers.IO) { Apps.launchable(ctx) }
    }
    val guardedPkgs = app.settings.current.guardedApps.map { it.packageName }.toSet()
    val sortedApps = remember(apps) {
        apps.sortedWith(compareByDescending<AppEntry> { it.packageName in draft.packages || it.packageName in guardedPkgs }.thenBy { it.label.lowercase() })
    }

    fun save() {
        if (draft.name.isBlank()) return snack("Give the schedule a name.")
        if (draft.days.isEmpty()) return snack("Pick at least one day.")
        if (draft.packages.isEmpty() && draft.targetIds.isEmpty()) return snack("Pick at least one app or feed.")
        val ok = update { s ->
            if (s.schedules.any { it.id == draft.id }) s.copy(schedules = s.schedules.map { if (it.id == draft.id) draft else it })
            else s.copy(schedules = s.schedules + draft)
        }
        if (ok) nav.popBackStack()
    }

    Screen(
        if (existing == null) "New schedule" else draft.name,
        onBack = { nav.popBackStack() },
        actions = { TextButton(onClick = ::save) { Text("Save") } },
    ) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(40)) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SectionCard(title = "When") {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ScheduleLogic.DAY_SHORT.forEachIndexed { i, label ->
                            val day = i + 1
                            FilterChip(
                                selected = day in draft.days,
                                onClick = { draft = draft.copy(days = if (day in draft.days) draft.days - day else draft.days + day) },
                                label = { Text(label) },
                            )
                        }
                    }
                    SettingRow(title = "Starts", subtitle = ScheduleLogic.formatMinute(draft.startMinute), onClick = { editingStart = true })
                    SettingRow(
                        title = "Ends",
                        subtitle = ScheduleLogic.formatMinute(draft.endMinute) +
                            when {
                                draft.endMinute == draft.startMinute -> " (all day)"
                                draft.endMinute < draft.startMinute -> " the next day"
                                else -> ""
                            },
                        onClick = { editingStart = false },
                    )
                }
            }
            item {
                SectionCard(title = "During this schedule") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SegmentedButton(
                            selected = draft.mode == ScheduleMode.BLOCK,
                            onClick = { draft = draft.copy(mode = ScheduleMode.BLOCK) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("Block apps") }
                        SegmentedButton(
                            selected = draft.mode == ScheduleMode.PAUSE,
                            onClick = { draft = draft.copy(mode = ScheduleMode.PAUSE) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("Pause first") }
                    }
                    Text(
                        if (draft.mode == ScheduleMode.BLOCK) "Selected apps can't be opened while the schedule is active."
                        else "Selected apps show a pause screen before opening.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                SectionCard(title = "Also block these feeds") {
                    rules.targets.forEach { t ->
                        CheckRow(t.name, t.id in draft.targetIds) { on ->
                            draft = draft.copy(targetIds = if (on) draft.targetIds + t.id else draft.targetIds - t.id)
                        }
                    }
                }
            }
            item {
                Text(
                    "Apps (${draft.packages.size} selected)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(sortedApps, key = { it.packageName }) { a ->
                SettingRow(
                    title = a.label,
                    leading = { AppIcon(a.packageName, 32.dp) },
                    onClick = {
                        draft = draft.copy(packages = if (a.packageName in draft.packages) draft.packages - a.packageName else draft.packages + a.packageName)
                    },
                    trailing = {
                        Checkbox(checked = a.packageName in draft.packages, onCheckedChange = { on ->
                            draft = draft.copy(packages = if (on) draft.packages + a.packageName else draft.packages - a.packageName)
                        })
                    },
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (existing != null) {
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Delete") }
                    }
                    Button(onClick = ::save, modifier = Modifier.weight(1f)) { Text("Save") }
                }
            }
        }
    }

    editingStart?.let { start ->
        val minute = if (start) draft.startMinute else draft.endMinute
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editingStart = null },
            title = { Text(if (start) "Start time" else "End time") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    draft = if (start) draft.copy(startMinute = m) else draft.copy(endMinute = m)
                    editingStart = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editingStart = null }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${draft.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (update { s -> s.copy(schedules = s.schedules.filterNot { it.id == draft.id }) }) nav.popBackStack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        title = title,
        onClick = { onChange(!checked) },
        trailing = { Checkbox(checked = checked, onCheckedChange = onChange) },
    )
}
