package com.medtrack.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class ScheduleKind(val label: String) {
    DAILY("Daily"), WEEKDAYS("Weekdays"), INTERVAL("Every N hours"), AFTER_MEAL("After meal")
}

val ScheduleRule.kind: ScheduleKind
    get() = when (this) {
        is ScheduleRule.DailyTimes -> ScheduleKind.DAILY
        is ScheduleRule.Weekdays -> ScheduleKind.WEEKDAYS
        is ScheduleRule.EveryNHours -> ScheduleKind.INTERVAL
        is ScheduleRule.AfterMeal -> ScheduleKind.AFTER_MEAL
    }

/** A sensible "remind anyway" time for each meal. */
fun defaultFallback(meal: MealType): LocalTime = when (meal) {
    MealType.BREAKFAST -> LocalTime.of(10, 0)
    MealType.LUNCH -> LocalTime.of(15, 0)
    MealType.DINNER -> LocalTime.of(22, 0)
    MealType.ANY -> LocalTime.of(13, 0)
}

/** Changes the meal; a fallback time still at the old meal's default moves to the new meal's default. */
fun ScheduleRule.AfterMeal.withMeal(meal: MealType): ScheduleRule.AfterMeal =
    copy(meal = meal, fallbackTime = if (fallbackTime == defaultFallback(this.meal)) defaultFallback(meal) else fallbackTime)

/** An every-N-hours rule's first dose until the user picks another time. */
val INTERVAL_FIRST_DOSE: LocalTime = LocalTime.of(6, 0)

/** Switches the rule's type, carrying over its side and the dose times where the new type has them. */
fun ScheduleRule.withKind(kind: ScheduleKind, startDate: LocalDate): ScheduleRule {
    if (this.kind == kind) return this
    val times = when (this) {
        is ScheduleRule.DailyTimes -> times
        is ScheduleRule.Weekdays -> times
        else -> emptyList()
    }.ifEmpty { listOf(LocalTime.of(8, 0)) }
    val rule = when (kind) {
        ScheduleKind.DAILY -> ScheduleRule.DailyTimes(times)
        ScheduleKind.WEEKDAYS -> ScheduleRule.Weekdays(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), times,
        )
        // Starts early, so the day's doses fit before midnight. The dose count is left for the user to enter.
        ScheduleKind.INTERVAL -> ScheduleRule.EveryNHours(8, startDate.atTime(INTERVAL_FIRST_DOSE))
        ScheduleKind.AFTER_MEAL -> ScheduleRule.AfterMeal(MealType.BREAKFAST, 0, defaultFallback(MealType.BREAKFAST))
    }
    return rule.withSide(side)
}

/** [count] doses spread evenly from 6 AM to 10 PM, to the nearest half hour ("6 times a day"). */
fun spreadTimes(count: Int): List<LocalTime> {
    if (count <= 1) return listOf(LocalTime.of(6, 0))
    val first = 6 * 60
    val span = 16 * 60
    return (0 until count).map { i ->
        val minutes = first + span * i / (count - 1)
        LocalTime.of(0, 0).plusMinutes(((minutes + 15) / 30 * 30).toLong())
    }.distinct()
}

/** Replaces the rule at [index]. */
fun Schedule.withRule(index: Int, rule: ScheduleRule): Schedule =
    copy(rules = rules.toMutableList().also { it[index] = rule })

fun Schedule.withoutRule(index: Int): Schedule =
    copy(rules = rules.filterIndexed { i, _ -> i != index })

/**
 * Adds another rule. After an after-meal rule it suggests the next meal not yet covered
 * (breakfast, then lunch, then dinner), since "after breakfast and after dinner" is the common case.
 */
fun Schedule.withAddedRule(): Schedule {
    val usedMeals = rules.filterIsInstance<ScheduleRule.AfterMeal>().map { it.meal }.toSet()
    val nextMeal = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER).firstOrNull { it !in usedMeals }
    val rule = if (rules.lastOrNull() is ScheduleRule.AfterMeal && MealType.ANY !in usedMeals && nextMeal != null) {
        ScheduleRule.AfterMeal(nextMeal, 0, defaultFallback(nextMeal))
    } else {
        ScheduleRule.DailyTimes(listOf(LocalTime.of(20, 0)))
    }
    return copy(rules = rules + rule)
}

/** Changes the start date; interval rules' first doses move with it. */
fun Schedule.withStartDate(date: LocalDate): Schedule = copy(
    startDate = date,
    rules = rules.map { rule ->
        (rule as? ScheduleRule.EveryNHours)?.let { it.copy(anchor = date.atTime(it.anchor.toLocalTime())) } ?: rule
    },
)

/**
 * Fills in the dose count of every-N-hours rules saved before it existed, with the doses they ring now,
 * so editing an older medicine doesn't ask for it.
 */
fun List<Schedule>.withIntervalCountsFilled(): List<Schedule> = map { step ->
    step.copy(
        rules = step.rules.map { rule ->
            if (rule is ScheduleRule.EveryNHours && rule.dosesPerDay == null) rule.copy(dosesPerDay = rule.dailyTimes().size) else rule
        },
    )
}
