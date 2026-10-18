package com.medtrack.reminders

import com.medtrack.data.DoseTrigger
import com.medtrack.schedule.DoseSide
import com.medtrack.schedule.DropSite
import com.medtrack.schedule.Schedule
import com.medtrack.schedule.ScheduleRule
import com.medtrack.schedule.dosesBetween
import com.medtrack.schedule.nextDose
import com.medtrack.schedule.slot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val MISSED_AFTER: Duration = Duration.ofHours(2)
val SNOOZE_FOR: Duration = Duration.ofMinutes(10)
private val MAX_BACKFILL: Duration = Duration.ofDays(7)

/**
 * One step of a medication's schedule; a medication with several steps gives several of these.
 * [site] is set when its doses have sides (drops for an eye, ear or nose).
 */
data class MedSchedule(val medicationId: Long, val schedule: Schedule, val site: DropSite? = null)
data class PendingDose(val logId: Long, val medicationId: Long, val dueAt: Instant, val snoozedUntil: Instant?)
data class NewDose(val medicationId: Long, val dueAt: Instant, val trigger: DoseTrigger, val slot: String? = null, val side: DoseSide? = null)

/** An after-meal rule's doses: (medication id, [com.medtrack.schedule.slot]). */
typealias SlotKey = Pair<Long, String>

data class ScanPlan(
    /** Insert as PENDING and ring. */
    val newDoses: List<NewDose>,
    /** Due while the phone couldn't ring, more than [MISSED_AFTER] ago: insert as MISSED. */
    val missedDoses: List<NewDose>,
    /** Existing PENDING logs to mark MISSED. */
    val missedLogIds: List<Long>,
    /** Existing PENDING logs whose ring time (due or snooze end) has just arrived. */
    val ringLogIds: List<Long>,
    /** When to wake next, or null if nothing is scheduled. */
    val nextWake: Instant?,
)

/**
 * Decides what a wake-up at [now] must record, ring and schedule, given the previous wake-up at [lastScanAt].
 * [handledDates] lists, per after-meal rule, the days that already have a dose, so no fallback is added for them.
 */
fun planScan(
    meds: List<MedSchedule>,
    handledDates: Map<SlotKey, Set<LocalDate>>,
    pending: List<PendingDose>,
    lastScanAt: Instant,
    now: Instant,
    zone: ZoneId,
): ScanPlan {
    val from = maxOf(lastScanAt, now.minus(MAX_BACKFILL))
    val missedCutoff = now.minus(MISSED_AFTER)
    val newDoses = mutableListOf<NewDose>()
    val missedDoses = mutableListOf<NewDose>()
    val wakes = mutableListOf<Instant>()

    for (med in meds) for (rule in med.schedule.rules) {
        val slot = rule.slot
        val skip = slot?.let { handledDates[med.medicationId to it] }.orEmpty()
        val trigger = if (rule is ScheduleRule.AfterMeal) DoseTrigger.FALLBACK else DoseTrigger.SCHEDULE
        val side = med.site?.let { site -> rule.side?.let { DoseSide(site, it) } }
        for (due in dosesBetween(med.schedule, rule, from, now, zone, skip)) {
            val dose = NewDose(med.medicationId, due, trigger, slot, side)
            if (due <= missedCutoff) missedDoses += dose else {
                newDoses += dose
                wakes += due.plus(MISSED_AFTER)
            }
        }
        nextDose(med.schedule, rule, now, zone, skip)?.let { wakes += it }
    }

    val missedLogIds = mutableListOf<Long>()
    val ringLogIds = mutableListOf<Long>()
    for (dose in pending) {
        val ringAt = dose.snoozedUntil ?: dose.dueAt
        if (ringAt <= missedCutoff) {
            missedLogIds += dose.logId
            continue
        }
        if (ringAt > lastScanAt && ringAt <= now) ringLogIds += dose.logId
        wakes += if (ringAt > now) ringAt else ringAt.plus(MISSED_AFTER)
    }

    return ScanPlan(newDoses, missedDoses, missedLogIds, ringLogIds, wakes.filter { it > now }.minOrNull())
}
