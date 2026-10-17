package com.medtrack.ui.edit

import com.medtrack.ui.common.PhotoSources
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.Box
import com.medtrack.ui.common.PhotoCropFlow
import com.medtrack.ui.common.rememberPhotoSources
import com.medtrack.ui.common.MedAlert
import com.medtrack.ui.common.AlertTone
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medtrack.AppContainer
import com.medtrack.data.Ingredient
import com.medtrack.data.MedicationEntity
import com.medtrack.data.SourceStatus
import com.medtrack.lookup.Brand
import com.medtrack.ui.common.ScreenScaffold
import com.medtrack.ui.common.containerViewModel
import com.medtrack.ui.theme.LabelShape
import com.medtrack.ui.theme.medColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditMedicationScreen(c: AppContainer, id: Long, onDone: () -> Unit) {
    val vm = containerViewModel { EditViewModel(c, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    val photoSources = rememberPhotoSources("edit:$id", vm::addPhotos)
    val scanSources = rememberPhotoSources("scan:$id") { vm.addPhotos(it, scan = true) }

    val med = state.med
    ScreenScaffold(
        title = if (id == 0L) "Add medication" else "Edit medication",
        isTab = false,
        onBack = onDone,
        actions = { TextButton(onClick = vm::save, enabled = state.loaded) { Text("Save") } },
    ) { padding ->
        if (!state.loaded) return@ScreenScaffold
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            state.error?.let { MedAlert(AlertTone.DANGER, it, onDismiss = vm::clearError) }

            ScanMedicine(scanSources, scanning = state.scanning)
            state.scanNote?.let { MedAlert(if (state.brandSuggestions.isEmpty()) AlertTone.WARNING else AlertTone.INFO, it, onDismiss = vm::clearScanNote) }

            Section("Medication") {
                OutlinedTextField(
                    value = med.brandName.orEmpty(),
                    onValueChange = vm::onBrandChanged,
                    label = { Text("Name on the pack (e.g. Dolo 650)") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.brandSuggestions.forEach { BrandRow(it) { vm.pickBrand(it) } }
                OutlinedTextField(
                    value = med.genericName,
                    onValueChange = vm::onNameChanged,
                    label = { Text("Generic name or composition") },
                    supportingText = { Text("Filled in when you pick a brand. Or type it, e.g. Paracetamol 650mg + Caffeine 50mg") },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.suggestions.forEach { s ->
                    TextButton(onClick = { vm.pickSuggestion(s) }) { Text(s.name) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { vm.lookUp(onlyMissing = false) },
                        enabled = (med.genericName.isNotBlank() || med.ingredients.isNotEmpty()) && !state.lookingUp,
                    ) { Text("Look up info") }
                    if (state.lookingUp) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
                LookupStatus(med, state.lookingUp) { vm.lookUp(onlyMissing = true) }
                FormAndDose(
                    form = med.form,
                    dose = med.doseText.orEmpty(),
                    onForm = { f -> vm.update { it.copy(form = f) } },
                    onDose = { d -> vm.update { it.copy(doseText = d) } },
                )
            }

            Section("What's in it") {
                IngredientsEditor(med.ingredients) { list -> vm.update { it.copy(ingredients = list) } }
                if (med.rxnormStatus == SourceStatus.OK) SourceNote("Checked against RxNorm")
                if (med.pubchemStatus == SourceStatus.OK) SourceNote("Formulas from PubChem")
                OutlinedTextField(med.usedFor.orEmpty(), { v -> vm.update { it.copy(usedFor = v) } }, Modifier.fillMaxWidth(), label = { Text("Used for") }, minLines = 2, colors = fieldColors())
                if (med.medlineStatus == SourceStatus.OK) SourceNote("From MedlinePlus")
                SourceNote("General information, not medical advice.")
            }

            Section("How you take it") {
                OutlinedTextField(med.notes.orEmpty(), { v -> vm.update { it.copy(notes = v) } }, Modifier.fillMaxWidth(), label = { Text("My notes (how I take it)") }, minLines = 2, colors = fieldColors())
                PhotosEditor(
                    photos = med.photos,
                    name = med.genericName.ifBlank { "Medication" },
                    sources = photoSources,
                    onRemove = vm::removePhoto,
                    onMakeMain = vm::makeMainPhoto,
                )
            }

            Section("Schedule") {
                ScheduleEditor(med.steps, med.doseText) { s -> vm.update { it.copy(steps = s) } }
            }

            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save") }
        }
    }
    if (state.pendingCrop.isNotEmpty()) PhotoCropFlow(state.pendingCrop, c.photos, vm::finishCrop)
}

/**
 * "Scan medicine": photograph (or choose a photo of) the box or strip; the photo is added to the medication and the
 * name read from it is offered as suggestions.
 */
@Composable
private fun ScanMedicine(sources: PhotoSources, scanning: Boolean) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Button(onClick = { open = true }, enabled = !scanning, modifier = Modifier.heightIn(min = 48.dp)) { Text("Scan medicine") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text("Take a photo") }, onClick = { open = false; sources.takePhoto() })
                    DropdownMenuItem(text = { Text("Choose from photos") }, onClick = { open = false; sources.choosePhotos() })
                }
            }
            if (scanning) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reading the pack…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "Photograph the name on the box or strip. It's added as the medication's photo, and its name is suggested below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.medColors.inkMuted,
        )
    }
}

