package com.medtrack.health

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
import com.medtrack.data.DoseTrigger
import com.medtrack.data.MedTrackDatabase
import com.medtrack.data.MedicationEntity
import com.medtrack.reminders.AlarmScheduler
import com.medtrack.reminders.DoseNotifier
import com.medtrack.reminders.ReminderEngine
import com.medtrack.schedule.Meal
import com.medtrack.schedule.MealType
import com.medtrack.schedule.Schedule
import com.medtrack.schedule.ScheduleRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class MealCheckerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(s: String): Instant = LocalDateTime.parse(s).atZone(zone).toInstant()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: MedTrackDatabase
    private lateinit var engine: ReminderEngine
    private var now = at("2026-10-13T08:15")
    private var meals: List<Meal>? = emptyList()
    private lateinit var checker: MealChecker
    private var medId = 0L

    @Before fun setUp() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        db = Room.inMemoryDatabaseBuilder(context, MedTrackDatabase::class.java).allowMainThreadQueries().build()
        val clock = { Clock.fixed(now, zone) }
        engine = ReminderEngine(db.medicationDao(), db.doseLogDao(), AlarmScheduler(context), DoseNotifier(context).apply { ensureChannel() }, AppPrefs(context), clock)
        checker = MealChecker({ _, _ -> meals }, db.medicationDao(), db.doseLogDao(), engine, clock)
        medId = db.medicationDao().insert(
            MedicationEntity(
                genericName = "metformin",
                steps = listOf(Schedule(ScheduleRule.AfterMeal(MealType.BREAKFAST, 30, LocalTime.of(10, 0)), LocalDate.parse("2026-10-01"))),
                createdAt = now,
            ),
        )
        engine.sync()
    }

    private fun logs() = runBlocking { db.doseLogDao().since(Instant.EPOCH) }
    private fun nextAlarm() = context.getSystemService(AlarmManager::class.java).nextAlarmClock?.let { Instant.ofEpochMilli(it.triggerTime) }
    private fun doseNotifications() = context.getSystemService(NotificationManager::class.java).activeNotifications.filter { it.tag == DoseNotifier.TAG }

    @Test fun breakfastSchedulesDoseAndReplacesFallback() = runBlocking {
        meals = listOf(Meal(at("2026-10-13T08:10"), MealType.BREAKFAST))
        checker.check()
        val log = logs().single()
        assertEquals(at("2026-10-13T08:40"), log.dueAt)
        assertEquals(DoseTrigger.MEAL, log.trigger)
        assertEquals(at("2026-10-13T08:40"), nextAlarm())
        now = at("2026-10-13T08:40"); engine.sync()
        assertEquals(1, doseNotifications().size)
        now = at("2026-10-13T08:45"); engine.sync()
        assertEquals(at("2026-10-13T10:40"), nextAlarm()) // missed-check, not the 10:00 fallback
    }

    @Test fun lateMealRingsImmediately() = runBlocking {
        now = at("2026-10-13T09:30")
        meals = listOf(Meal(at("2026-10-13T08:00"), MealType.BREAKFAST))
        checker.check()
        assertEquals(at("2026-10-13T09:30"), logs().single().dueAt)
        assertEquals(1, doseNotifications().size)
    }

    @Test fun secondMealDoesNotAddAnotherDose() = runBlocking {
        meals = listOf(Meal(at("2026-10-13T08:10"), MealType.BREAKFAST))
        checker.check()
        meals = meals!! + Meal(at("2026-10-13T08:20"), MealType.BREAKFAST)
        now = at("2026-10-13T08:30")
        checker.check()
        assertEquals(1, logs().size)
    }

    @Test fun unreadableHealthConnectDoesNothing() = runBlocking {
        meals = null
        checker.check()
        assertEquals(0, logs().size)
        assertEquals(at("2026-10-13T10:00"), nextAlarm())
    }

    @Test fun mealAfterFallbackIsIgnored() = runBlocking {
        now = at("2026-10-13T10:00"); engine.sync() // fallback rings
        now = at("2026-10-13T10:15")
        meals = listOf(Meal(at("2026-10-13T10:05"), MealType.BREAKFAST))
        checker.check()
        assertEquals(listOf(DoseTrigger.FALLBACK), logs().map { it.trigger })
    }

    @Test fun midnightCrossingDinnerDoseDoesNotCancelNextDay() = runBlocking {
        db.medicationDao().delete(db.medicationDao().get(medId)!!)
        val dinnerId = db.medicationDao().insert(
            MedicationEntity(
                genericName = "dinnerpill",
                steps = listOf(Schedule(ScheduleRule.AfterMeal(MealType.DINNER, 120, LocalTime.of(23, 0)), LocalDate.parse("2026-10-01"))),
                createdAt = now,
            ),
        )
        now = at("2026-10-13T22:35")
        meals = listOf(Meal(at("2026-10-13T22:30"), MealType.DINNER))
        checker.check()
        val first = logs().single()
        assertEquals(at("2026-10-14T00:30"), first.dueAt)
        assertEquals(LocalDate.parse("2026-10-13"), first.forDate)
        // Next day: no meal logged yet, the 23:00 fallback must still be planned.
        now = at("2026-10-14T03:00"); meals = emptyList(); engine.sync()
        assertEquals(at("2026-10-14T23:00"), nextAlarm())
        // Next day's dinner still produces a dose.
        now = at("2026-10-14T20:05")
        meals = listOf(Meal(at("2026-10-14T20:00"), MealType.DINNER))
        checker.check()
        assertEquals(2, logs().filter { it.medicationId == dinnerId }.size)
        assertEquals(LocalDate.parse("2026-10-14"), logs().maxByOrNull { it.dueAt }!!.forDate)
    }

    @Test fun breakfastMealLeavesDinnerFallbackInPlace() = runBlocking {
        val med = db.medicationDao().get(medId)!!
        db.medicationDao().update(
            med.copy(
                steps = listOf(med.steps.single().copy(
                    rules = listOf(
                        ScheduleRule.AfterMeal(MealType.BREAKFAST, 30, LocalTime.of(10, 0)),
                        ScheduleRule.AfterMeal(MealType.DINNER, 0, LocalTime.of(22, 0)),
                    ),
                )),
            ),
        )
        meals = listOf(Meal(at("2026-10-13T08:10"), MealType.BREAKFAST))
        checker.check()
        assertEquals(listOf("meal:BREAKFAST"), logs().map { it.slot })
        // The 10:00 breakfast fallback is gone; dinner's 22:00 fallback still rings.
        now = at("2026-10-13T08:45"); engine.sync()
        now = at("2026-10-13T22:00"); engine.sync()
        assertEquals(listOf("meal:BREAKFAST", "meal:DINNER"), logs().sortedBy { it.dueAt }.map { it.slot })
        // Dinner logged after its fallback rang adds nothing more.
        meals = meals!! + Meal(at("2026-10-13T21:00"), MealType.DINNER)
        now = at("2026-10-13T22:05"); checker.check()
        assertEquals(2, logs().size)
    }

    @Test fun passesRuleSide() = runBlocking {
        val nose = db.medicationDao().insert(
            MedicationEntity(
                genericName = "N", form = MedForm.DROPS, dropSite = DropSite.NOSE,
                steps = listOf(Schedule(ScheduleRule.AfterMeal(MealType.BREAKFAST, 30, LocalTime.of(10, 0), Side.RIGHT), LocalDate.parse("2026-10-01"))),
                createdAt = now,
            ),
        )
        meals = listOf(Meal(at("2026-10-13T08:10"), MealType.BREAKFAST))
        checker.check()
        assertEquals(DoseSide(DropSite.NOSE, Side.RIGHT), logs().single { it.medicationId == nose }.doseSide)
    }
}
