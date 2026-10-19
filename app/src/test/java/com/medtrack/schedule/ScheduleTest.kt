package com.medtrack.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleTest {
    private val start = LocalDate.of(2026, 10, 13)
    private fun t(s: String) = LocalTime.parse(s)

    private val samples = listOf(
        Schedule(ScheduleRule.DailyTimes(listOf(t("08:00"), t("21:00"))), start),
        Schedule(ScheduleRule.Weekdays(setOf(MONDAY, WEDNESDAY, FRIDAY), listOf(t("08:00"))), start, start.plusDays(7)),
        Schedule(ScheduleRule.EveryNHours(6, LocalDateTime.of(start, t("06:00")), dosesPerDay = 3), start),
        Schedule(ScheduleRule.AfterMeal(MealType.BREAKFAST, 30, t("10:00")), start),
    )

    @Test fun jsonRoundTripsEveryRuleType() {
        samples.forEach { assertEquals(it, ScheduleJson.decodeSteps(ScheduleJson.encodeSteps(listOf(it))).single()) }
    }

    @Test fun validSchedulesHaveNoError() {
        samples.forEach { assertNull(it.validationError()) }
    }

    @Test fun validationCatchesBadInput() {
        assertEquals("Add at least one time", Schedule(ScheduleRule.DailyTimes(emptyList()), start).validationError())
        assertEquals("Pick at least one day", Schedule(ScheduleRule.Weekdays(emptySet(), listOf(t("08:00"))), start).validationError())
        assertEquals("Interval must be 1–24 hours", Schedule(ScheduleRule.EveryNHours(0, LocalDateTime.of(start, t("06:00"))), start).validationError())
        assertEquals("Delay must be 0–240 minutes", Schedule(ScheduleRule.AfterMeal(MealType.ANY, 300, t("10:00")), start).validationError())
        assertEquals("End date is before start date", Schedule(ScheduleRule.DailyTimes(listOf(t("08:00"))), start, start.minusDays(1)).validationError())
        assertEquals("Add at least one schedule", Schedule(emptyList(), start).validationError())
        val breakfast = ScheduleRule.AfterMeal(MealType.BREAKFAST, 0, t("10:00"))
        assertEquals("Each meal can only have one after-meal schedule", Schedule(listOf(breakfast, breakfast.copy(delayMinutes = 30)), start).validationError())
        assertEquals(
            "\"Any meal\" can't be combined with other after-meal schedules",
            Schedule(listOf(breakfast, ScheduleRule.AfterMeal(MealType.ANY, 0, t("13:00"))), start).validationError(),
        )
        // A later rule's error is reported too.
        assertEquals("Add at least one time", Schedule(listOf(breakfast, ScheduleRule.DailyTimes(emptyList())), start).validationError())
    }

    @Test fun describesSeveralRules() {
        val s = Schedule(
            listOf(ScheduleRule.AfterMeal(MealType.BREAKFAST, 0, t("10:00")), ScheduleRule.AfterMeal(MealType.DINNER, 30, t("22:00"))),
            start,
            start.plusDays(7),
        )
        assertEquals("Right after breakfast (by 10:00 AM); 30 min after dinner (by 10:00 PM), until 20 Oct", s.describe())
        assertEquals(s, ScheduleJson.decodeSteps(ScheduleJson.encodeSteps(listOf(s))).single())
    }

    @Test fun describesEachRuleType() {
        assertEquals("Daily at 8:00 AM, 9:00 PM", samples[0].describe())
        assertEquals("Mon, Wed, Fri at 8:00 AM, until 20 Oct", samples[1].describe())
        assertEquals("Every 6 h: 6:00 AM, 12:00 PM, 6:00 PM", samples[2].describe())
        assertEquals("30 min after breakfast (by 10:00 AM)", samples[3].describe())
        assertEquals(
            "Right after any meal (by 1:00 PM)",
            Schedule(ScheduleRule.AfterMeal(MealType.ANY, 0, t("13:00")), start).describe(),
        )
    }

    @Test fun everyNHoursTakesTheChosenNumberOfDoses() {
        val rule = ScheduleRule.EveryNHours(3, LocalDateTime.of(start, t("06:00")), dosesPerDay = 5)
        assertEquals(listOf("06:00", "09:00", "12:00", "15:00", "18:00").map(::t), rule.dailyTimes())
        assertNull(Schedule(rule, start).validationError())
    }

    @Test fun everyNHoursNeedsADoseCount() {
        val rule = ScheduleRule.EveryNHours(3, LocalDateTime.of(start, t("06:00")))
        assertEquals("Enter how many doses a day", Schedule(rule, start).validationError())
    }

    @Test fun everyNHoursRefusesDosesThatRunPastMidnight() {
        // 6 AM, 11 AM, 4 PM and 9 PM fit; a fifth would be 2 AM the next day.
        val rule = ScheduleRule.EveryNHours(5, LocalDateTime.of(start, t("06:00")), dosesPerDay = 5)
        assertEquals("Only 4 doses fit between 6:00 AM and midnight at every 5 hours", Schedule(rule, start).validationError())
        assertNull(Schedule(rule.copy(dosesPerDay = 4), start).validationError())
    }

    @Test fun everyNHoursSavedWithoutACountKeepsEveryDoseBeforeMidnight() {
        val old = ScheduleJson.decodeSteps("""[{"rules":[{"type":"interval","intervalHours":3,"anchor":"2026-10-13T06:00"}],"startDate":"2026-10-13"}]""")
        val rule = old.single().rules.single() as ScheduleRule.EveryNHours
        assertNull(rule.dosesPerDay)
        assertEquals(listOf("06:00", "09:00", "12:00", "15:00", "18:00", "21:00").map(::t), rule.dailyTimes())
        assertEquals(6, (old.withIntervalCountsFilled().single().rules.single() as ScheduleRule.EveryNHours).dosesPerDay)
    }
}
