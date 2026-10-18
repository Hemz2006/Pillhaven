package com.medtrack.reminders

import com.medtrack.data.doseLineOn
import com.medtrack.data.AppPrefs
import com.medtrack.data.DoseLogDao
import com.medtrack.data.DoseLogEntity
import com.medtrack.data.DoseStatus
import com.medtrack.data.DoseTrigger
import com.medtrack.data.MedicationDao
import com.medtrack.data.coverPhoto
import com.medtrack.data.displayName
import com.medtrack.data.doseSide
import com.medtrack.data.sidedSite
import com.medtrack.schedule.DoseSide
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What [ReminderEngine.undo] needs to reverse a choice: the log and its status before (null = it had no log). */
data class UndoTarget(val logId: Long, val previous: DoseStatus?)

/**
 * Owns dose logs, notifications and the wake-up alarm. Every entry point ends with a scan,
 * so the stored state, the visible notifications and the next alarm always agree.
 */
class ReminderEngine(
    private val medications: MedicationDao,
    private val doseLogs: DoseLogDao,
    private val alarms: AlarmScheduler,
    private val notifier: DoseNotifier,
    private val prefs: AppPrefs,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    private val mutex = Mutex()

    private fun now(): Instant = clock().instant().truncatedTo(ChronoUnit.MILLIS)

    suspend fun sync() = mutex.withLock { scan() }

    suspend fun markTaken(logId: Long) = setStatus(listOf(logId), DoseStatus.TAKEN)
    suspend fun markSkipped(logId: Long) = setStatus(listOf(logId), DoseStatus.SKIPPED)
    suspend fun markAllTaken(logIds: List<Long>) = setStatus(logIds, DoseStatus.TAKEN)

    suspend fun snooze(logId: Long) = mutex.withLock {
        doseLogs.snooze(logId, now().plus(SNOOZE_FOR))
        notifier.cancel(logId)
        scan()
    }

    /**
     * Taken/skipped from the Today screen before the dose rang; stops it ringing later.
     * Returns what [undo] needs to reverse it, or null when nothing changed (the dose was already handled).
     */
    suspend fun recordUpcoming(medicationId: Long, dueAt: Instant, status: DoseStatus, slot: String?, side: DoseSide?): UndoTarget? = mutex.withLock {
        val at = now()
        val id = doseLogs.insertIgnore(
            DoseLogEntity(
                medicationId = medicationId, dueAt = dueAt, status = status, trigger = DoseTrigger.SCHEDULE,
                forDate = dueAt.atZone(clock().zone).toLocalDate(), actedAt = at, slot = slot,
                dropSite = side?.site, side = side?.side,
            ),
        )
        val target = if (id > 0) {
            UndoTarget(id, null)
        } else {
            // The alarm scan got there first; the user's choice must still win over the pending dose.
            doseLogs.find(medicationId, dueAt)?.takeIf { it.status == DoseStatus.PENDING }?.let {
                doseLogs.setStatus(it.id, status, at)
                notifier.cancel(it.id)
                UndoTarget(it.id, DoseStatus.PENDING)
            }
        }
        scan()
        target
    }

    /**
     * Reverses a take/skip: restores [previous] (unacted), or with null deletes the log [recordUpcoming] created
     * so the dose is upcoming again. If a scan has already passed that dose's time, deleting would lose it,
     * so it is restored as PENDING instead. A restored PENDING dose is not re-posted until its next ring time.
     */
    suspend fun undo(logId: Long, previous: DoseStatus?) = mutex.withLock {
        val log = doseLogs.get(logId)
        if (log != null) {
            when {
                previous != null -> doseLogs.setStatus(logId, previous, null)
                log.dueAt <= (prefs.lastScanAt ?: now()) -> doseLogs.setStatus(logId, DoseStatus.PENDING, null)
                else -> doseLogs.delete(logId)
            }
        }
        scan()
    }

    /** A meal was detected: add the dose for the after-meal rule [slot] (it also marks the day handled for that rule's fallback). */
    suspend fun addMealDose(medicationId: Long, slot: String, dueAt: Instant, forDate: LocalDate, side: DoseSide?) = mutex.withLock {
        if (doseLogs.existsForSlot(medicationId, slot, forDate)) return@withLock
        val id = doseLogs.insertIgnore(
            DoseLogEntity(medicationId = medicationId, dueAt = dueAt, status = DoseStatus.PENDING, trigger = DoseTrigger.MEAL, forDate = forDate, slot = slot,
                dropSite = side?.site, side = side?.side),
        )
        scan(ringNow = if (id > 0 && dueAt <= now()) listOf(id) else emptyList())
    }

    private suspend fun setStatus(logIds: List<Long>, status: DoseStatus) = mutex.withLock {
        val at = now()
        logIds.forEach {
            doseLogs.setStatus(it, status, at)
            notifier.cancel(it)
        }
        scan()
    }

    private suspend fun scan(ringNow: List<Long> = emptyList()) {
        val c = clock()
        val now = now()
        val zone = c.zone
        val lastScan = prefs.lastScanAt ?: now
        val meds = medications.active()
        val activeIds = meds.map { it.id }.toSet()
        // Paused medications stay PENDING in the DB but are silenced until resumed.
        val pendingActive = doseLogs.pending().filter { it.medicationId in activeIds }
        val recent = doseLogs.since(minOf(lastScan, now).minus(Duration.ofDays(8)))
        val handled = recent.mapNotNull { log -> log.slot?.let { (log.medicationId to it) to log.forDate } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.toSet() }

        val plan = planScan(
            meds = meds.flatMap { med -> med.steps.map { MedSchedule(med.id, it, med.sidedSite) } },
            handledDates = handled,
            pending = pendingActive.map { PendingDose(it.id, it.medicationId, it.dueAt, it.snoozedUntil) },
            lastScanAt = lastScan,
            now = now,
            zone = zone,
        )

        // Whatever fails below, the next wake-up must still be booked or later doses would be lost.
        try {
            val inserted = plan.newDoses.map {
                doseLogs.insertIgnore(DoseLogEntity(medicationId = it.medicationId, dueAt = it.dueAt, status = DoseStatus.PENDING, trigger = it.trigger, forDate = it.dueAt.atZone(zone).toLocalDate(), slot = it.slot, dropSite = it.side?.site, side = it.side?.side))
            }.filter { it > 0 }
            plan.missedDoses.forEach {
                doseLogs.insertIgnore(DoseLogEntity(medicationId = it.medicationId, dueAt = it.dueAt, status = DoseStatus.MISSED, trigger = it.trigger, forDate = it.dueAt.atZone(zone).toLocalDate(), slot = it.slot, dropSite = it.side?.site, side = it.side?.side))
            }
            if (plan.missedLogIds.isNotEmpty()) doseLogs.markMissed(plan.missedLogIds)
            prefs.lastScanAt = now

            notifier.cancelAllExcept(doseLogs.pending().filter { it.medicationId in activeIds }.map { it.id }.toSet())
            val ring = doseLogs.byIds((inserted + plan.ringLogIds + ringNow).distinct()).filter { it.status == DoseStatus.PENDING && it.medicationId in activeIds }
            if (ring.isNotEmpty()) {
                val medsById = medications.byIds(ring.map { it.medicationId }.distinct()).associateBy { it.id }
                notifier.ring(
                    ring.sortedBy { it.dueAt }.mapNotNull { log ->
                        medsById[log.medicationId]?.let { med ->
                            RingItem(log.id, med.displayName(), listOfNotNull(med.doseLineOn(log.forDate), log.doseSide?.label, med.notes?.takeIf { it.isNotBlank() }).joinToString(" · "), med.coverPhoto)
                        }
                    },
                )
            }
        } finally {
            alarms.set(plan.nextWake)
        }
    }
}
