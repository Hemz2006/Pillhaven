package com.medtrack.reminders

import com.medtrack.schedule.DoseSide
import com.medtrack.schedule.DropSite
import com.medtrack.schedule.Side
import com.medtrack.data.MedForm
import com.medtrack.data.doseSide
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medtrack.data.AppPrefs
import com.medtrack.data.DoseStatus
import com.medtrack.data.MedTrackDatabase
import com.medtrack.data.MedicationEntity
import com.medtrack.schedule.Schedule
import com.medtrack.schedule.ScheduleRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReminderEngineTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(s: String): Instant = LocalDateTime.parse(s).atZone(zone).toInstant()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val nm get() = context.getSystemService(NotificationManager::class.java)
    private val am get() = context.getSystemService(AlarmManager::class.java)
    private lateinit var db: MedTrackDatabase
    private lateinit var engine: ReminderEngine
    private var now = at("2026-10-13T07:00")

    @Before fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        db = Room.inMemoryDatabaseBuilder(context, MedTrackDatabase::class.java).allowMainThreadQueries().build()
        val notifier = DoseNotifier(context).apply { ensureChannel() }
        engine = ReminderEngine(db.medicationDao(), db.doseLogDao(), AlarmScheduler(context), notifier, AppPrefs(context)) {
            Clock.fixed(now, zone)
        }
    }

    private fun addDaily(name: String, vararg times: String): Long = runBlocking {
        db.medicationDao().insert(
            MedicationEntity(
                genericName = name,
                steps = listOf(Schedule(ScheduleRule.DailyTimes(times.map(LocalTime::parse)), LocalDate.parse("2026-10-01"))),
                createdAt = now,
            ),
        )
    }
    private fun doseNotifications() = nm.activeNotifications.filter { it.tag == DoseNotifier.TAG }
    private fun nextAlarm(): Instant? = am.nextAlarmClock?.let { Instant.ofEpochMilli(it.triggerTime) }
    private fun syncAt(time: String) = runBlocking { now = at(time); engine.sync() }

    @Test fun firstSyncSchedulesNextDoseWithoutRinging() {
        addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        assertEquals(at("2026-10-13T08:00"), nextAlarm())
        assertEquals(0, doseNotifications().size)
    }

    @Test fun ringsDueDoseAndWakesForMissedCheck() {
        addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        runBlocking { assertEquals(1, db.doseLogDao().pending().size) }
        assertEquals(1, doseNotifications().size)
        assertEquals(at("2026-10-13T10:00"), nextAlarm())
    }

    @Test fun twoMedicationsSameMinuteBothRing() {
        addDaily("metformin", "08:00")
        addDaily("atorvastatin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        assertEquals(2, doseNotifications().size)
    }

    @Test fun takenClearsNotification() = runBlocking {
        addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        val log = db.doseLogDao().pending().single()
        engine.markTaken(log.id)
        assertEquals(DoseStatus.TAKEN, db.doseLogDao().get(log.id)!!.status)
        assertEquals(0, doseNotifications().size)
        assertEquals(at("2026-10-14T08:00"), nextAlarm())
    }

    @Test fun snoozeRingsAgainAfterTenMinutes() = runBlocking {
        addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        val log = db.doseLogDao().pending().single()
        now = at("2026-10-13T08:01")
        engine.snooze(log.id)
        assertEquals(0, doseNotifications().size)
        assertEquals(at("2026-10-13T08:11"), nextAlarm())
        syncAt("2026-10-13T08:11")
        assertEquals(1, doseNotifications().size)
    }

    @Test fun missedAfterTwoHours() = runBlocking {
        addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        syncAt("2026-10-13T10:00")
        assertEquals(DoseStatus.MISSED, db.doseLogDao().since(Instant.EPOCH).single().status)
        assertEquals(0, doseNotifications().size)
    }

    @Test fun phoneOffOvernightBackfillsHistory() = runBlocking {
        addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-14T09:00")
        val logs = db.doseLogDao().since(Instant.EPOCH).sortedBy { it.dueAt }
        assertEquals(listOf(DoseStatus.MISSED, DoseStatus.PENDING), logs.map { it.status })
        assertEquals(1, doseNotifications().size)
    }

    @Test fun editingScheduleKeepsSinglePendingDose() = runBlocking {
        val id = addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        val med = db.medicationDao().get(id)!!
        db.medicationDao().update(med.copy(steps = listOf(med.steps.single().copy(rules = listOf(ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))))))))
        syncAt("2026-10-13T08:05")
        assertEquals(1, db.doseLogDao().pending().size)
        assertEquals(1, doseNotifications().size)
        assertEquals(at("2026-10-13T10:00"), nextAlarm())
    }

    @Test fun deletedMedicationClearsItsNotification() = runBlocking {
        val id = addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        db.medicationDao().delete(db.medicationDao().get(id)!!)
        syncAt("2026-10-13T08:01")
        assertEquals(0, doseNotifications().size)
        assertNull(nextAlarm())
    }

    @Test fun takingUpcomingDoseEarlyPreventsRing() = runBlocking {
        val id = addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        now = at("2026-10-13T07:30")
        engine.recordUpcoming(id, at("2026-10-13T08:00"), DoseStatus.TAKEN, null, null)
        syncAt("2026-10-13T08:00")
        assertEquals(0, doseNotifications().size)
        assertEquals(DoseStatus.TAKEN, db.doseLogDao().since(Instant.EPOCH).single().status)
    }

    @Test fun failingRingStillBooksNextAlarm() = runBlocking {
        val throwing = object : DoseNotifier(context) {
            override fun ring(items: List<RingItem>) = throw IllegalStateException("boom")
        }
        val failing = ReminderEngine(db.medicationDao(), db.doseLogDao(), AlarmScheduler(context), throwing, AppPrefs(context)) { Clock.fixed(now, zone) }
        addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        now = at("2026-10-13T08:00")
        runCatching { failing.sync() }
        assertEquals(at("2026-10-13T10:00"), nextAlarm())
    }

    @Test fun upcomingChoiceWinsOverAlreadyRungDose() = runBlocking {
        val id = addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        assertEquals(1, doseNotifications().size)
        engine.recordUpcoming(id, at("2026-10-13T08:00"), DoseStatus.TAKEN, null, null)
        assertEquals(DoseStatus.TAKEN, db.doseLogDao().since(Instant.EPOCH).single().status)
        assertEquals(0, doseNotifications().size)
    }

    @Test fun mealDoseDueNowRingsImmediately() = runBlocking {
        val id = addDaily("metformin", "21:00")
        syncAt("2026-10-13T09:00")
        engine.addMealDose(id, "meal:BREAKFAST", at("2026-10-13T09:00"), LocalDate.parse("2026-10-13"), null)
        assertEquals(1, doseNotifications().size)
    }

    @Test fun pausedMedicationStopsRingingAndWaking() = runBlocking {
        val id = addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        assertEquals(1, doseNotifications().size)
        db.medicationDao().setActive(id, false)
        syncAt("2026-10-13T08:05")
        assertEquals(0, doseNotifications().size)
        assertNull(nextAlarm())
        assertEquals(DoseStatus.PENDING, db.doseLogDao().pending().single().status)
    }
    @Test fun undoAfterTakingRungDoseReturnsItToPending() = runBlocking {
        addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        val log = db.doseLogDao().pending().single()
        now = at("2026-10-13T08:05")
        engine.markTaken(log.id)
        engine.undo(log.id, DoseStatus.PENDING)
        val restored = db.doseLogDao().get(log.id)!!
        assertEquals(DoseStatus.PENDING, restored.status)
        assertNull(restored.actedAt)
        assertEquals(at("2026-10-13T10:00"), nextAlarm())
    }

    @Test fun undoAfterTakingMissedDoseReturnsItToMissed() = runBlocking {
        addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        syncAt("2026-10-13T10:00")
        val log = db.doseLogDao().since(Instant.EPOCH).single()
        engine.markTaken(log.id)
        engine.undo(log.id, DoseStatus.MISSED)
        val restored = db.doseLogDao().get(log.id)!!
        assertEquals(DoseStatus.MISSED, restored.status)
        assertNull(restored.actedAt)
    }

    @Test fun undoAfterTakingUpcomingDoseRemovesLogAndStillRings() = runBlocking {
        val id = addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        now = at("2026-10-13T07:30")
        val recorded = engine.recordUpcoming(id, at("2026-10-13T08:00"), DoseStatus.TAKEN, null, null)!!
        assertNull(recorded.previous)
        engine.undo(recorded.logId, recorded.previous)
        assertEquals(0, db.doseLogDao().since(Instant.EPOCH).size)
        assertEquals(at("2026-10-13T08:00"), nextAlarm())
        syncAt("2026-10-13T08:00")
        assertEquals(1, doseNotifications().size)
        assertEquals(DoseStatus.PENDING, db.doseLogDao().pending().single().status)
    }

    @Test fun recordUpcomingOverRungDoseCanBeUndoneToPending() = runBlocking {
        val id = addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        val recorded = engine.recordUpcoming(id, at("2026-10-13T08:00"), DoseStatus.SKIPPED, null, null)!!
        assertEquals(DoseStatus.PENDING, recorded.previous)
        engine.undo(recorded.logId, recorded.previous)
        assertEquals(DoseStatus.PENDING, db.doseLogDao().get(recorded.logId)!!.status)
    }

    @Test fun undoUpcomingAfterItsTimePassedKeepsTheDoseDue() = runBlocking {
        val id = addDaily("metformin", "08:00")
        syncAt("2026-10-13T07:00")
        now = at("2026-10-13T07:59")
        val recorded = engine.recordUpcoming(id, at("2026-10-13T08:00"), DoseStatus.TAKEN, null, null)!!
        syncAt("2026-10-13T08:00")
        now = at("2026-10-13T08:01")
        engine.undo(recorded.logId, recorded.previous)
        // The 08:00 scan already passed this dose by, so deleting the log would lose it; it stays due instead.
        val log = db.doseLogDao().get(recorded.logId)!!
        assertEquals(DoseStatus.PENDING, log.status)
        assertNull(log.actedAt)
        assertEquals(at("2026-10-13T10:00"), nextAlarm())
    }

    @Test fun nextStepRingsItsOwnTimesAndDose() = runBlocking {
        db.medicationDao().insert(
            MedicationEntity(
                genericName = "budesonide",
                steps = listOf(
                    Schedule(ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-13")).copy(dose = "2 puffs"),
                    Schedule(ScheduleRule.DailyTimes(listOf(LocalTime.of(9, 0))), LocalDate.parse("2026-10-14")).copy(dose = "1 puff"),
                ),
                createdAt = now,
            ),
        )
        syncAt("2026-10-13T21:00")
        assertEquals(at("2026-10-14T09:00"), nextAlarm()) // not 08:00 or 20:00: step 2 has begun
        syncAt("2026-10-14T09:00")
        val text = doseNotifications().single().notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        assertEquals(true, "1 puff" in text)
    }

    private fun addEyeDrops(): Long = runBlocking {
        db.medicationDao().insert(
            MedicationEntity(
                genericName = "M", form = MedForm.DROPS, dropSite = DropSite.EYE,
                steps = listOf(
                    Schedule(
                        listOf(ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0)), Side.LEFT), ScheduleRule.DailyTimes(listOf(LocalTime.of(21, 0)), Side.RIGHT)),
                        LocalDate.parse("2026-10-01"),
                    ),
                ),
                createdAt = now,
            ),
        )
    }

    @Test fun scanStoresSide() = runBlocking {
        addEyeDrops()
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        assertEquals(DoseSide(DropSite.EYE, Side.LEFT), db.doseLogDao().pending().single().doseSide)
        val text = doseNotifications().single().notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        assertEquals(true, "Left eye" in text)
    }

    @Test fun recordUpcomingStoresSide() = runBlocking {
        val id = addEyeDrops()
        syncAt("2026-10-13T07:00")
        engine.recordUpcoming(id, at("2026-10-13T21:00"), DoseStatus.TAKEN, null, DoseSide(DropSite.EYE, Side.RIGHT))
        assertEquals(DoseSide(DropSite.EYE, Side.RIGHT), db.doseLogDao().find(id, at("2026-10-13T21:00"))!!.doseSide)
    }

    @Test fun logKeepsSideAfterMedicineEdited() = runBlocking {
        val id = addEyeDrops()
        syncAt("2026-10-13T07:00")
        syncAt("2026-10-13T08:00")
        db.medicationDao().update(db.medicationDao().get(id)!!.copy(form = MedForm.TABLET))
        syncAt("2026-10-13T21:00")
        assertEquals(DoseSide(DropSite.EYE, Side.LEFT), db.doseLogDao().find(id, at("2026-10-13T08:00"))!!.doseSide)
        assertNull(db.doseLogDao().find(id, at("2026-10-13T21:00"))!!.doseSide)
    }

    @Test fun mealDoseStoresSide() = runBlocking {
        val id = addDaily("metformin", "21:00")
        syncAt("2026-10-13T09:00")
        engine.addMealDose(id, "meal:BREAKFAST", at("2026-10-13T09:00"), LocalDate.parse("2026-10-13"), DoseSide(DropSite.EAR, Side.BOTH))
        assertEquals(DoseSide(DropSite.EAR, Side.BOTH), db.doseLogDao().find(id, at("2026-10-13T09:00"))!!.doseSide)
    }
}
