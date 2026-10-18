package com.medtrack.schedule

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Which side a dose of eye, ear or nose drops is for: always the patient's left or right. */
@Serializable
enum class Side { LEFT, RIGHT, BOTH }

/** What drops are for. Eyes, ears and nostrils come in pairs, so their doses need a [Side]. */
enum class DropSite(val label: String, val sided: Boolean, internal val one: String, internal val pair: String) {
    EYE("Eye", true, "eye", "eyes"),
    EAR("Ear", true, "ear", "ears"),
    NOSE("Nose", true, "nostril", "nostrils"),
    OTHER("Mouth or other", false, "", ""),
}

/** The editor's question for a block's side: "Which eye (patient's)". */
fun DropSite.question(): String = "Which $one (patient's)"

/** Where one dose goes: "Left eye", "Both nostrils". */
data class DoseSide(val site: DropSite, val side: Side) {
    val label: String
        get() = when (side) {
            Side.LEFT -> "Left ${site.one}"
            Side.RIGHT -> "Right ${site.one}"
            Side.BOTH -> "Both ${site.pair}"
        }

    /** For the amber badge in lists: "RIGHT EYE". */
    val badge: String get() = label.uppercase()

    /** For the alarm, where a caregiver facing the patient could otherwise mirror it: "Patient's right eye". */
    val patientLabel: String get() = if (side == Side.BOTH) label else "Patient's ${label.replaceFirstChar { it.lowercase() }}"

    /** For small tiles: "Left", "Right", "Both". */
    val short: String get() = side.name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Why sided drops' steps can't be saved, or null: every block needs a side, and no two blocks may ring at the same
 * time for different sides (a medicine has one dose per time, so one side would silently win). Null when [site]
 * has no sides.
 */
fun List<Schedule>.sidesValidationError(site: DropSite?): String? {
    if (site == null || !site.sided) return null
    forEachIndexed { i, step ->
        val error = step.sidesError(site) ?: return@forEachIndexed
        return if (size == 1) error else "Dose ${i + 1}: ${error.replaceFirstChar { it.lowercase() }}"
    }
    return null
}

private fun Schedule.sidesError(site: DropSite): String? {
    if (rules.any { it.side == null }) return "Choose which ${site.one}"
    // A week covers every pattern: weekdays repeat weekly and every-N-hours restarts each day.
    val from = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1)
    val to = from.plus(Duration.ofDays(7))
    val sidesAt = sortedMapOf<Instant, MutableSet<Side>>()
    for (rule in rules) for (due in dosesBetween(this, rule, from, to, ZoneOffset.UTC)) {
        sidesAt.getOrPut(due) { mutableSetOf() } += rule.side!!
    }
    val clash = sidesAt.entries.firstOrNull { it.value.size > 1 } ?: return null
    return "${TIME_12.format(clash.key.atZone(ZoneOffset.UTC))} is set for two sides – choose Both instead"
}
