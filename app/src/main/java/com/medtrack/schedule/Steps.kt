package com.medtrack.schedule

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * A medication's schedule is a list of steps: e.g. 6 times a day for 2 weeks, then 4 times a day for 2 weeks.
 * Each step is a [Schedule] with its own dates and (optionally) its own dose. Steps run back to back:
 * every step but the last has an end date, and the next one starts the day after.
 */

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** The step running on [date], or null before the first step starts or after the last one ends. */
fun List<Schedule>.stepOn(date: LocalDate): Schedule? =
    firstOrNull { date >= it.startDate && (it.endDate == null || date <= it.endDate) }

val List<Schedule>.hasMealRule: Boolean get() = any { it.hasMealRule }

/** Number of days a step lasts, or null when it has no end. */
val Schedule.lengthDays: Int?
    get() = endDate?.let { ChronoUnit.DAYS.between(startDate, it).toInt() + 1 }

/** A user-facing message describing why the steps can't be saved, or null if they're valid. */
fun List<Schedule>.stepsValidationError(): String? {
    if (isEmpty()) return "Add at least one schedule"
    forEachIndexed { i, step ->
        val error = step.validationError() ?: return@forEachIndexed
        return if (size == 1) error else "Dose ${i + 1}: $error"
    }
    for (i in 0 until size - 1) {
        val end = this[i].endDate ?: return "Dose ${i + 1}: set for how many days"
        if (this[i + 1].startDate <= end) return "Dose ${i + 2} starts before dose ${i + 1} ends"
    }
    return null
}

/** One line per step: "Until 28 Oct: Daily at 8:00 AM…", then "From 29 Oct: …". */
fun List<Schedule>.describeSteps(site: DropSite? = null): String =
    if (size == 1) single().describe(site) else mapIndexed { i, step -> step.describeAsStep(i == lastIndex, site) }.joinToString("\n")

private fun Schedule.describeAsStep(last: Boolean, site: DropSite?): String {
    val base = rules.joinToString("; ") { it.describe(site) }
    val dose = dose?.takeIf { it.isNotBlank() }?.let { "$it, " }.orEmpty()
    val dates = when {
        last && endDate == null -> "from ${startDate.format(DAY_MONTH)}"
        else -> "${startDate.format(DAY_MONTH)} – ${endDate?.format(DAY_MONTH)}"
    }
    return "${dates.replaceFirstChar { it.uppercase() }}: $dose$base"
}

/**
 * The step that matters on [date] (running now, else the next to start, else the last), described in one line,
 * with "dose 2 of 3" when there are several.
 */
fun List<Schedule>.describeOn(date: LocalDate, site: DropSite? = null): String {
    if (size == 1) return single().describe(site)
    val index = indexOfFirst { it == stepOn(date) }.takeIf { it >= 0 }
        ?: indexOfFirst { it.startDate > date }.takeIf { it >= 0 }
        ?: lastIndex
    val step = this[index]
    val dose = step.dose?.takeIf { it.isNotBlank() }?.let { "$it, " }.orEmpty()
    val until = step.endDate?.let { ", until ${it.format(DAY_MONTH)}" }.orEmpty()
    return "Dose ${index + 1} of $size: $dose${step.rules.joinToString("; ") { it.describe(site) }}$until"
}

// Editing. Every change re-chains the later steps so they keep their lengths and stay back to back.

fun List<Schedule>.withStep(index: Int, step: Schedule): List<Schedule> =
    toMutableList().also { it[index] = step }.rechainedFrom(index)

/** Sets how many days step [index] lasts; null means it runs until stopped (last step only). */
fun List<Schedule>.withStepLength(index: Int, days: Int?): List<Schedule> =
    withStep(index, this[index].copy(endDate = days?.let { this[index].startDate.plusDays(it - 1L) }))

/** Adds a step after the last one, copying its timings: the usual next step is the same times with a different dose. */
fun List<Schedule>.withAddedStep(): List<Schedule> {
    val last = last()
    val ended = last.copy(endDate = last.endDate ?: last.startDate.plusDays(13))
    val next = ended.withStartDate(ended.endDate!!.plusDays(1)).copy(endDate = null)
    return dropLast(1) + ended + next
}

fun List<Schedule>.withoutStep(index: Int): List<Schedule> {
    if (size == 1) return this
    val rest = filterIndexed { i, _ -> i != index }
    // Removing the first step makes the next one start where the first did.
    return if (index == 0) rest.withFirstStart(this[0].startDate) else rest.rechainedFrom(index - 1)
}

/** Moves the whole course to start on [date], keeping each step's length. */
fun List<Schedule>.withFirstStart(date: LocalDate): List<Schedule> {
    val first = this[0]
    val moved = first.withStartDate(date).copy(endDate = first.lengthDays?.let { date.plusDays(it - 1L) })
    return withStep(0, moved)
}

private fun List<Schedule>.rechainedFrom(index: Int): List<Schedule> {
    val out = toMutableList()
    for (i in index + 1 until out.size) {
        val prevEnd = out[i - 1].endDate ?: break
        val length = out[i].lengthDays
        val start = prevEnd.plusDays(1)
        out[i] = out[i].withStartDate(start).copy(endDate = length?.let { start.plusDays(it - 1L) })
    }
    return out
}
