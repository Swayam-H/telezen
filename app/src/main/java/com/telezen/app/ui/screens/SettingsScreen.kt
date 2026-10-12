package com.telezen.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.telezen.app.BuildConfig
import com.telezen.app.core.Permissions
import com.telezen.app.core.Pin
import com.telezen.app.data.BlockBehavior
import com.telezen.app.data.ThemeMode
import com.telezen.app.data.UpdateResult
import com.telezen.app.tz
import com.telezen.app.ui.components.Screen
import com.telezen.app.ui.components.SectionCard
import com.telezen.app.ui.components.SettingRow
import com.telezen.app.ui.components.SwitchRow
import com.telezen.app.ui.components.rememberResumeTick
import com.telezen.app.ui.components.rememberSnack
import com.telezen.app.ui.components.rememberUpdater
import java.text.DateFormat
import java.util.Date

private val STRICT_DURATIONS = listOf(
    "1 hour" to 1L,
    "4 hours" to 4L,
    "1 day" to 24L,
    "3 days" to 72L,
    "1 week" to 168L,
)

@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val app = ctx.tz
    val settings by app.settings.state.collectAsState()
    val update = rememberUpdater()
    val snack = rememberSnack()
    val tick = rememberResumeTick()
    val adminActive = remember(tick) { Permissions.adminActive(ctx) }

    var strictDialog by rememberSaveable { mutableStateOf(false) }
    var pinDialog by rememberSaveable { mutableStateOf<PinDialogMode?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)!!.use { it.write(app.settings.exportJson().toByteArray()) }
            }.onSuccess { snack("Settings exported") }.onFailure { snack("Couldn't write the file") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }.getOrNull()
            when (val r = text?.let { app.settings.importJson(it) }) {
                null -> snack("Couldn't read the file")
                UpdateResult.Ok -> snack("Settings imported")
                is UpdateResult.Rejected -> snack(r.reason)
            }
        }
    }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    Screen("Settings") {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard(title = "When a short-form feed is detected") {
                    RadioRow(
                        "Go back and show a message",
                        "A full-screen note, then you're back in the app.",
                        settings.blockBehavior == BlockBehavior.OVERLAY,
                    ) { update { it.copy(blockBehavior = BlockBehavior.OVERLAY) } }
                    RadioRow(
                        "Just go back",
                        "Quietly leaves the feed with a short toast.",
                        settings.blockBehavior == BlockBehavior.BACK,
                    ) { update { it.copy(blockBehavior = BlockBehavior.BACK) } }
                }
            }
            item {
                SectionCard(title = "Mindfulness") {
                    SwitchRow(
                        title = "Breathing exercise",
                        subtitle = "Show a breathing circle on the pause screen instead of a countdown ring.",
                        checked = settings.breathingExercise,
                    ) { on -> update { it.copy(breathingExercise = on) } }
                    SettingRow(
                        title = "Prompts",
                        subtitle = "${settings.prompts.size} messages shown on pause and block screens",
                        onClick = { nav.navigate("prompts") },
                    )
                }
            }
            item {
                SectionCard(title = "Daily goal") {
                    SliderSetting(
                        title = "Time on tracked apps",
                        value = settings.dailyGoalMinutes,
                        range = 15..240,
                        step = 15,
                        format = { "Under ${it} min" },
                    ) { v -> update { it.copy(dailyGoalMinutes = v) } }
                    Text(
                        "Tracked apps are your guarded apps, apps in schedules, and apps with blocked feeds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    SwitchRow(
                        title = "End-of-day summary",
                        subtitle = "A notification around 9 pm.",
                        checked = settings.summaryNotification,
                    ) { on -> update { it.copy(summaryNotification = on) } }
                }
            }
            item {
                SectionCard(title = "Strict mode") {
                    val strict = settings.strictActive()
                    SettingRow(
                        title = if (strict) "On until ${formatTime(settings.strictUntil)}" else "Off",
                        subtitle = "While strict mode is on, settings can be tightened but not loosened, emergency unlock is off, " +
                            "and TeleZen's pages in Android Settings are blocked.",
                        onClick = { strictDialog = true },
                        trailing = { TextButton(onClick = { strictDialog = true }) { Text(if (strict) "Extend" else "Turn on") } },
                    )
                    SwitchRow(
                        title = "Uninstall protection",
                        subtitle = "Uses device admin so TeleZen can't be uninstalled without turning this off first.",
                        checked = adminActive,
                    ) { on ->
                        if (on) adminLauncher.launch(Permissions.addAdminIntent(ctx))
                        else if (strict) snack("Uninstall protection can't be turned off during strict mode.")
                        else Permissions.removeAdmin(ctx)
                    }
                    SettingRow(
                        title = if (settings.pinHash == null) "Set a PIN" else "PIN is set",
                        subtitle = "Ask for a PIN to open TeleZen.",
                        onClick = { pinDialog = if (settings.pinHash == null) PinDialogMode.SET else PinDialogMode.REMOVE },
                        trailing = {
                            if (settings.pinHash != null) TextButton(onClick = { pinDialog = PinDialogMode.REMOVE }) { Text("Remove") }
                        },
                    )
                }
            }
            item {
                SectionCard(title = "Detection & reliability") {
                    SettingRow(
                        title = "Detection rules",
                        subtitle = "Rules version ${app.rules.current.version}${if (app.rules.isOverride) " (imported)" else ""} · report problems",
                        onClick = { nav.navigate("rules") },
                    )
                    SettingRow(
                        title = "Permissions & battery",
                        subtitle = "Keep TeleZen running on your phone",
                        onClick = { nav.navigate("reliability") },
                    )
                }
            }
            item {
                SectionCard(title = "Backup") {
                    SettingRow(
                        title = "Export settings",
                        subtitle = "Save apps, limits, schedules and prompts to a file. The PIN isn't included.",
                        onClick = { exportLauncher.launch("telezen-settings.json") },
                    )
                    SettingRow(
                        title = "Import settings",
                        subtitle = "Replace your current settings with an exported file.",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                }
            }
            item {
                SectionCard(title = "Appearance") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ThemeMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = settings.theme == mode,
                                onClick = { update { it.copy(theme = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "About") {
                    SettingRow(
                        title = "TeleZen ${BuildConfig.VERSION_NAME}",
                        subtitle = "Everything runs on this phone. TeleZen has no internet permission and collects nothing.",
                    )
                    SettingRow(
                        title = "Run setup again",
                        onClick = { update { it.copy(onboardingDone = false) } },
                    )
                }
            }
        }
    }

    if (strictDialog) StrictDialog(onDismiss = { strictDialog = false }) { hours ->
        strictDialog = false
        update { it.copy(strictUntil = maxOf(it.strictUntil, System.currentTimeMillis() + hours * 3_600_000L)) }
    }
    pinDialog?.let { mode -> PinDialog(mode) { pinDialog = null } }
}

