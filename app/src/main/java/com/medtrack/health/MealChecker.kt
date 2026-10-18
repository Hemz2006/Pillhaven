package com.medtrack.health

import com.medtrack.data.sideOf
import com.medtrack.data.DoseLogDao
import com.medtrack.data.MedicationDao
import com.medtrack.reminders.ReminderEngine
import com.medtrack.schedule.Meal
import com.medtrack.schedule.ScheduleRule
import com.medtrack.schedule.hasMealRule
import com.medtrack.schedule.mealTriggeredDose
import com.medtrack.schedule.slot
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Turns today's logged meals into after-meal doses. [readMeals] returns null when meals can't be read. */
class MealChecker(
    private val readMeals: suspend (from: Instant, to: Instant) -> List<Meal>?,
    private val medications: MedicationDao,
    private val doseLogs: DoseLogDao,
    private val engine: ReminderEngine,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    suspend fun check() {
        val c = clock()
        val now = c.instant()
        val zone = c.zone
        val today = LocalDate.now(c)
        val mealMeds = medications.active().filter { it.steps.hasMealRule }
        if (mealMeds.isEmpty()) return
        val dayStart = today.atStartOfDay(zone).toInstant()
        val meals = readMeals(dayStart, now) ?: return
        val handledToday = doseLogs.since(dayStart)
            .filter { it.forDate == today && it.slot != null }
            .map { it.medicationId to it.slot }
            .toSet()
        for (med in mealMeds) for (step in med.steps) for (rule in step.rules.filterIsInstance<ScheduleRule.AfterMeal>()) {
            val slot = rule.slot!!
            val handled = if ((med.id to slot) in handledToday) setOf(today) else emptySet()
            val due = mealTriggeredDose(step, rule, meals, today, zone, handled) ?: continue
            engine.addMealDose(med.id, slot, maxOf(due, now), today, med.sideOf(rule))
        }
    }
}
