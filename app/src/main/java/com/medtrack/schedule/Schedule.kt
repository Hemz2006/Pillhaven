@file:UseSerializers(
    LocalTimeSerializer::class,
    LocalDateSerializer::class,
    LocalDateTimeSerializer::class,
    DayOfWeekSerializer::class,
)

package com.medtrack.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Serializable
enum class MealType { BREAKFAST, LUNCH, DINNER, ANY }

@Serializable
sealed interface ScheduleRule {
    /** Which eye, ear or nostril this block's doses are for; only used for drops that have sides. */
    val side: Side?

    @Serializable @SerialName("daily")
    data class DailyTimes(val times: List<LocalTime>, override val side: Side? = null) : ScheduleRule

    @Serializable @SerialName("weekdays")
    data class Weekdays(val days: Set<DayOfWeek>, val times: List<LocalTime>, override val side: Side? = null) : ScheduleRule

    /**
     * [dosesPerDay] doses every [intervalHours] from [anchor]'s clock time, each day. Null only in schedules saved
     * before the count existed: those keep every dose that fits before midnight.
     */
    @Serializable @SerialName("interval")
    data class EveryNHours(
        val intervalHours: Int,
        val anchor: LocalDateTime,
        override val side: Side? = null,
        val dosesPerDay: Int? = null,
    ) : ScheduleRule

    /** Fires [delayMinutes] after a matching meal; fires at [fallbackTime] if no meal was logged. */
    @Serializable @SerialName("meal")
    data class AfterMeal(val meal: MealType, val delayMinutes: Int, val fallbackTime: LocalTime, override val side: Side? = null) : ScheduleRule
}

fun ScheduleRule.withSide(side: Side?): ScheduleRule = when (this) {
    is ScheduleRule.DailyTimes -> copy(side = side)
    is ScheduleRule.Weekdays -> copy(side = side)
    is ScheduleRule.EveryNHours -> copy(side = side)
    is ScheduleRule.AfterMeal -> copy(side = side)
}

/** One or more rules (e.g. after breakfast and after dinner) sharing a course's start and end dates. */
@Serializable
data class Schedule(
    val rules: List<ScheduleRule>,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    /** This step's dose ("2 puffs"); null means the medication's usual dose. */
    val dose: String? = null,
) {
    constructor(rule: ScheduleRule, startDate: LocalDate, endDate: LocalDate? = null) : this(listOf(rule), startDate, endDate)
}

/**
 * Identifies a rule's doses in the dose log where that matters: an after-meal rule's day is "handled"
 * once its meal or fallback dose is logged, independently of the medication's other rules.
 */
val ScheduleRule.slot: String?
    get() = (this as? ScheduleRule.AfterMeal)?.let { "meal:${it.meal}" }

val Schedule.hasMealRule: Boolean get() = rules.any { it is ScheduleRule.AfterMeal }

object ScheduleJson {
    private val json = Json { ignoreUnknownKeys = true }
    private val steps = ListSerializer(Schedule.serializer())
    /** One v1/v2 schedule, as stored before steps; only the v1→v2 migration reads these. */
    fun decode(text: String): Schedule = json.decodeFromString(Schedule.serializer(), text)
    fun encodeSteps(list: List<Schedule>): String = json.encodeToString(steps, list)
    fun decodeSteps(text: String): List<Schedule> = json.decodeFromString(steps, text)
}

/** A user-facing message describing why the schedule can't be saved, or null if it's valid. */
fun Schedule.validationError(): String? {
    if (endDate != null && endDate < startDate) return "End date is before start date"
    if (rules.isEmpty()) return "Add at least one schedule"
    val meals = rules.filterIsInstance<ScheduleRule.AfterMeal>().map { it.meal }
    if (meals.size != meals.distinct().size) return "Each meal can only have one after-meal schedule"
    if (MealType.ANY in meals && meals.size > 1) return "\"Any meal\" can't be combined with other after-meal schedules"
    return rules.firstNotNullOfOrNull { it.validationError() }
}

private fun ScheduleRule.validationError(): String? {
    return when (val r = this) {
        is ScheduleRule.DailyTimes -> if (r.times.isEmpty()) "Add at least one time" else null
        is ScheduleRule.Weekdays -> when {
            r.days.isEmpty() -> "Pick at least one day"
            r.times.isEmpty() -> "Add at least one time"
            else -> null
        }
        is ScheduleRule.EveryNHours -> when {
            r.intervalHours !in 1..24 -> "Interval must be 1–24 hours"
            r.dosesPerDay == null || r.dosesPerDay < 1 -> "Enter how many doses a day"
            else -> r.tooManyDosesError()
        }
        is ScheduleRule.AfterMeal -> if (r.delayMinutes !in 0..240) "Delay must be 0–240 minutes" else null
    }
}
