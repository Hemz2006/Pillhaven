package com.medtrack.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.medtrack.schedule.DoseSide
import com.medtrack.schedule.DropSite
import com.medtrack.schedule.Schedule
import com.medtrack.schedule.ScheduleRule
import com.medtrack.schedule.sidesValidationError
import com.medtrack.schedule.stepOn
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class Ingredient(val name: String, val strength: String? = null, val formula: String? = null)

/** Outcome of one lookup source (RxNorm, PubChem or MedlinePlus) for a medication. */
enum class SourceStatus { NOT_RUN, OK, NOT_FOUND, FAILED }

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val genericName: String,
    val brandName: String? = null,
    val doseText: String? = null,
    /** Null for medications saved before forms existed, or when the user hasn't said. */
    val form: MedForm? = null,
    /** What drops are for (eye, ear, nose…); null for other forms, or drops saved before this was asked. */
    val dropSite: DropSite? = null,
    val notes: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val usedFor: String? = null,
    val rxcui: String? = null,
    val rxnormStatus: SourceStatus = SourceStatus.NOT_RUN,
    val pubchemStatus: SourceStatus = SourceStatus.NOT_RUN,
    val medlineStatus: SourceStatus = SourceStatus.NOT_RUN,
    /** Photo file paths; the first is the one shown in lists, notifications and alarms. */
    val photos: List<String> = emptyList(),
    val active: Boolean = true,
    /** Steps run back to back (e.g. 6 times a day for 2 weeks, then 4 times a day); usually just one. */
    @ColumnInfo(name = "schedule") val steps: List<Schedule>,
    val createdAt: Instant,
)

val MedicationEntity.coverPhoto: String? get() = photos.firstOrNull()

/** The name on the pack: the brand when there is one, otherwise the generic name. */
val MedicationEntity.title: String
    get() = brandName?.takeIf { it.isNotBlank() } ?: genericName

/** The generic name when [title] is the brand; null when there is nothing more to say. */
val MedicationEntity.subtitle: String?
    get() = genericName.takeIf { it.isNotBlank() && title != genericName }

/** The dose on [date]: the running step's own dose, otherwise the medication's usual one. */
fun MedicationEntity.doseOn(date: LocalDate): String? =
    steps.stepOn(date)?.dose?.takeIf { it.isNotBlank() } ?: doseText?.takeIf { it.isNotBlank() }

/** "Inhaler · 2 puffs": how it's taken on [date], for lists, Today and alarms; null when neither is known. */
fun MedicationEntity.doseLineOn(date: LocalDate): String? =
    listOfNotNull(form?.label, doseOn(date)).joinToString(" · ").ifEmpty { null }

/** The site when this medicine's doses have sides: drops for an eye, ear or nose. */
val MedicationEntity.sidedSite: DropSite?
    get() = dropSite?.takeIf { form == MedForm.DROPS && it.sided }

/** Where a dose of [rule] goes, or null when this medicine's doses have no side. */
fun MedicationEntity.sideOf(rule: ScheduleRule): DoseSide? =
    sidedSite?.let { site -> rule.side?.let { DoseSide(site, it) } }

/** Why the drops' site or sides can't be saved, or null. */
fun MedicationEntity.sideValidationError(): String? =
    if (form == MedForm.DROPS && dropSite == null) "Choose what the drops are for" else steps.sidesValidationError(sidedSite)

fun MedicationEntity.displayName(): String = subtitle?.let { "$title ($it)" } ?: title

fun newMedication(today: LocalDate, now: Instant) = MedicationEntity(
    genericName = "",
    steps = listOf(Schedule(ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0))), today)),
    createdAt = now,
)
