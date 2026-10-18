package com.medtrack.reminders

import com.medtrack.schedule.DoseSide
import com.medtrack.schedule.DropSite
import com.medtrack.schedule.Side
import com.medtrack.data.DoseTrigger
import com.medtrack.schedule.MealType
import com.medtrack.schedule.Schedule
import com.medtrack.schedule.ScheduleRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(s: String): Instant = LocalDateTime.parse(s).atZone(zone).toInstant()
    private fun daily(id: Long, vararg times: String) =
        MedSchedule(id, Schedule(ScheduleRule.DailyTimes(times.map(LocalTime::parse)), LocalDate.parse("2026-10-01")))
    private fun plan(
        meds: List<MedSchedule>,
        lastScan: String,
        now: String,
        pending: List<PendingDose> = emptyList(),
        handled: Map<SlotKey, Set<LocalDate>> = emptyMap(),
    ) = planScan(meds, handled, pending, at(lastScan), at(now), zone)

    @Test fun nothingDueSchedulesNextDose() {
        val p = plan(listOf(daily(1, "08:00")), "2026-10-13T06:00", "2026-10-13T07:00")
        assertEquals(emptyList<NewDose>(), p.newDoses)
        assertEquals(at("2026-10-13T08:00"), p.nextWake)
    }

    @Test fun dueDoseIsNewAndWakesForMissedCheck() {
        val p = plan(listOf(daily(1, "08:00", "21:00")), "2026-10-13T07:00", "2026-10-13T08:00")
        assertEquals(listOf(NewDose(1, at("2026-10-13T08:00"), DoseTrigger.SCHEDULE)), p.newDoses)
        assertEquals(at("2026-10-13T10:00"), p.nextWake)
    }

    @Test fun twoMedicationsSameMinuteBothDue() {
        val p = plan(listOf(daily(1, "08:00"), daily(2, "08:00")), "2026-10-13T07:00", "2026-10-13T08:00")
        assertEquals(setOf(1L, 2L), p.newDoses.map { it.medicationId }.toSet())
    }

    @Test fun backfillsDosesWhilePhoneWasOff() {
        val p = plan(listOf(daily(1, "08:00", "21:00")), "2026-10-13T07:00", "2026-10-14T09:00")
        assertEquals(listOf(at("2026-10-13T08:00"), at("2026-10-13T21:00")), p.missedDoses.map { it.dueAt })
        assertEquals(listOf(at("2026-10-14T08:00")), p.newDoses.map { it.dueAt })
    }

    @Test fun backfillIsCappedAtSevenDays() {
        val p = plan(listOf(daily(1, "08:00")), "2026-09-01T07:00", "2026-10-13T09:00")
        assertEquals(6, p.missedDoses.size)
        assertEquals(1, p.newDoses.size)
    }

    @Test fun pendingRingsWhenSnoozeEnds() {
        val pending = listOf(PendingDose(10, 1, at("2026-10-13T08:00"), at("2026-10-13T08:11")))
        val before = plan(listOf(daily(1, "08:00")), "2026-10-13T08:01", "2026-10-13T08:05", pending)
        assertEquals(emptyList<Long>(), before.ringLogIds)
        assertEquals(at("2026-10-13T08:11"), before.nextWake)
        val after = plan(listOf(daily(1, "08:00")), "2026-10-13T08:05", "2026-10-13T08:11", pending)
        assertEquals(listOf(10L), after.ringLogIds)
        assertEquals(at("2026-10-13T10:11"), after.nextWake)
    }

    @Test fun pendingBecomesMissedTwoHoursAfterLastRing() {
        val pending = listOf(PendingDose(10, 1, at("2026-10-13T08:00"), null))
        assertEquals(emptyList<Long>(), plan(listOf(daily(1, "08:00")), "2026-10-13T08:00", "2026-10-13T09:59", pending).missedLogIds)
        assertEquals(listOf(10L), plan(listOf(daily(1, "08:00")), "2026-10-13T08:00", "2026-10-13T10:00", pending).missedLogIds)
    }

    @Test fun mealFallbackSkipsHandledDays() {
        val meal = MedSchedule(1, Schedule(ScheduleRule.AfterMeal(MealType.BREAKFAST, 30, LocalTime.of(10, 0)), LocalDate.parse("2026-10-01")))
        val handled = mapOf((1L to "meal:BREAKFAST") to setOf(LocalDate.parse("2026-10-13")))
        val p = plan(listOf(meal), "2026-10-13T09:00", "2026-10-13T10:00", handled = handled)
        assertEquals(emptyList<NewDose>(), p.newDoses)
        assertEquals(at("2026-10-14T10:00"), p.nextWake)
        val unhandled = plan(listOf(meal), "2026-10-13T09:00", "2026-10-13T10:00")
        assertEquals(listOf(NewDose(1, at("2026-10-13T10:00"), DoseTrigger.FALLBACK, "meal:BREAKFAST")), unhandled.newDoses)
    }

    @Test fun eachMealRuleHasItsOwnHandledDays() {
        val rules = listOf(
            ScheduleRule.AfterMeal(MealType.BREAKFAST, 0, LocalTime.of(10, 0)),
            ScheduleRule.AfterMeal(MealType.DINNER, 0, LocalTime.of(22, 0)),
        )
        val med = MedSchedule(1, Schedule(rules, LocalDate.parse("2026-10-01")))
        // Breakfast already handled today: its 10:00 fallback is skipped, dinner's 22:00 still comes.
        val handled = mapOf((1L to "meal:BREAKFAST") to setOf(LocalDate.parse("2026-10-13")))
        val p = plan(listOf(med), "2026-10-13T09:00", "2026-10-13T22:00", handled = handled)
        assertEquals(listOf(NewDose(1, at("2026-10-13T22:00"), DoseTrigger.FALLBACK, "meal:DINNER")), p.newDoses)
    }

    @Test fun mixedRulesAllProduceDoses() {
        val rules = listOf(ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0))), ScheduleRule.AfterMeal(MealType.DINNER, 0, LocalTime.of(22, 0)))
        val med = MedSchedule(1, Schedule(rules, LocalDate.parse("2026-10-01")))
        val morning = plan(listOf(med), "2026-10-13T07:00", "2026-10-13T08:00")
        assertEquals(listOf(NewDose(1, at("2026-10-13T08:00"), DoseTrigger.SCHEDULE)), morning.newDoses)
        assertEquals(at("2026-10-13T10:00"), morning.nextWake) // missed-check for 08:00
        val evening = plan(listOf(med), "2026-10-13T21:00", "2026-10-13T22:00")
        assertEquals(listOf(NewDose(1, at("2026-10-13T22:00"), DoseTrigger.FALLBACK, "meal:DINNER")), evening.newDoses)
    }

    @Test fun noMedicationsNoWake() {
        assertEquals(null, plan(emptyList(), "2026-10-13T07:00", "2026-10-13T08:00").nextWake)
    }

    @Test fun newDoseCarriesSide() {
        val eyes = Schedule(
            listOf(ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0)), Side.LEFT), ScheduleRule.DailyTimes(listOf(LocalTime.of(21, 0)), Side.RIGHT)),
            LocalDate.parse("2026-10-01"),
        )
        val morning = plan(listOf(MedSchedule(1, eyes, DropSite.EYE)), "2026-10-13T07:00", "2026-10-13T08:00")
        assertEquals(DoseSide(DropSite.EYE, Side.LEFT), morning.newDoses.single().side)
        val night = plan(listOf(MedSchedule(1, eyes, DropSite.EYE)), "2026-10-13T20:00", "2026-10-13T21:00")
        assertEquals(DoseSide(DropSite.EYE, Side.RIGHT), night.newDoses.single().side)
        val noSite = plan(listOf(MedSchedule(1, eyes)), "2026-10-13T07:00", "2026-10-13T08:00")
        assertEquals(null, noSite.newDoses.single().side)
    }
}
