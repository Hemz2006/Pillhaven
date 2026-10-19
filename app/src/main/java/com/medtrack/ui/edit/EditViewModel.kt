package com.medtrack.ui.edit

import com.medtrack.data.sideValidationError
import com.medtrack.data.title
import com.medtrack.lookup.readTextIn
import android.util.Log
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtrack.AppContainer
import com.medtrack.data.MedicationEntity
import com.medtrack.data.guessForm
import com.medtrack.data.newMedication
import com.medtrack.lookup.Brand
import com.medtrack.lookup.Suggestion
import com.medtrack.lookup.brandKey
import com.medtrack.lookup.formatComposition
import com.medtrack.lookup.parseComposition
import com.medtrack.lookup.applyLookup
import com.medtrack.schedule.stepsValidationError
import com.medtrack.schedule.withIntervalCountsFilled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class EditState(
    val loaded: Boolean = false,
    /** Photos just added, waiting to be cropped (or kept whole) before they join the medication. */
    val pendingCrop: List<String> = emptyList(),
    /** The photos waiting to be cropped came from "Scan medicine": read the first once it's cropped. */
    val scanPending: Boolean = false,
    /** Reading a scanned pack's name. */
    val scanning: Boolean = false,
    /** What the last scan found, shown above the name fields. */
    val scanNote: String? = null,
    val med: MedicationEntity,
    val suggestions: List<Suggestion> = emptyList(),
    val brandSuggestions: List<Brand> = emptyList(),
    val lookingUp: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

class EditViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    private val _state = MutableStateFlow(EditState(med = newMedication(LocalDate.now(), Instant.now())))
    val state = _state.asStateFlow()

    /** The photos stored in the database; files of removed ones are deleted only on save. */
    private var savedPhotos: List<String> = emptyList()
    private var suggestJob: Job? = null
    private var brandJob: Job? = null
    private var lookupJob: Job? = null

    init {
        if (id == 0L) {
            _state.update { it.copy(loaded = true) }
        } else {
            viewModelScope.launch {
                val existing = c.db.medicationDao().get(id)
                savedPhotos = existing?.photos.orEmpty()
                _state.update { s -> if (existing == null) s.copy(loaded = true) else s.copy(med = existing.copy(steps = existing.steps.withIntervalCountsFilled()), loaded = true) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun update(transform: (MedicationEntity) -> MedicationEntity) =
        _state.update { it.copy(med = transform(it.med), error = null) }

    fun onNameChanged(name: String) {
        cancelLookup()
        update { it.copy(genericName = name, rxcui = null) } // a typed name no longer matches a picked suggestion
        suggestJob?.cancel()
        if (name.trim().length < 3) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(400)
            val suggestions = c.lookup.suggest(name)
            _state.update { it.copy(suggestions = suggestions) }
        }
    }

    fun onBrandChanged(name: String) {
        update { it.copy(brandName = name) }
        brandJob?.cancel()
        if (brandKey(name).length < 2) {
            _state.update { it.copy(brandSuggestions = emptyList()) }
            return
        }
        brandJob = viewModelScope.launch {
            delay(250)
            // Brands you've saved before come first: they also cover brands missing from the list.
            val own = c.db.medicationDao().all()
                .filter { it.id != id && it.ingredients.isNotEmpty() && brandKey(it.brandName.orEmpty()).startsWith(brandKey(name)) }
                .map { Brand(it.brandName!!, it.ingredients.formatComposition(), null, saved = true) }
            val listed = c.brands.search(name)
            _state.update { s -> s.copy(brandSuggestions = (own + listed).distinctBy { it.name.lowercase() to it.composition.lowercase() }.take(8)) }
        }
    }

    /** Fills in the composition printed on that brand's pack, then looks up each ingredient. */
    fun pickBrand(brand: Brand) {
        _state.update { it.copy(scanNote = null) }
        brandJob?.cancel()
        suggestJob?.cancel()
        cancelLookup()
        val ingredients = parseComposition(brand.composition)
        _state.update {
            it.copy(
                brandSuggestions = emptyList(),
                suggestions = emptyList(),
                med = it.med.copy(
                    brandName = brand.name,
                    form = guessForm(brand.name) ?: it.med.form,
                    genericName = ingredients.joinToString(" + ") { i -> i.name },
                    ingredients = ingredients,
                    rxcui = null,
                ),
            )
        }
        lookUp(onlyMissing = false)
    }

    fun pickSuggestion(suggestion: Suggestion) {
        suggestJob?.cancel()
        cancelLookup()
        _state.update { it.copy(suggestions = emptyList(), med = it.med.copy(genericName = suggestion.name, rxcui = suggestion.rxcui)) }
        lookUp(onlyMissing = false)
    }

    private fun cancelLookup() {
        lookupJob?.cancel()
        lookupJob = null
        _state.update { it.copy(lookingUp = false) }
    }

    /** [onlyMissing] retries failed sources without overwriting fields that already came back OK. A new lookup replaces a running one. */
    fun lookUp(onlyMissing: Boolean) {
        val med = _state.value.med
        if (med.genericName.isBlank() && med.ingredients.isEmpty()) return
        lookupJob?.cancel()
        _state.update { it.copy(lookingUp = true, suggestions = emptyList()) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                c.lookup.lookupFor(med.genericName.trim(), med.ingredients, med.rxcui) { partial ->
                    _state.update { s -> s.copy(med = s.med.applyLookup(partial, onlyMissing)) }
                }
            } finally {
                if (lookupJob === currentCoroutineContext()[Job]) {
                    lookupJob = null
                    _state.update { it.copy(lookingUp = false) }
                }
            }
        }
        lookupJob = job
        job.start()
    }

    /** Adds photos to crop; [scan] = from "Scan medicine", so the pack's name is read once the first is cropped. */
    fun addPhotos(uris: List<Uri>, scan: Boolean = false) {
        viewModelScope.launch {
            var failed = 0
            val imported = mutableListOf<String>()
            for (uri in uris) {
                val path = try {
                    c.photos.importFrom(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    continue
                }
                imported += path
            }
            if (imported.isNotEmpty()) _state.update { it.copy(pendingCrop = it.pendingCrop + imported, scanPending = it.scanPending || scan) }
            if (failed > 0) _state.update { it.copy(error = if (failed == 1) "Couldn't load one photo" else "Couldn't load $failed photos") }
        }
    }

    /** The crop step is done: add the photos as cropped (or whole), and read the name off a scanned pack. */
    fun finishCrop(paths: List<String>) {
        val scan = _state.value.scanPending
        _state.update { it.copy(pendingCrop = emptyList(), scanPending = false, med = it.med.copy(photos = it.med.photos + paths), error = null) }
        if (scan) paths.firstOrNull()?.let(::scanPack)
    }

    /**
     * Reads the photo of a pack and offers the brands it matches (the brand list and the user's own medications)
     * as suggestions under the name, to tap the right one. Nothing is filled in until one is tapped.
     */
    private fun scanPack(path: String) {
        brandJob?.cancel()
        _state.update { it.copy(scanning = true, scanNote = null, brandSuggestions = emptyList()) }
        viewModelScope.launch {
            val found = try {
                val text = readTextIn(path)
                val own = c.db.medicationDao().all()
                    .filter { it.id != id }
                    .map { Brand(it.title, it.ingredients.formatComposition().ifBlank { it.genericName }, null, saved = true) }
                c.brands.matchText(text, own).flatMap { it.brands }.distinctBy { it.name.lowercase() to it.composition.lowercase() }.take(6)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MedTrack", "Reading the pack failed", e)
                null
            }
            _state.update {
                it.copy(
                    scanning = false,
                    brandSuggestions = found.orEmpty(),
                    scanNote = when {
                        found == null -> "Couldn't read that photo. Type the name instead, or scan again closer and well lit."
                        found.isEmpty() -> "Couldn't find the medicine's name in the photo. Type it instead, or scan again with the name in view."
                        else -> "Found on the pack. Tap the right one below the name."
                    },
                )
            }
        }
    }

    fun clearScanNote() = _state.update { it.copy(scanNote = null) }

    fun removePhoto(path: String) {
        if (path !in savedPhotos) c.photos.delete(path)
        update { it.copy(photos = it.photos - path) }
    }

    /** Moves [path] to the front, so it is shown in lists, notifications and alarms. */
    fun makeMainPhoto(path: String) = update { it.copy(photos = listOf(path) + (it.photos - path)) }

    fun save() {
        val current = _state.value
        if (current.saving || current.saved) return
        val med = current.med
        val error = if (med.genericName.isBlank() && med.brandName.isNullOrBlank()) "Enter the medicine's name" else med.steps.stepsValidationError() ?: med.sideValidationError()
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                // Leaving the screen mid-save must not split the DB write from the photo bookkeeping.
                withContext(NonCancellable) {
                    c.medications.save(
                        med.copy(
                            genericName = med.genericName.trim(),
                            brandName = med.brandName?.trim()?.ifEmpty { null },
                            doseText = med.doseText?.trim()?.ifEmpty { null },
                            notes = med.notes?.trim()?.ifEmpty { null },
                        ),
                    )
                    (savedPhotos - med.photos.toSet()).forEach(c.photos::delete)
                    savedPhotos = med.photos
                    _state.update { it.copy(saving = false, saved = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = "Couldn't save – please try again") }
            }
        }
    }

    override fun onCleared() {
        _state.value.pendingCrop.forEach(c.photos::delete)
        if (!_state.value.saved && !_state.value.saving) (_state.value.med.photos - savedPhotos.toSet()).forEach(c.photos::delete)
    }
}
