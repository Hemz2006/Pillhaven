package com.medtrack.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class SidesTest {
    private val start = LocalDate.parse("2026-10-18")
    private fun t(s: String) = LocalTime.parse(s)
    private val leftMorning = ScheduleRule.DailyTimes(listOf(t("08:00")), side = Side.LEFT)
    private val rightNight = ScheduleRule.DailyTimes(listOf(t("21:00")), side = Side.RIGHT)

    @Test fun labels() {
        assertEquals("Left eye", DoseSide(DropSite.EYE, Side.LEFT).label)
        assertEquals("Both nostrils", DoseSide(DropSite.NOSE, Side.BOTH).label)
        assertEquals("RIGHT EAR", DoseSide(DropSite.EAR, Side.RIGHT).badge)
        assertEquals("Patient's right eye", DoseSide(DropSite.EYE, Side.RIGHT).patientLabel)
        assertEquals("Both eyes", DoseSide(DropSite.EYE, Side.BOTH).patientLabel)
        assertEquals("Left", DoseSide(DropSite.EAR, Side.LEFT).short)
    }

    @Test fun oldJsonDecodesWithoutSide() {
        val old = ScheduleJson.decodeSteps("""[{"rules":[{"type":"daily","times":["08:00"]}],"startDate":"2026-10-18"}]""")
        assertNull(old[0].rules[0].side)
        val round = ScheduleJson.decodeSteps(ScheduleJson.encodeSteps(listOf(Schedule(leftMorning, start))))
        assertEquals(Side.LEFT, round[0].rules[0].side)
    }

    @Test fun describeAddsSide() {
        val s = Schedule(listOf(leftMorning, rightNight), start)
        assertEquals("Daily at 8:00 AM · Left eye; Daily at 9:00 PM · Right eye", s.describe(DropSite.EYE))
        assertEquals("Daily at 8:00 AM; Daily at 9:00 PM", s.describe())
        assertEquals("Daily at 8:00 AM · Left eye; Daily at 9:00 PM · Right eye", listOf(s).describeSteps(DropSite.EYE))
        assertEquals("Daily at 8:00 AM · Left eye; Daily at 9:00 PM · Right eye", listOf(s).describeOn(start, DropSite.EYE))
    }

    @Test fun withKindKeepsSide() {
        assertEquals(Side.LEFT, leftMorning.withKind(ScheduleKind.INTERVAL, start).side)
        assertEquals(Side.LEFT, leftMorning.withKind(ScheduleKind.AFTER_MEAL, start).side)
        assertEquals(Side.LEFT, leftMorning.withKind(ScheduleKind.WEEKDAYS, start).side)
        assertEquals(Side.RIGHT, leftMorning.withSide(Side.RIGHT).side)
    }

    @Test fun addedStepCopiesSides() {
        assertEquals(Side.LEFT, listOf(Schedule(leftMorning, start)).withAddedStep()[1].rules[0].side)
    }

    @Test fun missingSideIsRefused() {
        val noSide = Schedule(ScheduleRule.DailyTimes(listOf(t("08:00"))), start)
        assertEquals("Choose which eye", listOf(noSide).sidesValidationError(DropSite.EYE))
        assertEquals("Choose which nostril", listOf(noSide).sidesValidationError(DropSite.NOSE))
        val twoSteps = listOf(Schedule(leftMorning, start)).withAddedStep()
            .let { it.withStep(1, it[1].copy(rules = listOf(ScheduleRule.DailyTimes(listOf(t("08:00")))))) }
        assertEquals("Dose 2: choose which eye", twoSteps.sidesValidationError(DropSite.EYE))
        assertNull(listOf(noSide).sidesValidationError(DropSite.OTHER))
        assertNull(listOf(noSide).sidesValidationError(null))
        assertNull(listOf(Schedule(listOf(leftMorning, rightNight), start)).sidesValidationError(DropSite.EYE))
    }

    @Test fun clashingSidesAreRefused() {
        val clash = "8:00 AM is set for two sides – choose Both instead"
        val rightMorning = ScheduleRule.DailyTimes(listOf(t("08:00")), side = Side.RIGHT)
        assertEquals(clash, listOf(Schedule(listOf(leftMorning, rightMorning), start)).sidesValidationError(DropSite.EYE))
        val mondays = ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY), listOf(t("08:00")), side = Side.RIGHT)
        assertEquals(clash, listOf(Schedule(listOf(leftMorning, mondays), start)).sidesValidationError(DropSite.EYE))
        val every6 = ScheduleRule.EveryNHours(6, start.atTime(8, 0), side = Side.LEFT)
        val rightAt2 = ScheduleRule.DailyTimes(listOf(t("14:00")), side = Side.RIGHT)
        assertEquals(
            "2:00 PM is set for two sides – choose Both instead",
            listOf(Schedule(listOf(every6, rightAt2), start)).sidesValidationError(DropSite.EYE),
        )
        val leftAgain = ScheduleRule.DailyTimes(listOf(t("08:00")), side = Side.LEFT)
        assertNull(listOf(Schedule(listOf(leftMorning, leftAgain), start)).sidesValidationError(DropSite.EYE))
        val twoSteps = listOf(Schedule(leftMorning, start)).withAddedStep()
            .let { it.withStep(1, it[1].copy(rules = listOf(leftMorning, rightMorning))) }
        assertEquals("Dose 2: $clash", twoSteps.sidesValidationError(DropSite.EYE))
    }
}
