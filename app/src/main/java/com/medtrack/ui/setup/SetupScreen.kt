package com.medtrack.ui.setup

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.medtrack.AppContainer
import com.medtrack.health.HealthStatus
import com.medtrack.ui.settings.SetupStatus
import com.medtrack.ui.settings.readSetupStatus
import com.medtrack.ui.settings.rememberPermissionRequests
import com.medtrack.ui.theme.LabelShape
import com.medtrack.ui.theme.medColors
import kotlinx.coroutines.launch

/**
 * First run: a welcome, then one screen per permission explaining what it's for, then "All set".
 * "Not now" always moves on; a permission allowed in the system screen moves on by itself.
 */
@Composable
fun SetupScreen(c: AppContainer, onDone: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<SetupStatus?>(null) }
    // Fixed once at the start, so allowing a permission doesn't renumber the steps.
    var steps by remember { mutableStateOf<List<SetupStep>?>(null) }
    // -1 is the welcome screen; steps.size is "All set".
    var index by remember { mutableIntStateOf(-1) }
    var openedAutoStart by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh) {
        val now = readSetupStatus(context, c.health)
        status = now
        if (steps == null) steps = setupSteps(now, Build.MANUFACTURER)
        c.appScope.launch { c.refreshReminders() } // e.g. exact alarms were just allowed
    }
    val ask = rememberPermissionRequests(c.health) { refresh++ }

    val s = status ?: return
    val all = steps ?: return
    val step = all.getOrNull(index)
    LaunchedEffect(step, s) { if (step != null && step.isDone(s)) index++ }
    BackHandler(enabled = index >= 0) { index-- }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.medColors.paper) {
        when {
            index < 0 -> Welcome(onStart = { index = 0 })
            step == null -> AllSet(skipped = all.filter { !it.isDone(s) && it != SetupStep.AUTO_START }, onDone = onDone)
            else -> StepPage(
                step = step,
                number = index + 1,
                count = all.size,
                health = s.health,
                openedAutoStart = openedAutoStart,
                onAllow = {
                    when (step) {
                        SetupStep.NOTIFICATIONS -> ask.notifications()
                        SetupStep.EXACT_ALARMS -> ask.exactAlarms()
                        SetupStep.FULL_SCREEN -> ask.fullScreen()
                        SetupStep.BATTERY -> ask.battery()
                        SetupStep.AUTO_START -> { ask.autoStart(); openedAutoStart = true }
                        SetupStep.MEALS -> ask.health(s.health)
                    }
                },
                onNext = { index++ },
            )
        }
    }
}

