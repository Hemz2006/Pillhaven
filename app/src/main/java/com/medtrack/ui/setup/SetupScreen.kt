package com.medtrack.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.medtrack.AppContainer
import com.medtrack.R
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh) {
        val now = readSetupStatus(context, c.health)
        status = now
        if (steps == null) steps = setupSteps(now)
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
            step == null -> AllSet(steps = all, status = s, onDone = onDone)
            else -> StepPage(
                step = step,
                number = index + 1,
                count = all.size,
                health = s.health,
                onAllow = {
                    when (step) {
                        SetupStep.NOTIFICATIONS -> ask.notifications()
                        SetupStep.EXACT_ALARMS -> ask.exactAlarms()
                        SetupStep.FULL_SCREEN -> ask.fullScreen()
                        SetupStep.BATTERY -> ask.battery()
                        SetupStep.MEALS -> ask.health(s.health)
                    }
                },
                onNext = { index++ },
            )
        }
    }
}

/** A screen: an optional header, content centred in the space above, and the main button pinned to the bottom. */
@Composable
private fun Page(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    header: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        header()
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            content = content,
        )
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(primary, style = MaterialTheme.typography.titleMedium)
        }
        if (secondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(secondary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) = Page("Get started", onStart) {
    val colors = MaterialTheme.medColors
    // The app's logo, on the off-white tile it has on the home screen.
    Box(
        Modifier.fillMaxWidth().heightIn(min = 200.dp).background(colors.paperRaised, LabelShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = "Pillhaven logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(150.dp).background(colors.paper, LabelShape),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Welcome to Pillhaven", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("A safe place for all your medicines.", style = MaterialTheme.typography.titleMedium, color = colors.inkMuted)
    }
    Text(
        "A few quick steps, so reminders ring on time even when the phone is locked or asleep. Each one says what it's for, and you can skip any of them.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Promise(Icons.Default.Notifications, "Reminders ring on time, even when the phone is locked")
        Promise(Icons.Default.Lock, "Your medicines stay on your phone")
        Promise(Icons.Default.Check, "Skip any step and allow it later in Settings")
    }
}

/** One line of what Pillhaven promises, with its icon on a small soft tile. */
@Composable
private fun Promise(icon: ImageVector, text: String) {
    val colors = MaterialTheme.medColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(colors.paperRaised, LabelShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun StepPage(
    step: SetupStep,
    number: Int,
    count: Int,
    health: HealthStatus,
    onAllow: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = MaterialTheme.medColors
    val primary = when {
        step != SetupStep.MEALS -> step.allowLabel
        health == HealthStatus.NOT_INSTALLED -> "Install Health Connect"
        health == HealthStatus.NEEDS_UPDATE || health == HealthStatus.NO_BACKGROUND_READ -> "Update Health Connect"
        else -> step.allowLabel
    }
    val skipLabel = if (step == SetupStep.MEALS) "Skip" else "Not now"
    Page(
        primary = primary,
        onPrimary = onAllow,
        secondary = skipLabel,
        onSecondary = onNext,
        header = { StepHeader(number, count, skipLabel, onNext) },
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 220.dp).background(colors.paperRaised, LabelShape).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) { StepPicture(step) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (step == SetupStep.MEALS) "Meal reminders" else step.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                if (step == SetupStep.MEALS) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Optional",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inkMuted,
                        modifier = Modifier.border(1.dp, colors.foil, LabelShape).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(step.body, style = MaterialTheme.typography.bodyLarge)
        }
        Row(
            Modifier.fillMaxWidth().background(colors.paperRaised, LabelShape).padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(step.reassurance, style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
        }
    }
}

/** A segmented progress bar with "Step N of M", and a quick way to move on. */
@Composable
private fun StepHeader(number: Int, count: Int, skipLabel: String, onSkip: () -> Unit) {
    val colors = MaterialTheme.medColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clearAndSetSemantics { contentDescription = "Step $number of $count" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.width((28 * count + 6 * (count - 1)).dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(count) { i ->
                    Box(Modifier.weight(1f).height(4.dp).background(if (i < number) colors.ink else colors.foil, CircleShape))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text("STEP $number OF $count", style = MaterialTheme.typography.labelLarge, color = colors.inkMuted)
        }
        TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text(skipLabel, style = MaterialTheme.typography.titleSmall) }
    }
}

/** A small picture of what the permission gives: the reminder itself, the alarm on time, the alarm screen… */
@Composable
private fun StepPicture(step: SetupStep) {
    val colors = MaterialTheme.medColors
    val t = MaterialTheme.typography
    @Composable fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
        Modifier.fillMaxWidth().background(colors.paper, LabelShape).border(1.dp, colors.foil, LabelShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
    Box(Modifier.clearAndSetSemantics { }) {
        when (step) {
            SetupStep.NOTIFICATIONS -> Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pillhaven · now", style = t.labelMedium, color = colors.inkMuted)
                }
                Text("Time for your medicine", style = t.titleMedium, fontWeight = FontWeight.Bold)
                Text("1 tablet · after breakfast", style = t.bodyMedium, color = colors.inkMuted)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("Taken", "Skip", "Snooze 10 min").forEach { Text(it, style = t.labelLarge, color = colors.taken) }
                }
            }
            SetupStep.EXACT_ALARMS -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("8:00 AM", style = t.displayMedium, fontWeight = FontWeight.Bold)
                Text("Rings at 8:00, not 8:07", style = t.bodyLarge, color = colors.inkMuted)
            }
            SetupStep.FULL_SCREEN -> Column(
                Modifier.width(170.dp).background(colors.ink, LabelShape).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Time for your medication", style = t.titleSmall, color = colors.paper, textAlign = TextAlign.Center)
                Text("Taken", style = t.labelLarge, color = colors.ink, modifier = Modifier.background(colors.paper, CircleShape).padding(horizontal = 22.dp, vertical = 8.dp))
                Text("Snooze · Skip", style = t.labelMedium, color = colors.paper)
            }
            SetupStep.BATTERY -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 120.dp, height = 56.dp).border(4.dp, colors.ink, LabelShape).padding(6.dp)) {
                        Box(Modifier.fillMaxSize().background(colors.taken, LabelShape))
                    }
                    Box(Modifier.size(width = 7.dp, height = 20.dp).background(colors.ink))
                }
                Text(
                    "Unrestricted",
                    style = t.labelLarge,
                    color = colors.taken,
                    modifier = Modifier.background(colors.paper, LabelShape).border(1.dp, colors.foil, LabelShape).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            SetupStep.MEALS -> Card {
                TimelineRow("Breakfast logged · 8:10 AM", null, dot = colors.inkMuted)
                Box(Modifier.padding(start = 3.dp).width(2.dp).height(18.dp).background(colors.foil))
                TimelineRow("Reminder · 8:40 AM", "30 min after breakfast", dot = colors.taken)
            }
        }
    }
}

