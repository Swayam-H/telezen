package com.telezen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.telezen.app.core.Apps
import com.telezen.app.tz
import com.telezen.app.ui.components.AppIcon
import com.telezen.app.ui.components.SectionCard
import com.telezen.app.ui.components.SwitchRow
import com.telezen.app.ui.components.rememberUpdater

private const val STEPS = 4

@Composable
fun OnboardingScreen() {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val perms = rememberPermissionState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                LinearProgressIndicator(
                    progress = { (step + 1) / STEPS.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (step) {
                        0 -> Welcome()
                        1 -> Privacy()
                        2 -> {
                            Text("Permissions", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "TeleZen needs these to work. Each button takes you to the right Android setting. Come back here when you're done.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SectionCard { PermissionRows(perms) }
                        }
                        else -> {
                            Text("What should TeleZen block?", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "The rest of each app keeps working. You can change this any time, and add pause screens, limits and schedules later.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SectionCard { TargetToggles(showExperimental = false) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
                    Spacer(Modifier.weight(1f))
                    val last = step == STEPS - 1
                    Button(
                        onClick = {
                            if (last) ctx.tz.settings.update { it.copy(onboardingDone = true) } else step++
                        },
                        enabled = step != 2 || perms.accessibility,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            when {
                                last -> "Start"
                                step == 2 && !perms.accessibility -> "Allow accessibility to continue"
                                else -> "Next"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    Text("TeleZen", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
    Text("Keep the apps. Lose the endless feeds.", style = MaterialTheme.typography.headlineSmall)
    Feature(Icons.Outlined.Block, "Block Reels and Shorts", "Instagram and YouTube keep working: messages, search, profiles and long videos. Only the short-form feeds are blocked.")
    Feature(Icons.Outlined.PauseCircle, "Pause before you scroll", "A short pause before distracting apps, with limits and focus schedules if you want them.")
    Feature(Icons.Outlined.Lock, "Hard to undo on a whim", "Strict mode locks your settings for a while, so a weak moment can't undo them.")
}

@Composable
private fun Privacy() {
    Text("How it works", style = MaterialTheme.typography.headlineMedium)
    Text(
        "TeleZen uses Android's accessibility service to see which app and screen is open. When it sees a Reels or Shorts screen, it takes you back and shows a short message.",
    )
    SectionCard(title = "Your privacy") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("• Everything runs on your phone.")
            Text("• TeleZen has no internet permission, so it can't send anything anywhere.")
            Text("• It doesn't record your screen, messages or what you type.")
            Text("• Your stats stay in TeleZen's private storage on this device.")
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TargetToggles(showExperimental: Boolean) {
    val ctx = LocalContext.current
    val settings by ctx.tz.settings.state.collectAsState()
    val rules by ctx.tz.rules.rules.collectAsState()
    val update = rememberUpdater()
    rules.targets.filter { showExperimental || !it.experimental }.forEach { t ->
        val installedPkg = t.packages.firstOrNull { Apps.isInstalled(ctx, it) }
        SwitchRow(
            title = t.name + if (t.experimental) " (experimental)" else "",
            subtitle = t.description + if (installedPkg == null) " Not installed." else "",
            checked = t.id in settings.enabledTargets,
            leading = { AppIcon(installedPkg ?: t.packages.first(), 36.dp) },
        ) { on ->
            update { s -> s.copy(enabledTargets = if (on) s.enabledTargets + t.id else s.enabledTargets - t.id) }
        }
    }
}