/** A titled group of fields; sections sit 24dp apart. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** Field outlines in muted ink: the foil outline colour is too faint for an input's edge. */
@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.medColors.inkMuted)

@Composable
private fun LookupStatus(med: MedicationEntity, lookingUp: Boolean, onRetry: () -> Unit) {
    if (lookingUp) return
    val problems = buildList {
        when (med.rxnormStatus) {
            SourceStatus.NOT_FOUND -> add("Couldn't find this name. Check the spelling or enter the details yourself." to null)
            SourceStatus.FAILED -> add("Not looked up yet" to "The lookup needs an internet connection.")
            else -> Unit
        }
        if (med.pubchemStatus == SourceStatus.FAILED) add("Formula lookup failed" to "PubChem didn't answer.")
        if (med.medlineStatus == SourceStatus.FAILED) add("Usage note lookup failed" to "MedlinePlus didn't answer.")
    }
    problems.forEach { (title, detail) ->
        MedAlert(
            if (detail == null) AlertTone.WARNING else AlertTone.DANGER, title,
            description = detail,
            actionLabel = if (detail == null) null else "Retry",
            onAction = if (detail == null) null else onRetry,
        )
    }
}

@Composable
private fun SourceNote(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.medColors.inkMuted)

@Composable
private fun IngredientsEditor(ingredients: List<Ingredient>, onChange: (List<Ingredient>) -> Unit) {
    ingredients.forEachIndexed { index, ingredient ->
        fun set(updated: Ingredient) = onChange(ingredients.toMutableList().also { it[index] = updated })
        Surface(shape = LabelShape, color = MaterialTheme.medColors.paperRaised, border = BorderStroke(1.dp, MaterialTheme.medColors.foil), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(ingredient.name, { set(ingredient.copy(name = it)) }, Modifier.weight(1f), label = { Text("Ingredient") }, singleLine = true, colors = fieldColors())
                    IconButton(onClick = { onChange(ingredients.filterIndexed { i, _ -> i != index }) }) {
                        Icon(Icons.Default.Delete, "Remove ingredient")
                    }
                }
                OutlinedTextField(ingredient.strength.orEmpty(), { set(ingredient.copy(strength = it.ifEmpty { null })) }, Modifier.fillMaxWidth(), label = { Text("Strength") }, singleLine = true, colors = fieldColors())
                OutlinedTextField(ingredient.formula.orEmpty(), { set(ingredient.copy(formula = it.ifEmpty { null })) }, Modifier.fillMaxWidth(), label = { Text("Chemical formula") }, singleLine = true, colors = fieldColors())
            }
        }
    }
    TextButton(onClick = { onChange(ingredients + Ingredient("")) }) { Text("Add ingredient") }
}

/** A brand suggestion: its name, then what the pack says it contains. */
@Composable
private fun BrandRow(brand: Brand, onClick: () -> Unit) {
    val colors = MaterialTheme.medColors
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(LabelShape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(brand.name, style = MaterialTheme.typography.titleMedium, color = colors.ink)
        Text(brand.composition, style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
        val source = if (brand.saved) "From your medications" else brand.manufacturer
        source?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.inkMuted) }
    }
}
