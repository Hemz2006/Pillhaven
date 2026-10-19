package com.medtrack.reminders

import com.medtrack.data.doseSide
import com.medtrack.ui.common.SidePicture
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import com.medtrack.ui.common.FormIcon
import com.medtrack.data.doseLineOn
import com.medtrack.data.title
import com.medtrack.data.coverPhoto
import android.graphics.Color.TRANSPARENT
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medtrack.AppContainer
import com.medtrack.container
import com.medtrack.data.DoseLogEntity
import com.medtrack.data.MedicationEntity
import com.medtrack.data.displayName
import com.medtrack.ui.common.PhotoHeader
import com.medtrack.ui.common.PhotoThumb
import com.medtrack.ui.common.TIME
import com.medtrack.ui.theme.MedTrackTheme
import com.medtrack.ui.theme.tabular
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Full-screen alarm shown over the lock screen; lists every dose ringing right now. */
class AlarmActivity : ComponentActivity() {
    private lateinit var sound: AlarmSound

    override fun onCreate(savedInstanceState: Bundle?) {
        // System bar icons match the alarm's text: dark on amber, light on the darker colours.
        val darkText = Color(container.uiSettings.value.alarmContent).luminance() < 0.5f
        val bars = if (darkText) SystemBarStyle.light(TRANSPARENT, TRANSPARENT) else SystemBarStyle.dark(TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val c = container
        // Rings until every dose here is taken, skipped or snoozed: then the screen closes and the sound stops.
        sound = AlarmSound(this)
        ringHere()
        setContent { MedTrackTheme { AlarmScreen(c, onEmpty = ::finish) } }
    }

    /** Another dose rang while the screen was open: its notification brought the system's sound back. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ringHere()
    }

    override fun onDestroy() {
        sound.stop()
        super.onDestroy()
    }

    private fun ringHere() {
        sound.start()
        DoseNotifier(this).hush()
    }
}

@Composable
private fun AlarmScreen(c: AppContainer, onEmpty: () -> Unit) {
    val due by remember {
        combine(c.db.doseLogDao().observePending(), c.db.medicationDao().observeAll()) { logs, meds ->
            val now = Instant.now()
            val byId = meds.associateBy { it.id }
            logs.filter { (it.snoozedUntil ?: it.dueAt) <= now }
                .mapNotNull { log -> byId[log.medicationId]?.let { log to it } }
        }
    }.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(due) { if (due?.isEmpty() == true) onEmpty() }
    val items = due ?: return
    val settings by c.uiSettings.collectAsStateWithLifecycle()
    val background = Color(settings.alarmColor.background)
    val content = Color(settings.alarmContent)

    Surface(color = background, contentColor = content, modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalAlarmBackground provides background) {
            Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
                Text(
                    "Time for your medication",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 16.dp).semantics { heading() },
                )
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(items, key = { _, it -> it.first.id }) { index, (log, med) ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 24.dp), thickness = 2.dp, color = content.copy(alpha = 0.3f))
                        AlarmDose(c, log, med, single = items.size == 1)
                    }
                }
                if (items.size > 1) {
                    AlarmPrimaryButton("Take all", Modifier.padding(bottom = 16.dp)) {
                        c.appScope.launch { c.engine.markAllTaken(items.map { it.first.id }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmDose(c: AppContainer, log: DoseLogEntity, med: MedicationEntity, single: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (single) PhotoHeader(med.coverPhoto, med.title) else PhotoThumb(med.coverPhoto, med.title, 120.dp, form = med.form)
        Text(med.displayName(), style = MaterialTheme.typography.displaySmall)
        log.doseSide?.let {
            Text(
                it.patientLabel.uppercase(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            SidePicture(it, onLit = LocalAlarmBackground.current)
        }
        med.doseLineOn(log.forDate)?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormIcon(med.form, Modifier.padding(end = 8.dp), size = 28.dp, tint = LocalContentColor.current)
                Text(it, style = MaterialTheme.typography.titleLarge)
            }
        }
        Text("Due ${TIME.format(log.dueAt.atZone(ZoneId.systemDefault()))}", style = MaterialTheme.typography.titleMedium.tabular())
        med.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        AlarmPrimaryButton("Taken") { c.appScope.launch { c.engine.markTaken(log.id) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlarmSecondaryButton("Snooze 10 min", Modifier.weight(1f)) { c.appScope.launch { c.engine.snooze(log.id) } }
            AlarmSecondaryButton("Skip", Modifier.weight(1f)) { c.appScope.launch { c.engine.markSkipped(log.id) } }
        }
    }
}

/** A solid button in the alarm's text colour, with its background colour for the label. */
@Composable
private fun AlarmPrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val alarm = LocalContentColor.current
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = alarm, contentColor = LocalAlarmBackground.current),
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) { Text(label, style = MaterialTheme.typography.titleLarge) }
}

@Composable
private fun AlarmSecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val content = LocalContentColor.current
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(2.dp, content),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content, containerColor = Color.Transparent),
        modifier = modifier.heightIn(min = 56.dp),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}

/** The alarm screen's background, for labels drawn on its text-coloured buttons. */
private val LocalAlarmBackground = compositionLocalOf { Color.Black }