@Composable
private fun TimelineRow(title: String, detail: String?, dot: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = if (detail == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                color = if (detail == null) MaterialTheme.medColors.inkMuted else MaterialTheme.medColors.ink)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.medColors.inkMuted) }
        }
    }
}

@Composable
private fun AllSet(steps: List<SetupStep>, status: SetupStatus, onDone: () -> Unit) = Page("Go to Today", onDone) {
    val colors = MaterialTheme.medColors
    val skipped = steps.filter { !it.isDone(status) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // A calm success mark: the check inside soft rings.
        Box(Modifier.size(112.dp).background(colors.paperRaised, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(84.dp).background(colors.foil, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.taken, modifier = Modifier.size(64.dp))
            }
        }
        Text("All set", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(
            if (skipped.isEmpty()) "Reminders will ring on time. Add your first medicine to get started."
            else "You skipped some steps. You can allow them any time in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
    if (steps.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().border(1.dp, colors.foil, LabelShape)) {
            steps.forEachIndexed { i, step ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.foil))
                val allowed = step.isDone(status)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp)
                        .clearAndSetSemantics { contentDescription = "${step.title}: ${if (allowed) "allowed" else "skipped"}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(step.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (allowed) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = colors.taken, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Allowed", style = MaterialTheme.typography.labelLarge, color = colors.taken)
                    } else {
                        // Amber is too light for text on white, so the tag is dark text on an amber outline.
                        Text(
                            "Skipped",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.ink,
                            modifier = Modifier.border(1.5.dp, colors.amber, LabelShape).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
