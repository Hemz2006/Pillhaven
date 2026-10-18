package com.medtrack.ui.detail

import com.medtrack.data.doseSide
import com.medtrack.data.sidedSite
import com.medtrack.ui.common.PhotoCropFlow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.CancellationException
import android.util.Log
import com.medtrack.data.doseLineOn
import com.medtrack.data.subtitle
import com.medtrack.data.title
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medtrack.AppContainer
import com.medtrack.data.DoseLogEntity
import com.medtrack.data.DoseTrigger
import com.medtrack.data.MedicationEntity
import com.medtrack.data.SourceStatus
import com.medtrack.data.displayName
import com.medtrack.lookup.applyLookup
import com.medtrack.schedule.describeSteps
import com.medtrack.ui.common.Capsule
import com.medtrack.ui.common.PhotoGallery
import com.medtrack.ui.common.PhotoSourceButtons
import com.medtrack.ui.common.PhotoSources
import com.medtrack.ui.common.rememberPhotoSources
import com.medtrack.ui.common.ScreenScaffold
import com.medtrack.ui.common.TIME
import com.medtrack.ui.common.TextTag
import com.medtrack.ui.theme.LabelShape
import com.medtrack.ui.theme.medColors
import com.medtrack.ui.theme.tabular
import com.medtrack.ui.today.CapsuleState
import com.medtrack.ui.today.capsuleState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DetailScreen(c: AppContainer, id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val med by remember(id) { c.db.medicationDao().observe(id) }.collectAsStateWithLifecycle(null)
    val logs by remember(id) { c.db.doseLogDao().observeForMedication(id) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Photos picked here are cropped, then added to the medication; the first becomes its main photo if it had none.
    var cropping by remember { mutableStateOf(emptyList<String>()) }
    val photoSources = rememberPhotoSources("detail:$id") { uris ->
        c.appScope.launch {
            val added = uris.mapNotNull { uri ->
                try {
                    c.photos.importFrom(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MedTrack", "Couldn't add a photo", e)
                    null
                }
            }
            cropping = added
        }
    }
    val m = med ?: return
    val zone = ZoneId.systemDefault()
    val days = remember(logs) { historyByDay(logs, LocalDate.now(zone), zone) }

    ScreenScaffold(
        title = m.displayName(),
        isTab = false,
        onBack = onBack,
        actions = { IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                if (m.photos.isEmpty()) {
                    AddPhotosBox(photoSources)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoGallery(m.photos, m.title)
                        PhotoSourceButtons(photoSources)
                    }
                }
            }
            item { LabelBlock(m) }
            item { MyNotesAndSchedule(m) }
            item {
                Actions(
                    m,
                    refreshing = refreshing,
                    onTogglePause = { c.appScope.launch { c.medications.setActive(id, !m.active) } },
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            try {
                                val result = c.lookup.lookupFor(m.genericName, m.ingredients, m.rxcui)
                                c.db.medicationDao().get(id)?.let { c.medications.save(it.applyLookup(result, onlyMissing = false)) }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.e("MedTrack", "Refresh info failed", e)
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                    onDelete = { confirmDelete = true },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Heading("History")
                    if (days.isEmpty()) Text("No doses recorded yet.") else Legend()
                }
            }
            items(days, key = { it.date.toString() }) { day -> HistoryDayRow(day, zone) }
        }
    }
    // Each new photo can be cropped before it's added to the medication.
    if (cropping.isNotEmpty()) {
        PhotoCropFlow(cropping, c.photos) { finals ->
            cropping = emptyList()
            c.appScope.launch {
                val current = c.db.medicationDao().get(id)
                if (current == null) finals.forEach(c.photos::delete) else c.medications.save(current.copy(photos = current.photos + finals))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${m.displayName()}?") },
            text = { Text("This also deletes its photo and dose history. To keep the history, pause it instead.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    c.appScope.launch { c.medications.delete(id) }
                    onBack()
                }) { Text("Delete", color = MaterialTheme.medColors.missed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })

/** The dispensing label: what the medication is and what it is for. */
@Composable
private fun LabelBlock(m: MedicationEntity) {
    val colors = MaterialTheme.medColors
    Surface(shape = LabelShape, color = colors.paperRaised, border = BorderStroke(1.dp, colors.foil), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(m.title, style = MaterialTheme.typography.titleLarge)
                listOfNotNull(m.subtitle, m.doseLineOn(java.time.LocalDate.now())).joinToString(" · ").ifEmpty { null }?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
                }
                if (!m.active) TextTag("Paused", colors.inkMuted, Modifier.padding(top = 4.dp))
            }
            if (m.ingredients.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.ingredients.forEach { i ->
                        Column {
                            Text(listOfNotNull(i.name, i.strength).joinToString(" "), style = MaterialTheme.typography.bodyLarge)
                            i.formula?.let { Text(it, style = MaterialTheme.typography.bodyMedium.tabular(), color = colors.inkMuted) }
                        }
                    }
                }
            }
            m.usedFor?.let {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Heading("Used for")
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                    Text("General information, not medical advice.", style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                }
            }
            sourceNote(m)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted) }
        }
    }
}

