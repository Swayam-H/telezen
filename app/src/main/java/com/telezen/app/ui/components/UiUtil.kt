package com.telezen.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.telezen.app.data.AppSettings
import com.telezen.app.data.UpdateResult
import com.telezen.app.tz
import kotlinx.coroutines.launch

val LocalSnack = staticCompositionLocalOf { SnackbarHostState() }

/** Applies a settings change, showing the strict-mode reason in a snackbar if it's rejected. */
@Composable
fun rememberUpdater(): ((AppSettings) -> AppSettings) -> Boolean {
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    return remember {
        { transform ->
            when (val r = ctx.tz.settings.update(transform)) {
                UpdateResult.Ok -> true
                is UpdateResult.Rejected -> {
                    scope.launch { snack.showSnackbar(r.reason) }
                    false
                }
            }
        }
    }
}

@Composable
fun rememberSnack(): (String) -> Unit {
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    return remember { { msg -> scope.launch { snack.showSnackbar(msg) } } }
}

/** Increments every time the screen resumes, so permission checks re-run after visiting Settings. */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (onBack != null) Modifier.navigationBarsPadding().imePadding() else Modifier),
    ) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        content()
    }
}
