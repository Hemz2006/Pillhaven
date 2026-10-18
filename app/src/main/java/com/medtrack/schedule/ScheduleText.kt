package com.medtrack.schedule

import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Clock times everywhere in the app: "8:00 AM". */
val TIME_12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val HM = TIME_12
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun mealName(meal: MealType): String = when (meal) {
    MealType.BREAKFAST -> "breakfast"
    MealType.LUNCH -> "lunch"
    MealType.DINNER -> "dinner"
    MealType.ANY -> "any meal"
}

fun Schedule.describe(site: DropSite? = null): String {
    val base = rules.joinToString("; ") { it.describe(site) }
    return if (endDate != null) "$base, until ${endDate.format(DAY_MONTH)}" else base
}

/** The rule in words; with a [site] that has sides, followed by the side ("Daily at 8:00 AM · Left eye"). */
fun ScheduleRule.describe(site: DropSite? = null): String {
    val sideText = site?.takeIf { it.sided }?.let { s -> side?.let { " · " + DoseSide(s, it).label } }.orEmpty()
    return timing() + sideText
}

private fun ScheduleRule.timing(): String =
    when (val r = this) {
        is ScheduleRule.DailyTimes -> "Daily at ${r.times.sorted().joinToString(", ") { it.format(HM) }}"
        is ScheduleRule.Weekdays -> {
            val days = DayOfWeek.values().filter { it in r.days }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
            "$days at ${r.times.sorted().joinToString(", ") { it.format(HM) }}"
        }
        is ScheduleRule.EveryNHours -> "Every ${r.intervalHours} h: ${r.dailyTimes().joinToString(", ") { it.format(HM) }}"
        is ScheduleRule.AfterMeal -> {
            val delay = if (r.delayMinutes == 0) "Right" else "${r.delayMinutes} min"
            "$delay after ${mealName(r.meal)} (by ${r.fallbackTime.format(HM)})"
        }
    }

/**
 * An every-N-hours rule's times on each day: from the first dose, every N hours until midnight. Each day stands
 * alone, so a dose never spills past midnight into the next day (or into the next dose's dates).
 */
fun ScheduleRule.EveryNHours.dailyTimes(): List<java.time.LocalTime> {
    if (intervalHours <= 0) return emptyList()
    val first = anchor.toLocalTime()
    val minutesLeft = 24 * 60 - (first.hour * 60 + first.minute)
    return (0 until (minutesLeft + intervalHours * 60 - 1) / (intervalHours * 60)).map { first.plusHours(it.toLong() * intervalHours) }
}
