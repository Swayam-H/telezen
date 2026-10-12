package com.telezen.app.ui.screens

import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.telezen.app.BuildConfig
import com.telezen.app.TeleZenApp
import com.telezen.app.core.Apps
import com.telezen.app.core.Permissions
import com.telezen.app.core.Pin
import com.telezen.app.data.AppSettings
import com.telezen.app.data.DEFAULT_PROMPTS
import com.telezen.app.rules.TargetRule
import com.telezen.app.rules.Versions
import com.telezen.app.tz
import com.telezen.app.ui.components.AppIcon
import com.telezen.app.ui.components.Screen
import com.telezen.app.ui.components.SectionCard
import com.telezen.app.ui.components.SettingRow
import com.telezen.app.ui.components.rememberResumeTick
import com.telezen.app.ui.components.rememberSnack
import com.telezen.app.ui.components.rememberUpdater
import kotlinx.coroutines.delay

// ---- PIN lock ----

@Composable
fun PinLockScreen(settings: AppSettings, onUnlock: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var failures by rememberSaveable { mutableIntStateOf(0) }
    var lockedUntil by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val locked = now < lockedUntil

    fun attempt() {
        if (Pin.verify(pin, settings.pinSalt, settings.pinHash)) onUnlock()
        else {
            failures++
            pin = ""
            if (failures % 5 == 0) lockedUntil = System.currentTimeMillis() + 30_000
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 360.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("TeleZen is locked", style = MaterialTheme.typography.headlineSmall)
                PinField(pin, "PIN") { pin = it }
                if (failures > 0) {
                    Text(
                        if (locked) "Too many attempts. Try again in ${(lockedUntil - now) / 1000 + 1}s." else "Wrong PIN.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = ::attempt,
                    enabled = !locked && pin.length >= 4,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Unlock") }
            }
        }
    }
}

// ---- Prompts ----

@Composable
fun PromptsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val settings by ctx.tz.settings.state.collectAsState()
    val update = rememberUpdater()
    var text by rememberSaveable { mutableStateOf("") }

    Screen("Prompts", onBack = { nav.popBackStack() }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "One of these is picked at random for each pause and block screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(140) },
                        placeholder = { Text("Write your own") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        val t = text.trim()
                        if (t.isNotEmpty()) {
                            update { it.copy(prompts = it.prompts + t) }
                            text = ""
                        }
                    }) { Text("Add") }
                }
            }
            item {
                SectionCard {
                    settings.prompts.forEachIndexed { i, p ->
                        SettingRow(
                            title = p,
                            trailing = {
                                IconButton(onClick = {
                                    update { s -> s.copy(prompts = s.prompts.filterIndexed { j, _ -> j != i }) }
                                }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
                            },
                        )
                    }
                    if (settings.prompts.isEmpty()) SettingRow(title = "No prompts. The built-in ones will be used.")
                }
            }
            item {
                TextButton(onClick = { update { it.copy(prompts = DEFAULT_PROMPTS) } }) { Text("Restore the built-in prompts") }
            }
        }
    }
}

// ---- Detection rules ----