private fun sourceNote(m: MedicationEntity): String? = listOfNotNull(
    "Ingredients from RxNorm".takeIf { m.rxnormStatus == SourceStatus.OK },
    "formulas from PubChem".takeIf { m.pubchemStatus == SourceStatus.OK },
    "usage from MedlinePlus".takeIf { m.medlineStatus == SourceStatus.OK },
).joinToString(", ").ifEmpty { null }?.replaceFirstChar { it.uppercase() }?.plus(".")

@Composable
private fun MyNotesAndSchedule(m: MedicationEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        m.notes?.takeIf { it.isNotBlank() }?.let {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Heading("My notes")
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Heading("Schedule")
            Text(m.steps.describeSteps(m.sidedSite), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(m: MedicationEntity, refreshing: Boolean, onTogglePause: () -> Unit, onRefresh: () -> Unit, onDelete: () -> Unit) {
    val ink = BorderStroke(1.dp, MaterialTheme.medColors.inkMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onTogglePause, border = ink, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (m.active) "Pause" else "Resume")
        }
        OutlinedButton(enabled = !refreshing, onClick = onRefresh, border = ink, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (refreshing) "Refreshing…" else "Refresh info")
        }
        val missed = MaterialTheme.medColors.missed
        OutlinedButton(
            onClick = onDelete,
            border = BorderStroke(1.dp, missed),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = missed),
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Delete") }
    }
}

/** Sample capsules naming each state, so colour is never the only cue. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Legend: filled with a tick is taken, dashed is skipped, struck through is missed" },
    ) {
        listOf(CapsuleState.TAKEN to "Taken", CapsuleState.SKIPPED to "Skipped", CapsuleState.MISSED to "Missed").forEach { (state, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Capsule(state, width = 12.dp, height = 18.dp)
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.medColors.inkMuted)
            }
        }
    }
}

data class HistoryDay(val date: LocalDate, val label: String, val logs: List<DoseLogEntity>)

private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** Logs (newest first) grouped by local day: "Today", "Yesterday", then "Mon 12 Oct". Doses within a day run in time order. */
fun historyByDay(logs: List<DoseLogEntity>, today: LocalDate, zone: ZoneId): List<HistoryDay> =
    logs.groupBy { it.dueAt.atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .map { (date, dayLogs) ->
            val label = when (date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> DAY_LABEL.format(date)
            }
            HistoryDay(date, label, dayLogs.sortedBy { it.dueAt })
        }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryDayRow(day: HistoryDay, zone: ZoneId) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(day.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.medColors.inkMuted, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            day.logs.forEach { log ->
                val time = TIME.format(log.dueAt.atZone(zone))
                val state = log.status.capsuleState()
                val trigger = when (log.trigger) {
                    DoseTrigger.SCHEDULE -> ""
                    DoseTrigger.MEAL -> ", after a meal"
                    DoseTrigger.FALLBACK -> ", meal fallback time"
                }
                Column(
                    Modifier.widthIn(min = 48.dp).clearAndSetSemantics {
                        contentDescription = listOfNotNull("$time, ${state.spoken}$trigger", log.doseSide?.label).joinToString(", ")
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Capsule(state, width = 20.dp, height = 32.dp)
                    Text(time, style = MaterialTheme.typography.labelMedium.tabular(), color = MaterialTheme.medColors.inkMuted)
                    log.doseSide?.let { Text(it.short, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.medColors.ink) }
                }
            }
        }
    }
}

/** Shown where the photo goes when there isn't one yet: a dashed box that invites a photo of the strip or box. */
@Composable
private fun AddPhotosBox(sources: PhotoSources) {
    val colors = MaterialTheme.medColors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .clip(LabelShape)
            .drawBehind {
                drawRoundRect(
                    color = colors.inkMuted,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            "A photo of the strip or box helps you recognise it",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        PhotoSourceButtons(sources)
    }
}
