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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.medtrack.AppContainer
import com.medtrack.health.HealthStatus
import com.medtrack.ui.common.ScreenScaffold
import com.medtrack.ui.theme.LabelShape
import com.medtrack.ui.theme.medColors
import kotlinx.coroutines.launch
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Email
import androidx.compose.ui.res.painterResource
import com.medtrack.R

@Composable
fun SettingsScreen(c: AppContainer) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<SetupStatus?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh) {
        status = readSetupStatus(context, c.health)
        c.appScope.launch { c.refreshReminders() } // e.g. exact alarms were just allowed
    }
    val ask = rememberPermissionRequests(c.health) { refresh++ }

    val ui by c.uiSettings.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Settings", isTab = true) { padding ->
        val s = status ?: return@ScreenScaffold
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Row("Notifications", s.notifications, ask::notifications)
            Row("Exact alarms", s.exactAlarms, ask::exactAlarms)
            Row("Full-screen alarms", s.fullScreen, ask::fullScreen)
            Row("Unrestricted battery", s.batteryUnrestricted, ask::battery)
            Text(
                "For Unrestricted battery, tap the words Allow background usage (not the switch), then choose Unrestricted. On Realme, Xiaomi, Oppo, Vivo and Samsung phones, also allow Pillhaven to auto-start and run in the background in the phone's battery settings. After the phone restarts, reminders resume once you unlock it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.medColors.inkMuted,
                modifier = Modifier.padding(16.dp),
            )

            HorizontalDivider(color = MaterialTheme.medColors.foil)
            Text("Meal reminders (optional)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            val (healthText, healthAction) = when (s.health) {
                HealthStatus.NOT_INSTALLED -> "Health Connect isn't installed." to ("Install" to { ask.health(s.health) })
                HealthStatus.NEEDS_UPDATE -> "Health Connect needs an update." to ("Update" to { ask.health(s.health) })
                HealthStatus.NO_BACKGROUND_READ ->
                    "This Health Connect version can't read meals in the background. Update it." to ("Update" to { ask.health(s.health) })
                HealthStatus.NOT_CONNECTED -> "Not connected." to ("Connect" to { ask.health(s.health) })
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

            HorizontalDivider(color = MaterialTheme.medColors.foil)
            Support()

            HorizontalDivider(color = MaterialTheme.medColors.foil)
            About()

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

private const val SUPPORT_EMAIL = "hemzz.devv@gmail.com"
private const val SUPPORT_INSTAGRAM = "https://www.instagram.com/hemzz.devv/"
const val PRIVACY_POLICY_URL = "https://hemz2006.github.io/Pillhaven/privacy-policy.html"
private const val BRAND_DATASET_URL = "https://www.kaggle.com/datasets/shudhanshusingh/az-medicine-dataset-of-india"

/** Starts [intent], or says what's missing when no app can handle it. */
fun Context.openOrSay(intent: Intent, missing: String) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, missing, Toast.LENGTH_SHORT).show()
    }
}

/** Ways to reach the developer: email opens the mail app with a subject filled in, Instagram opens the app or browser. */
@Composable
private fun Support() {
    val context = LocalContext.current
    val open = context::openOrSay
    Text("Support", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
    Text(
        "Questions, problems or ideas? Get in touch.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.medColors.inkMuted,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ListItem(
        headlineContent = { Text("Email", style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(SUPPORT_EMAIL) },
        leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
        modifier = Modifier.clickable(onClickLabel = "Write an email") {
            open(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, "Pillhaven support"),
                "No email app to write to $SUPPORT_EMAIL",
            )
        },
    )
    ListItem(
        headlineContent = { Text("Instagram", style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text("@hemzz.devv") },
        leadingContent = { Icon(painterResource(R.drawable.ic_instagram), contentDescription = null) },
        modifier = Modifier.clickable(onClickLabel = "Open Instagram") {
            open(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_INSTAGRAM)), "No app to open Instagram")
        },
    )
}

/** The privacy policy, credit for the brand list the search is built from, and the app version. */
@Composable
private fun About() {
    val context = LocalContext.current
    fun browse(url: String) = context.openOrSay(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "No browser to open the link")
    Text("About", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
    ListItem(
        headlineContent = { Text("Privacy policy", style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text("What Pillhaven stores and sends") },
        modifier = Modifier.clickable(onClickLabel = "Open the privacy policy") { browse(PRIVACY_POLICY_URL) },
    )
    ListItem(
        headlineContent = { Text("Medicine brand list", style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text("A-Z Medicine Dataset of India by Shudhanshu Singh (Kaggle)") },
        modifier = Modifier.clickable(onClickLabel = "Open the dataset page") { browse(BRAND_DATASET_URL) },
    )
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    Text(
        "Version $version",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.medColors.inkMuted,
        modifier = Modifier.padding(16.dp),
    )
}

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