@Composable
fun RulesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val app = ctx.tz
    val rules by app.rules.rules.collectAsState()
    val snack = rememberSnack()
    val tick = rememberResumeTick()
    var reportFor by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }.getOrNull()
            if (text == null) snack("Couldn't read the file")
            else app.rules.import(text)
                .onSuccess { snack("Imported rules version ${it.version}") }
                .onFailure { snack("Invalid rules file: ${it.message}") }
        }
    }

    Screen("Detection rules", onBack = { nav.popBackStack() }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    SettingRow(
                        title = "Rules version ${rules.version}",
                        subtitle = (if (app.rules.isOverride) "Imported from a file" else "Built in") +
                            if (rules.updated.isNotBlank()) " · updated ${rules.updated}" else "",
                    )
                    Text(
                        "Apps change their screens often. If a feed stops being blocked after an app update, open that feed, " +
                            "come back here and tap Report not working. The report includes the screen's view ids so the rule can be fixed. " +
                            "Nothing is sent automatically. You choose where to share it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { reportFor = true }) { Text("Report not working") }
                        TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                            Text("Import rules")
                        }
                        if (app.rules.isOverride) {
                            TextButton(onClick = {
                                app.rules.reset()
                                snack("Using the built-in rules")
                            }) { Text("Reset") }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Status") {
                    rules.targets.forEach { t -> key(tick) { TargetStatusRow(app, t) } }
                }
            }
            item {
                SectionCard(title = "All blockable feeds") { TargetToggles(showExperimental = true) }
            }
        }
    }

    if (reportFor) {
        AlertDialog(
            onDismissRequest = { reportFor = false },
            title = { Text("What isn't being blocked?") },
            text = {
                Column {
                    rules.targets.forEach { t ->
                        SettingRow(title = t.name, onClick = {
                            reportFor = false
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "TeleZen: ${t.name} not blocked")
                                .putExtra(Intent.EXTRA_TEXT, buildReport(app, t))
                            ctx.startActivity(Intent.createChooser(send, "Share report"))
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reportFor = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TargetStatusRow(app: TeleZenApp, t: TargetRule) {
    val ctx = LocalContext.current
    val installed = t.packages.filter { Apps.isInstalled(ctx, it) }
    val lastMatch = app.runtime.lastMatch(t.id)
    val lines = buildList {
        if (installed.isEmpty()) add("Not installed")
        installed.forEach { pkg ->
            val v = Apps.versionName(ctx, pkg)
            val known = listOf(t.testedVersions[pkg], app.runtime.lastWorkingVersion(pkg))
            val warn = Versions.isNewerThanKnown(v, known)
            add("${Apps.label(ctx, pkg)} $v" + if (warn) " · newer than the last version where blocking was confirmed" else "")
        }
        add(
            if (lastMatch > 0) "Last blocked ${DateUtils.getRelativeTimeSpanString(lastMatch)}"
            else "Not detected yet",
        )
        if (t.experimental) add("Experimental rule")
    }
    SettingRow(
        title = t.name,
        subtitle = lines.joinToString("\n"),
        leading = { AppIcon(installed.firstOrNull() ?: t.packages.first(), 36.dp) },
    )
}

private fun buildReport(app: TeleZenApp, t: TargetRule): String = buildString {
    appendLine("TeleZen problem report")
    appendLine("Target: ${t.name} (${t.id})")
    appendLine("TeleZen ${BuildConfig.VERSION_NAME}, rules v${app.rules.current.version}${if (app.rules.isOverride) " (imported)" else ""}")
    appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
    for (pkg in t.packages.filter { Apps.isInstalled(app, it) }) {
        appendLine()
        appendLine("$pkg ${Apps.versionName(app, pkg)}")
        val snap = app.runtime.snapshot(pkg)
        if (snap == null) {
            appendLine("No screen captured yet. Open the feed that isn't blocked, then send the report again.")
        } else {
            appendLine("Screen captured ${DateUtils.getRelativeTimeSpanString(app.runtime.snapshotTime(pkg))}:")
            appendLine(snap)
        }
    }
}

// ---- Reliability ----

@Composable
fun ReliabilityScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val perms = rememberPermissionState()
    Screen("Permissions & battery", onBack = { nav.popBackStack() }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard(title = "Status") {
                    SettingRow(
                        title = "Blocking service",
                        subtitle = when {
                            !perms.accessibility -> "Disabled"
                            perms.accessibilityRunning -> "Running"
                            else -> "Enabled but not running. Turn it off and on again."
                        },
                    )
                    PermissionRows(perms)
                }
            }
            item {
                SectionCard(title = "Tips for your ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} phone") {
                    Text(
                        oemTips(Build.MANUFACTURER),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    OutlinedButton(
                        onClick = { Permissions.startSafely(ctx, Permissions.appDetailsIntent(ctx)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text("Open TeleZen app info") }
                }
            }
            item {
                Text(
                    "TeleZen starts again by itself after a restart, as long as its accessibility service stays on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

private fun oemTips(manufacturer: String): String = when (manufacturer.lowercase()) {
    "xiaomi", "redmi", "poco" ->
        "• App info > Autostart: on\n• App info > Battery saver: No restrictions\n• In Recents, long-press TeleZen and lock it\n" +
            "• If the accessibility service keeps turning off, turn it on again after each MIUI/HyperOS update"
    "samsung" ->
        "• Settings > Battery > Background usage limits > Never sleeping apps: add TeleZen\n• App info > Battery: Unrestricted"
    "oneplus", "oppo", "realme" ->
        "• App info > Battery usage: Allow background activity and Allow auto launch\n• In Recents, lock TeleZen"
    "huawei", "honor" ->
        "• Battery > App launch > TeleZen: Manage manually, turn everything on"
    "vivo", "iqoo" ->
        "• Battery > Background power consumption management > TeleZen: Allow\n• i Manager > Autostart: on"
    else ->
        "• Allow TeleZen to ignore battery optimization (above)\n• Don't swipe TeleZen away in a task killer or cleaner app"
}
