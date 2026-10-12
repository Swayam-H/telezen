package com.telezen.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.telezen.app.core.Permissions
import com.telezen.app.ui.components.SettingRow
import com.telezen.app.ui.components.rememberResumeTick

data class PermissionState(
    val accessibility: Boolean,
    val accessibilityRunning: Boolean,
    val usage: Boolean,
    val notifications: Boolean,
    val battery: Boolean,
)

@Composable
fun rememberPermissionState(): PermissionState {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    return remember(tick) {
        PermissionState(
            accessibility = Permissions.accessibilityEnabled(ctx),
            accessibilityRunning = Permissions.accessibilityRunning(),
            usage = Permissions.usageAccess(ctx),
            notifications = Permissions.notifications(ctx),
            battery = Permissions.batteryExempt(ctx),
        )
    }
}

@Composable
private fun StatusIcon(granted: Boolean, required: Boolean) {
    when {
        granted -> Icon(Icons.Filled.CheckCircle, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary)
        required -> Icon(Icons.Outlined.ErrorOutline, contentDescription = "Needed", tint = MaterialTheme.colorScheme.error)
        else -> Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = "Optional", tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun PermissionRows(state: PermissionState) {
    val ctx = LocalContext.current
    var disclosure by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    SettingRow(
        title = "Accessibility service",
        subtitle = "Required. Detects Reels, Shorts and which app is open.",
        leading = { StatusIcon(state.accessibility, required = true) },
        trailing = {
            if (!state.accessibility) FilledTonalButton(onClick = { disclosure = true }) { Text("Allow") }
        },
    )
    SettingRow(
        title = "Usage access",
        subtitle = "Needed for time limits, stats and goals.",
        leading = { StatusIcon(state.usage, required = true) },
        trailing = {
            if (!state.usage) {
                FilledTonalButton(onClick = {
                    Permissions.startSafely(ctx, Permissions.usageAccessIntent(), Permissions.appDetailsIntent(ctx))
                }) { Text("Allow") }
            }
        },
    )
    if (Build.VERSION.SDK_INT >= 33) {
        SettingRow(
            title = "Notifications",
            subtitle = "For your end-of-day summary.",
            leading = { StatusIcon(state.notifications, required = false) },
            trailing = {
                if (!state.notifications) {
                    FilledTonalButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
                }
            },
        )
    }
    SettingRow(
        title = "Ignore battery optimization",
        subtitle = "Recommended. Stops Android from killing the blocker.",
        leading = { StatusIcon(state.battery, required = false) },
        trailing = {
            if (!state.battery) {
                FilledTonalButton(onClick = {
                    Permissions.startSafely(ctx, Permissions.batteryIntent(ctx), Permissions.appDetailsIntent(ctx))
                }) { Text("Allow") }
            }
        },
    )

    if (disclosure) {
        AlertDialog(
            onDismissRequest = { disclosure = false },
            title = { Text("Accessibility permission") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "TeleZen uses the Accessibility API to read which app and screen is in front, so it can " +
                            "block Reels and Shorts, show pause screens and enforce your limits and schedules.",
                    )
                    Text("This happens only on your phone. TeleZen has no internet permission and never collects or shares what's on your screen.")
                    Text(
                        "On the next screen, open Installed apps (or Downloaded apps) > TeleZen and turn it on. " +
                            "If the switch is greyed out, go back, tap App info below, open the ⋮ menu and choose Allow restricted settings.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    disclosure = false
                    Permissions.startSafely(ctx, Permissions.accessibilitySettingsIntent())
                }) { Text("Agree and open settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    disclosure = false
                    Permissions.startSafely(ctx, Permissions.appDetailsIntent(ctx))
                }) { Text("App info") }
            },
        )
    }
}
