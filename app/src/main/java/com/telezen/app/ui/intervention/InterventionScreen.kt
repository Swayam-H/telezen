package com.telezen.app.ui.intervention

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.telezen.app.core.plural
import com.telezen.app.core.Apps
import com.telezen.app.core.Day
import com.telezen.app.core.ScheduleLogic
import com.telezen.app.data.AppSettings
import com.telezen.app.data.DEFAULT_PROMPTS
import com.telezen.app.data.DEFAULT_SCHEDULE_PAUSE_SECONDS
import com.telezen.app.data.db.EventType
import com.telezen.app.tz
import com.telezen.app.ui.components.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val REASONS = listOf("Bored", "Specific task", "Reply to a message", "Habit", "Taking a break")
private val SESSION_CHOICES = listOf(5, 10, 15, 30)
private const val UNLOCK_PHRASE = "I choose to spend more time here"
private const val UNLOCK_WAIT_SECONDS = 20
private const val UNLOCK_MINUTES = 10

@Composable
fun InterventionScreen(r: InterventionRequest, settings: AppSettings, actions: InterventionActions) {
    val ctx = LocalContext.current
    val label = remember(r.pkg) { Apps.label(ctx, r.pkg) }
    val prompt = remember(r.nonce) { settings.prompts.ifEmpty { DEFAULT_PROMPTS }.random() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIcon(r.pkg, 64.dp)
                when (r.mode) {
                    Mode.PAUSE -> PauseContent(r, label, prompt, settings, actions)
                    Mode.SHORT_BLOCK -> ShortBlockContent(r, label, prompt, actions)
                    Mode.LIMIT -> LimitContent(r, label, settings, actions)
                    Mode.SCHEDULE_BLOCK -> ScheduleContent(r, label, settings, actions)
                    Mode.SESSION_END -> SessionEndContent(r, label, prompt, actions)
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

@Composable
private fun Body(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseContent(
    r: InterventionRequest,
    label: String,
    prompt: String,
    settings: AppSettings,
    actions: InterventionActions,
) {
    val ctx = LocalContext.current
    val guarded = settings.guarded(r.pkg)
    val total = guarded?.delaySeconds ?: DEFAULT_SCHEDULE_PAUSE_SECONDS
    var remaining by rememberSaveable { mutableIntStateOf(total) }
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var minutes by rememberSaveable { mutableIntStateOf(10) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }

    val opens by produceState(-1, r.pkg) {
        value = ctx.tz.db.events().count(Day.today(), r.pkg, EventType.SESSION_START)
    }
    val usedMinutes by produceState(-1, r.pkg) {
        value = withContext(Dispatchers.IO) { ctx.tz.usage.minutesToday(r.pkg) }
    }
    val minutesLeft = guarded?.maxMinutesPerDay?.takeIf { it > 0 }?.let { (it - usedMinutes.coerceAtLeast(0)).coerceAtLeast(1) }
    val sessionMinutes = minutesLeft?.let { minOf(minutes, it) } ?: minutes
    LaunchedEffect(minutesLeft) {
        if (minutesLeft != null && minutes > minutesLeft) {
            minutes = SESSION_CHOICES.lastOrNull { it <= minutesLeft } ?: SESSION_CHOICES.first()
        }
    }

    Title("Opening $label")
    Body(prompt)

    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        if (settings.breathingExercise) BreathingCircle() else CountdownRing(remaining, total)
        Text(
            if (remaining > 0) "$remaining" else "Ready",
            style = if (remaining > 0) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleLarge,
        )
    }

    if (opens >= 0) {
        val limitText = guarded?.maxOpensPerDay?.takeIf { it > 0 }?.let { " of $it allowed" } ?: ""
        val timeText = if (usedMinutes >= 0) " · ${Day.formatDuration(usedMinutes * 60_000L)} today" else ""
        Text(
            "Opened $opens time${if (opens == 1) "" else "s"} today$limitText$timeText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }

    if (guarded?.askReason == true) {
        Text("Why are you opening it?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            REASONS.forEach { option ->
                FilterChip(
                    selected = reason == option,
                    onClick = { reason = if (reason == option) null else option },
                    label = { Text(option) },
                )
            }
        }
    }

    AnimatedVisibility(remaining == 0) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("For how long?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                SESSION_CHOICES.forEach { m ->
                    val allowed = minutesLeft == null || m <= minutesLeft || m == SESSION_CHOICES.first()
                    FilterChip(
                        selected = minutes == m,
                        enabled = allowed,
                        onClick = { minutes = m },
                        label = { Text("$m min") },
                    )
                }
            }
            if (minutesLeft != null) {
                Text(
                    "$minutesLeft min left of today's limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Button(onClick = { actions.neverMind(r.pkg) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text("Never mind")
    }
    OutlinedButton(
        onClick = { actions.continueSession(r.pkg, sessionMinutes, reason) },
        enabled = remaining == 0,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(if (remaining == 0) "Continue to $label for $sessionMinutes min" else "Continue in ${remaining}s")
    }
}

@Composable
private fun CountdownRing(remaining: Int, total: Int) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val fraction = if (total <= 0) 0f else remaining / total.toFloat()
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 10.dp.toPx()
        val inset = stroke / 2
        val size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), size, style = Stroke(stroke))
        drawArc(progressColor, -90f, 360f * fraction, false, Offset(inset, inset), size, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun BreathingCircle() {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val color = MaterialTheme.colorScheme.primaryContainer
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color, radius = size.minDimension / 2 * scale)
        }
        Text(
            if (scale > 0.78f) "breathe out" else "breathe in",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun ShortBlockContent(r: InterventionRequest, label: String, prompt: String, actions: InterventionActions) {
    val ctx = LocalContext.current
    val target = remember(r.targetId) { r.targetId?.let { ctx.tz.rules.current.target(it) } }
    val name = target?.name ?: "Short-form video"
    if (target?.wholeApp == true) {
        Title("$name is blocked")
        Body(prompt)
        Button(onClick = { actions.dismissBlock(r) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("OK") }
    } else {
        Title("$name blocked")
        Body("The rest of $label still works. $prompt")
        Button(onClick = { actions.dismissBlock(r) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Go back to $label")
        }
        TextButton(onClick = { actions.goHome() }) { Text("Close $label") }
    }
}

@Composable
private fun LimitContent(r: InterventionRequest, label: String, settings: AppSettings, actions: InterventionActions) {
    Title("Daily limit reached")
    Body(
        if (r.limitKind == "OPENS") "You've opened $label ${plural(r.limit, "time")} today, which is your limit. It resets at midnight."
        else "You've used $label for ${plural(r.limit, "minute")} today, which is your limit. It resets at midnight.",
    )
    Button(onClick = { actions.goHome() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Go home") }
    EmergencyUnlock(r.pkg, settings, actions)
}

@Composable
private fun ScheduleContent(r: InterventionRequest, label: String, settings: AppSettings, actions: InterventionActions) {
    val schedule = settings.schedules.firstOrNull { it.id == r.scheduleId }
    Title("${schedule?.name ?: "Focus time"} is on")
    Body(
        if (schedule != null && schedule.startMinute != schedule.endMinute) {
            "$label is blocked until ${ScheduleLogic.formatMinute(schedule.endMinute)}."
        } else "$label is blocked by your schedule.",
    )
    Button(onClick = { actions.goHome() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Go home") }
    EmergencyUnlock(r.pkg, settings, actions)
}

@Composable
private fun SessionEndContent(r: InterventionRequest, label: String, prompt: String, actions: InterventionActions) {
    Title("Time's up")
    Body("Your session on $label has ended. $prompt")
    Button(onClick = { actions.goHome() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Close $label") }
    TextButton(onClick = { actions.extend(r) }) { Text("I need more time") }
}

@Composable
private fun EmergencyUnlock(pkg: String, settings: AppSettings, actions: InterventionActions) {
    if (settings.strictActive()) {
        Text(
            "Strict mode is on, so emergency unlock isn't available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    if (!open) {
        TextButton(onClick = { open = true }) { Text("Emergency unlock") }
        return
    }
    var typed by rememberSaveable { mutableStateOf("") }
    var wait by rememberSaveable { mutableIntStateOf(UNLOCK_WAIT_SECONDS) }
    LaunchedEffect(Unit) {
        while (wait > 0) {
            delay(1_000)
            wait--
        }
    }
    val matches = typed.trim().equals(UNLOCK_PHRASE, ignoreCase = true)
    Text("Type this to unlock for $UNLOCK_MINUTES minutes:", style = MaterialTheme.typography.bodyMedium)
    Text("\"$UNLOCK_PHRASE\"", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { actions.emergencyUnlock(pkg, UNLOCK_MINUTES) },
        enabled = matches && wait == 0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (wait > 0) "Unlock available in ${wait}s" else "Unlock for $UNLOCK_MINUTES minutes")
    }
}
