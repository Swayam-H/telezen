package com.telezen.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.telezen.app.core.LaptopSync
import com.telezen.app.tz
import com.telezen.app.ui.components.SectionCard
import com.telezen.app.ui.components.SettingRow
import com.telezen.app.ui.components.rememberSnack
import kotlinx.coroutines.launch

@Composable
fun LaptopSyncSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = rememberSnack()
    var pairing by remember { mutableStateOf(LaptopSync.pairing(ctx)) }
    var status by remember { mutableStateOf(LaptopSync.status(ctx)) }
    var syncing by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }

    fun syncNow() {
        if (syncing) return
        syncing = true
        scope.launch {
            LaptopSync.push(ctx.tz)
            status = LaptopSync.status(ctx)
            syncing = false
        }
    }

    val current = pairing
    SectionCard(title = "Sync to laptop") {
        SettingRow(
            title = if (current == null) "Pair with your laptop" else "Paired with ${current.hostPort}",
            subtitle = "Run the WatchZen helper on the laptop and paste the code it shows. Both must be on the same Wi-Fi.",
            onClick = { code = ""; dialog = true },
        )
        if (current != null) {
            SettingRow(
                title = if (syncing) "Syncing…" else "Sync now",
                subtitle = status ?: "Stats are also sent about once an hour.",
                onClick = { syncNow() },
            )
            SettingRow(
                title = "Unpair",
                onClick = { LaptopSync.unpair(ctx); pairing = null; status = null },
            )
        }
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Pairing code") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    label = { Text("192.168.1.5:4747/…") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (LaptopSync.pair(ctx, code)) {
                        pairing = LaptopSync.pairing(ctx)
                        status = null
                        dialog = false
                        syncNow()
                    } else {
                        snack("That isn't a pairing code. It looks like 192.168.1.5:4747/AbC…")
                    }
                }) { Text("Pair") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
        )
    }
}
