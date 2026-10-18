package com.medtrack.ui.meds

import com.medtrack.data.sidedSite
import com.medtrack.ui.common.OrderButton
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import com.medtrack.ui.common.islandBottom
import androidx.compose.ui.Alignment
import com.medtrack.ui.common.FormIcon
import com.medtrack.data.doseLineOn
import com.medtrack.data.subtitle
import com.medtrack.data.title
import com.medtrack.data.coverPhoto
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medtrack.AppContainer
import com.medtrack.data.MedicationEntity
import com.medtrack.schedule.describeOn
import com.medtrack.ui.common.PhotoThumb
import com.medtrack.ui.common.ScreenScaffold
import com.medtrack.ui.common.TextTag
import com.medtrack.ui.theme.medColors

@Composable
fun MedicationsScreen(c: AppContainer, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val meds by remember { c.db.medicationDao().observeAll() }.collectAsStateWithLifecycle(null)
    ScreenScaffold(
        title = "Medications",
        isTab = true,
        fab = {
            if (!meds.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add medication") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        val list = meds ?: return@ScreenScaffold
        if (list.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("No medications yet. Add one to start getting reminders.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add medication") }
            }
        } else {
            // Bottom padding keeps the last row clear of the extended FAB.
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = islandBottom() + 80.dp)) {
                itemsIndexed(list, key = { _, it -> it.id }) { index, med ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.medColors.foil)
                    MedicationRow(med) { onOpen(med.id) }
                }
            }
        }
    }
}

/** A row that reads like a dispensing label: name, brand and dose, then the schedule in words. */
@Composable
private fun MedicationRow(med: MedicationEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.medColors
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open details", onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PhotoThumb(med.coverPhoto, med.title, 44.dp, Modifier.alpha(if (med.active) 1f else 0.5f), form = med.form)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                med.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (med.active) colors.ink else colors.inkMuted,
            )
            listOfNotNull(med.subtitle, med.doseLineOn(java.time.LocalDate.now())).joinToString(" · ").ifEmpty { null }?.let {
                Row(verticalAlignment = Alignment.Top) {
                    FormIcon(med.form, Modifier.padding(top = 1.dp, end = 6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
                }
            }
            Text(med.steps.describeOn(java.time.LocalDate.now(), med.sidedSite), style = MaterialTheme.typography.bodyMedium, color = if (med.active) colors.ink else colors.inkMuted)
            if (!med.active) TextTag("Paused", colors.inkMuted, Modifier.padding(top = 4.dp))
        }
        OrderButton(med)
    }
}