@Composable
private fun Page(primary: String, onPrimary: () -> Unit, secondary: String? = null, onSecondary: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(primary, style = MaterialTheme.typography.titleMedium) }
        if (secondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(secondary, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) = Page("Get started", onStart) {
    Spacer(Modifier.heightIn(min = 32.dp))
    Text("Welcome to Pillhaven", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text("A safe place for all your medicines.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.medColors.inkMuted)
    Text(
        "A few quick steps, so reminders ring on time even when the phone is locked or asleep. Each one says what it's for, and you can skip any of them.",
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun StepPage(
    step: SetupStep,
    number: Int,
    count: Int,
    health: HealthStatus,
    openedAutoStart: Boolean,
    onAllow: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = MaterialTheme.medColors
    val (primary, secondary) = when (step) {
        // Android can't tell whether auto-start was allowed, so the person says when they're done.
        SetupStep.AUTO_START -> if (openedAutoStart) "Done" to "Open phone settings again" else step.allowLabel to "Not now"
        SetupStep.MEALS -> when (health) {
            HealthStatus.NOT_INSTALLED -> "Install Health Connect"
            HealthStatus.NEEDS_UPDATE, HealthStatus.NO_BACKGROUND_READ -> "Update Health Connect"
            else -> step.allowLabel
        } to "Skip"
        else -> step.allowLabel to "Not now"
    }
    val autoStartDone = step == SetupStep.AUTO_START && openedAutoStart
    Page(
        primary = primary,
        onPrimary = if (autoStartDone) onNext else onAllow,
        secondary = secondary,
        onSecondary = if (autoStartDone) onAllow else onNext,
    ) {
        StepDots(number, count)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 160.dp).background(colors.paperRaised, LabelShape).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) { StepPicture(step) }
        Text(step.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(step.body, style = MaterialTheme.typography.bodyLarge)
        Row(
            Modifier.fillMaxWidth().background(colors.paperRaised, LabelShape).padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(step.reassurance, style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
        }
    }
}

@Composable
private fun StepDots(number: Int, count: Int) {
    val colors = MaterialTheme.medColors
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Step $number of $count" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(count) { i ->
                Box(Modifier.size(width = if (i == number - 1) 20.dp else 8.dp, height = 8.dp).background(if (i < number) colors.ink else colors.foil, CircleShape))
            }
        }
        Text("STEP $number OF $count", style = MaterialTheme.typography.labelMedium, color = colors.inkMuted)
    }
}

/** A small picture of what the permission gives: the reminder itself, the alarm on time, the alarm screen… */
@Composable
private fun StepPicture(step: SetupStep) {
    val colors = MaterialTheme.medColors
    val t = MaterialTheme.typography
    @Composable fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
        Modifier.background(colors.paper, LabelShape).border(1.dp, colors.foil, LabelShape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
    Box(Modifier.clearAndSetSemantics { }) {
        when (step) {
            SetupStep.NOTIFICATIONS -> Card {
                Text("Pillhaven · now", style = t.labelMedium, color = colors.inkMuted)
                Text("Time for your medicine", style = t.titleSmall)
                Text("1 tablet · after breakfast", style = t.bodyMedium, color = colors.inkMuted)
                Text("Taken    Skip    Snooze 10 min", style = t.labelLarge, color = colors.taken)
            }
            SetupStep.EXACT_ALARMS -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("8:00 AM", style = t.displayMedium, fontWeight = FontWeight.Bold)
                Text("Rings at 8:00, not 8:07", style = t.bodyLarge, color = colors.inkMuted)
            }
            SetupStep.FULL_SCREEN -> Column(
                Modifier.width(150.dp).background(colors.ink, LabelShape).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Time for your medication", style = t.titleSmall, color = colors.paper)
                Text("Taken", style = t.labelLarge, color = colors.ink, modifier = Modifier.background(colors.paper, CircleShape).padding(horizontal = 18.dp, vertical = 6.dp))
                Text("Snooze · Skip", style = t.labelMedium, color = colors.paper)
            }
            SetupStep.BATTERY -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 96.dp, height = 44.dp).border(3.dp, colors.ink, LabelShape).padding(5.dp)) {
                        Box(Modifier.fillMaxSize().background(colors.taken, LabelShape))
                    }
                    Box(Modifier.size(width = 6.dp, height = 16.dp).background(colors.ink))
                }
                Text("Unrestricted", style = t.titleMedium, color = colors.taken)
            }
            SetupStep.AUTO_START -> Card {
                Text("Settings › Battery", style = t.bodyMedium, color = colors.inkMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow auto-start", style = t.titleSmall, modifier = Modifier.padding(end = 12.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.taken)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow background activity", style = t.titleSmall, modifier = Modifier.padding(end = 12.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.taken)
                }
            }
            SetupStep.MEALS -> Card {
                Text("Breakfast logged · 8:10 AM", style = t.bodyMedium, color = colors.inkMuted)
                Text("Reminder · 8:40 AM", style = t.titleSmall)
                Text("30 min after breakfast", style = t.bodyMedium, color = colors.inkMuted)
            }
        }
    }
}

@Composable
private fun AllSet(skipped: List<SetupStep>, onDone: () -> Unit) = Page("Go to Today", onDone) {
    Spacer(Modifier.heightIn(min = 32.dp))
    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.medColors.taken, modifier = Modifier.size(64.dp))
    Text("All set", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    if (skipped.isEmpty()) {
        Text("Reminders will ring on time. Add your first medicine to get started.", style = MaterialTheme.typography.bodyLarge)
    } else {
        Text(
            "You skipped: ${skipped.joinToString(", ") { it.title }}. You can allow these any time in Settings.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
