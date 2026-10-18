package com.medtrack.ui.settings

import androidx.compose.ui.graphics.luminance
import com.medtrack.data.AlarmTextColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import com.medtrack.data.AlarmColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.OutlinedIconButton
import com.medtrack.ui.common.islandBottom
import com.medtrack.data.UiSettings
import com.medtrack.data.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.medtrack.AppContainer
import com.medtrack.health.HealthStatus
import com.medtrack.reminders.DoseNotifier
import com.medtrack.ui.common.ScreenScaffold
import com.medtrack.ui.theme.LabelShape
import com.medtrack.ui.theme.medColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(c: AppContainer, isSetup: Boolean, onSetupDone: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<SetupStatus?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh) {
        status = readSetupStatus(context, c.health)
        c.appScope.launch { c.refreshReminders() } // e.g. exact alarms were just allowed
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Permanently denied: the system shows no dialog, so send the user to the settings page.
        if (!granted) context.open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        refresh++
    }
    val healthPermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh++ }

    val ui by c.uiSettings.collectAsStateWithLifecycle()
    ScreenScaffold(title = if (isSetup) "Set up reminders" else "Settings", isTab = true) { padding ->
        val s = status ?: return@ScreenScaffold
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            if (isSetup) {
                Text(
                    "Pillhaven needs a few permissions so reminders ring on time, even when your phone is locked or asleep.",
                    Modifier.padding(horizontal = 16.dp),
                )
            }
            Row("Notifications", s.notifications) {
                if (channelBlocked(context)) {
                    context.open(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, DoseNotifier.CHANNEL_ID),
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            Row("Exact alarms", s.exactAlarms) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, context.packageUri()))
                }
            }
            Row("Full-screen alarms", s.fullScreen) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    context.open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, context.packageUri()))
                }
            }
            Row("Unrestricted battery", s.batteryUnrestricted) {
                context.open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, context.packageUri()))
            }
            Text(
                "On Xiaomi, Oppo, Vivo and Samsung phones, also allow Pillhaven to auto-start and run in the background in the phone's battery settings. After the phone restarts, reminders resume once you unlock it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.medColors.inkMuted,
                modifier = Modifier.padding(16.dp),
            )

            HorizontalDivider(color = MaterialTheme.medColors.foil)
            Text("Meal reminders (optional)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            val (healthText, healthAction) = when (s.health) {
                HealthStatus.NOT_INSTALLED -> "Health Connect isn't installed." to ("Install" to { context.openHealthConnectStore() })
                HealthStatus.NEEDS_UPDATE -> "Health Connect needs an update." to ("Update" to { context.openHealthConnectStore() })
                HealthStatus.NO_BACKGROUND_READ ->
                    "This Health Connect version can't read meals in the background. Update it." to ("Update" to { context.openHealthConnectStore() })
                HealthStatus.NOT_CONNECTED -> "Not connected." to ("Connect" to { healthPermissions.launch(c.health.permissions) })
                HealthStatus.CONNECTED -> "Connected. Pillhaven reads the meals you log." to null
            }
            ListItem(
                headlineContent = { Text("Health Connect", style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(healthText) },
                trailingContent = { healthAction?.let { (label, action) -> TextButton(onClick = action) { Text(label) } } },
            )

            HorizontalDivider(color = MaterialTheme.medColors.foil)
            Appearance(ui.theme) { mode -> c.updateUiSettings { it.copy(theme = mode) } }

            HorizontalDivider(color = MaterialTheme.medColors.foil)
            Text("Accessibility", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            SizeSetting("Text size", UiSettings.TEXT_SCALES, ui.textScale) { v -> c.updateUiSettings { it.copy(textScale = v) } }
            SizeSetting("Buttons and icons", UiSettings.SIZE_SCALES, ui.sizeScale) { v -> c.updateUiSettings { it.copy(sizeScale = v) } }
            AlarmColorSetting(ui.alarmColor) { color -> c.updateUiSettings { it.copy(alarmColor = color) } }
            AlarmTextColorSetting(ui) { text -> c.updateUiSettings { it.copy(alarmTextColor = text) } }
            if (ui.textScale != 1f || ui.sizeScale != 1f) {
                TextButton(onClick = { c.updateUiSettings { it.copy(textScale = 1f, sizeScale = 1f) } }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Reset sizes")
                }
            }

            if (isSetup) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { c.prefs.setupDone = true; onSetupDone() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
            Spacer(Modifier.height(islandBottom()))
        }
    }
}