private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = { RadioButton(selected = selected, onClick = onClick) },
    )
}

@Composable
private fun StrictDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var choice by rememberSaveable { mutableStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Strict mode") },
        text = {
            Column {
                Text("For how long? You won't be able to turn this off early.", modifier = Modifier.padding(bottom = 8.dp))
                STRICT_DURATIONS.forEachIndexed { i, (label, _) ->
                    RadioRow(label, null, choice == i) { choice = i }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(STRICT_DURATIONS[choice].second) }) { Text("Start strict mode") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

enum class PinDialogMode { SET, REMOVE }

@Composable
private fun PinDialog(mode: PinDialogMode, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.tz
    val update = rememberUpdater()
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (mode == PinDialogMode.SET) "Set a PIN" else "Remove PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(first, if (mode == PinDialogMode.SET) "New PIN (4–8 digits)" else "Current PIN") { first = it }
                if (mode == PinDialogMode.SET) PinField(second, "Repeat PIN") { second = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = app.settings.current
                when (mode) {
                    PinDialogMode.SET -> when {
                        first.length !in 4..8 -> error = "Use 4 to 8 digits."
                        first != second -> error = "The PINs don't match."
                        else -> {
                            val salt = Pin.newSalt()
                            if (update { it.copy(pinSalt = salt, pinHash = Pin.hash(first, salt)) }) onDone()
                        }
                    }
                    PinDialogMode.REMOVE -> if (!Pin.verify(first, s.pinSalt, s.pinHash)) error = "Wrong PIN."
                    else if (update { it.copy(pinHash = null, pinSalt = null) }) onDone()
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

@Composable
fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(8)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
