package com.medtrack.ui.today

import java.time.LocalDate
import com.medtrack.data.DoseLogEntity
import com.medtrack.data.DoseStatus
import com.medtrack.data.MedicationEntity
import com.medtrack.data.doseSide
import com.medtrack.data.sideOf
import com.medtrack.schedule.DoseSide
import com.medtrack.schedule.ScheduleRule
import com.medtrack.schedule.dosesBetween
import com.medtrack.schedule.mealName
import com.medtrack.schedule.slot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TodayItem(
    val key: String,
    val logId: Long?,
    val medication: MedicationEntity,
    val dueAt: Instant,
    /** null = upcoming, not yet rung. */
    val status: DoseStatus?,
    val snoozedUntil: Instant?,
    val mealHint: String?,
    /** When it was taken or skipped, if recorded. */
    val actedAt: Instant? = null,
    /** The after-meal rule this dose belongs to, for recording it before it rings. */
    val slot: String? = null,
    /** Which eye, ear or nostril, for drops whose doses have sides. */
    val side: DoseSide? = null,
)

/**
 * The doses of [today]: the ones recorded (taken, skipped, missed, waiting) plus, for a day not yet over,
 * the ones still to come. A past day shows only what was recorded; that is its history.
 */
fun buildDay(meds: List<MedicationEntity>, logs: List<DoseLogEntity>, today: LocalDate, now: Instant, zone: ZoneId): List<TodayItem> {
    val dayStart = today.atStartOfDay(zone).toInstant()
    val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
    val byId = meds.associateBy { it.id }
    val todayLogs = logs.filter { it.dueAt >= dayStart && it.dueAt < dayEnd }

    val recorded = todayLogs.mapNotNull { log ->
        byId[log.medicationId]?.let { TodayItem("log-${log.id}", log.id, it, log.dueAt, log.status, log.snoozedUntil, null, log.actedAt, side = log.doseSide) }
    }
    val loggedKeys = todayLogs.map { it.medicationId to it.dueAt }.toSet()
    val handledToday = logs.filter { it.forDate == today && it.slot != null }.map { it.medicationId to it.slot }.toSet()

    val upcoming = meds.filter { it.active }.flatMap { med ->
        med.steps.flatMap { it.rules.map { rule -> it to rule } }.flatMap { (step, rule) ->
            val slot = rule.slot
            val skip = if (slot != null && (med.id to slot) in handledToday) setOf(today) else emptySet()
            val mealHint = (rule as? ScheduleRule.AfterMeal)?.let { "After ${mealName(it.meal)}" }
            dosesBetween(step, rule, maxOf(now, dayStart.minusMillis(1)), dayEnd.minusMillis(1), zone, skip)
                .filter { (med.id to it) !in loggedKeys }
                .map { due -> TodayItem("up-${med.id}-${due.toEpochMilli()}", null, med, due, null, null, mealHint, slot = slot, side = med.sideOf(rule)) }
        }.distinctBy { it.key } // two rules landing on the same minute are one dose
    }
    return (recorded + upcoming).sortedBy { it.dueAt }
}

/** Stays the same when a dose goes from upcoming to recorded, so its row isn't rebuilt. */
val TodayItem.stableKey: String get() = "dose-${medication.id}-${dueAt.toEpochMilli()}"

/** One medication's doses on the day, for the "By medication" view. Medications come in order of their first dose. */
data class MedDay(val medication: MedicationEntity, val items: List<TodayItem>)

fun List<TodayItem>.byMedication(): List<MedDay> =
    groupBy { it.medication.id }.values.map { MedDay(it.first().medication, it) }

/** Today's progress: doses taken out of doses to take. Skipped doses don't count against you. */
data class DayProgress(val taken: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else taken.toFloat() / total
}

fun List<TodayItem>.progress(): DayProgress =
    DayProgress(taken = count { it.status == DoseStatus.TAKEN }, total = count { it.status != DoseStatus.SKIPPED })

/** How a recorded dose looks as a capsule in a medication's history. */
enum class CapsuleState(val spoken: String) {
    TAKEN("taken"), DUE("due now"), MISSED("missed"), SKIPPED("skipped"),
}

/** A rung dose that is still waiting for the user. */
fun TodayItem.isDueNow(now: Instant): Boolean = status == DoseStatus.PENDING && dueAt <= now

fun DoseStatus.capsuleState(): CapsuleState = when (this) {
    DoseStatus.TAKEN -> CapsuleState.TAKEN
    DoseStatus.SKIPPED -> CapsuleState.SKIPPED
    DoseStatus.MISSED -> CapsuleState.MISSED
    DoseStatus.PENDING -> CapsuleState.DUE
}

data class TodayGroup(val key: String, val label: String, val items: List<TodayItem>) {
    fun isDueNow(now: Instant): Boolean = items.any { it.isDueNow(now) }
}

private val HH_MM: DateTimeFormatter = com.medtrack.schedule.TIME_12

/** Groups time-ordered items by display time; upcoming after-meal doses group under their meal ("After dinner, by 22:00"). */
fun groupToday(items: List<TodayItem>, zone: ZoneId): List<TodayGroup> =
    items.groupBy { item ->
        val time = HH_MM.format(item.dueAt.atZone(zone))
        item.mealHint?.let { "$it, by $time" } ?: time
    }.map { (label, groupItems) -> TodayGroup("group-$label", label, groupItems) }