@Composable
private fun Row(title: String, ok: Boolean, onFix: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                StatusTag(ok)
                if (!ok) Text("Needed for reliable reminders")
            }
        },
        trailingContent = { if (!ok) TextButton(onClick = onFix, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow") } },
    )
}

/**
 * "Allowed" in the taken colour, or "Needs attention" on an amber tag. Amber is too light for text on paper,
 * so the attention tag is dark text on an amber block instead.
 */
@Composable
private fun StatusTag(ok: Boolean) {
    val colors = MaterialTheme.medColors
    if (ok) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.taken, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Allowed", style = MaterialTheme.typography.labelLarge, color = colors.taken)
        }
    } else {
        Text(
            "Needs attention",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onAmber,
            modifier = Modifier.background(colors.amber, LabelShape).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun Context.packageUri(): Uri = Uri.parse("package:$packageName")

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()))
    }
}

private fun Context.openHealthConnectStore() =
    open(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))

/** Light (green and white), dark, or following the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Appearance(current: ThemeMode, onChange: (ThemeMode) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = mode == current,
                    onClick = { onChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                ) { Text(mode.label) }
            }
        }
        Text(
            "Light is green and white. System follows your phone's dark mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.medColors.inkMuted,
        )
    }
}

private val SIZE_NAMES = listOf("Smaller", "Default", "Large", "Larger", "Largest")

/**
 * A five-step size control: − and + around the current size's name. Buttons rather than a slider, so scrolling
 * the page can't nudge the size by accident (a slider jumps to wherever a finger lands).
 */
@Composable
private fun SizeSetting(label: String, steps: List<Float>, current: Float, onChange: (Float) -> Unit) {
    val index = steps.indexOfFirst { it == current }.takeIf { it >= 0 } ?: steps.indexOf(1f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedIconButton(onClick = { onChange(steps[index - 1]) }, enabled = index > 0) {
            Text("−", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "Smaller $label" })
        }
        Text(
            SIZE_NAMES[index],
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(84.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        OutlinedIconButton(onClick = { onChange(steps[index + 1]) }, enabled = index < steps.lastIndex) {
            Text("+", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "Larger $label" })
        }
    }
}

/** The full-screen alarm's colour: a row of swatches, each showing the alarm's own text colour on it. */
@Composable
private fun AlarmColorSetting(current: AlarmColor, onChange: (AlarmColor) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alarm screen colour", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(current.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.medColors.inkMuted)
        }
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlarmColor.entries.forEach { color ->
                val chosen = color == current
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(color.background))
                        .border(if (chosen) 3.dp else 1.5.dp, if (chosen) MaterialTheme.medColors.ink else MaterialTheme.medColors.inkMuted, CircleShape)
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { onChange(color) })
                        .semantics { contentDescription = "${color.label} alarm" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) Icon(Icons.Default.Check, null, tint = Color(color.content), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * The alarm's text colour: each swatch shows "Aa" in that colour on the chosen alarm colour, so the choice is
 * previewed. A pairing below 3:1 contrast gets a warning, since the alarm is read at a glance, often half asleep.
 */
@Composable
private fun AlarmTextColorSetting(ui: UiSettings, onChange: (AlarmTextColor) -> Unit) {
    val background = Color(ui.alarmColor.background)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alarm text colour", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(ui.alarmTextColor.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.medColors.inkMuted)
        }
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlarmTextColor.entries.forEach { option ->
                val chosen = option == ui.alarmTextColor
                val ink = Color(option.argb ?: ui.alarmColor.content)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(background)
                        .border(if (chosen) 3.dp else 1.5.dp, if (chosen) MaterialTheme.medColors.ink else MaterialTheme.medColors.inkMuted, CircleShape)
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { onChange(option) })
                        .semantics { contentDescription = "${option.label} alarm text" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (option == AlarmTextColor.AUTO) "A" else "Aa", color = ink, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (contrast(Color(ui.alarmContent), background) < 3f) {
            Text(
                "This text colour is hard to read on ${ui.alarmColor.label.lowercase()}. Automatic picks a readable one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.medColors.missed,
            )
        }
    }
}

/** WCAG contrast ratio between two colours (1 to 21). */
private fun contrast(a: Color, b: Color): Float {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05f) / (lo + 0.05f)
}
