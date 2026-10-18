package com.medtrack.ui.edit

import com.medtrack.schedule.DropSite
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medtrack.data.MedForm
import com.medtrack.ui.theme.medColors

/** What kind of medicine it is, and how much per dose, with one-tap doses that suit the form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormAndDose(
    form: MedForm?,
    dose: String,
    dropSite: DropSite?,
    onForm: (MedForm?) -> Unit,
    onDose: (String) -> Unit,
    onDropSite: (DropSite) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Form", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.medColors.inkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MedForm.entries.forEach { f ->
                FilterChip(
                    selected = form == f,
                    onClick = { onForm(if (form == f) null else f) },
                    label = { Text(f.label) },
                )
            }
        }
        if (form == MedForm.DROPS) {
            // Required: eye, ear and nose drops then ask which side each dose is for.
            Text("Drops for", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.medColors.inkMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropSite.entries.forEach { site ->
                    FilterChip(selected = dropSite == site, onClick = { onDropSite(site) }, label = { Text(site.label) })
                }
            }
        }
        OutlinedTextField(
            dose,
            onDose,
            Modifier.fillMaxWidth(),
            label = { Text("Dose") },
            placeholder = { Text(form?.dosePresets?.firstOrNull()?.let { "e.g. $it" } ?: "e.g. 1 tablet") },
            singleLine = true,
            colors = fieldColors(),
        )
        val presets = form?.dosePresets.orEmpty()
        if (presets.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset -> AssistChip(onClick = { onDose(preset) }, label = { Text(preset) }) }
            }
        }
    }
}
