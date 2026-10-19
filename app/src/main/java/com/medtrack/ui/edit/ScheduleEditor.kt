package com.medtrack.ui.edit

import com.medtrack.schedule.DropSite
import com.medtrack.schedule.Side
import com.medtrack.schedule.question
import com.medtrack.schedule.withSide
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.HorizontalDivider
import com.medtrack.schedule.spreadTimes
import com.medtrack.schedule.lengthDays
import com.medtrack.schedule.withStepLength
import com.medtrack.schedule.withStep
import com.medtrack.schedule.withoutStep
import com.medtrack.schedule.withAddedStep
import com.medtrack.schedule.withFirstStart
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medtrack.schedule.MealType
import com.medtrack.schedule.Schedule
import com.medtrack.schedule.ScheduleKind
import com.medtrack.schedule.ScheduleRule
import com.medtrack.schedule.kind
import com.medtrack.schedule.mealName
import com.medtrack.schedule.describe
import com.medtrack.schedule.withAddedRule
import com.medtrack.schedule.tooManyDosesError
import com.medtrack.schedule.withKind
import com.medtrack.schedule.withMeal
import com.medtrack.schedule.withRule
import com.medtrack.schedule.withoutRule
import com.medtrack.ui.theme.LabelShape
import com.medtrack.ui.theme.medColors
import com.medtrack.ui.common.DATE
import com.medtrack.ui.common.DatePickDialog
import com.medtrack.ui.common.TIME
import com.medtrack.ui.common.TimePickDialog
import com.medtrack.ui.theme.tabular
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * The medication's schedule. Usually one step; a tapering course has several (6 times a day for 2 weeks,
 * then 4 times a day for 2 weeks…), each with its own length, dose and timings.
 */
@Composable
fun ScheduleEditor(steps: List<Schedule>, usualDose: String?, site: DropSite?, onChange: (List<Schedule>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (steps.size == 1) {
            val step = steps.single()
            StepRules(step, site) { onChange(listOf(it)) }
            DateRow("Start date", step.startDate, clearable = false) { it?.let { d -> onChange(steps.withFirstStart(d)) } }
            DaysField("For how many days (optional)", step.lengthDays, allowBlank = true) { onChange(steps.withStepLength(0, it)) }
            LastDay(step)
        } else {
            steps.indices.forEach { index -> StepBlock(index, steps, usualDose, site, onChange) }
        }
        OutlinedButton(onClick = { onChange(steps.withAddedStep()) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add a dose")
        }
        if (steps.size == 1) {
            Text(
                "For a dose that changes later, e.g. 6 times a day for 2 weeks, then 4 times a day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.medColors.inkMuted,
            )
        }
    }
}

@Composable
private fun StepBlock(index: Int, steps: List<Schedule>, usualDose: String?, site: DropSite?, onChange: (List<Schedule>) -> Unit) {
    val step = steps[index]
    val last = index == steps.lastIndex
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Dose ${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { onChange(steps.withoutStep(index)) }) { Icon(Icons.Default.Close, "Remove dose ${index + 1}") }
        }
        if (index == 0) {
            DateRow("Starts", step.startDate, clearable = false) { it?.let { d -> onChange(steps.withFirstStart(d)) } }
        } else {
            Text("Starts ${step.startDate.format(DATE)}", style = MaterialTheme.typography.bodyLarge)
        }
        DaysField(
            label = if (last) "For how many days (blank = until stopped)" else "For how many days",
            days = step.lengthDays,
            allowBlank = last,
        ) { onChange(steps.withStepLength(index, it)) }
        LastDay(step)
        OutlinedTextField(
            value = step.dose.orEmpty(),
            onValueChange = { onChange(steps.withStep(index, step.copy(dose = it.ifEmpty { null }))) },
            label = { Text("Amount (e.g. 2 puffs)") },
            placeholder = { Text(usualDose?.takeIf { it.isNotBlank() }?.let { "Usual: $it" } ?: "e.g. 2 puffs") },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        StepRules(step, site) { onChange(steps.withStep(index, it)) }
        HorizontalDivider(color = MaterialTheme.medColors.foil)
    }
}

/** A step's rules (e.g. after breakfast and after dinner). */
@Composable
private fun StepRules(schedule: Schedule, site: DropSite?, onChange: (Schedule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        schedule.rules.forEachIndexed { index, rule ->
            RuleBlock(
                rule = rule,
                startDate = schedule.startDate,
                site = site,
                onChange = { onChange(schedule.withRule(index, it)) },
                onRemove = if (schedule.rules.size > 1) ({ onChange(schedule.withoutRule(index)) }) else null,
            )
        }
        OutlinedButton(onClick = { onChange(schedule.withAddedRule()) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add another time or meal")
        }
    }
}

/** "Last day: Tue 20 Oct" under a length, so the user sees when the course ends without picking an end date. */
@Composable
private fun LastDay(step: Schedule) {
    step.endDate?.let {
        Text("Last day: ${it.format(DATE)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.medColors.inkMuted)
    }
}

@Composable
private fun DaysField(label: String, days: Int?, allowBlank: Boolean, onChange: (Int?) -> Unit) {
    var text by remember(days) { mutableStateOf(days?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit).take(3)
            val n = text.toIntOrNull()
            when {
                n != null && n >= 1 -> onChange(n)
                text.isEmpty() && allowBlank -> onChange(null)
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = fieldColors(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleBlock(rule: ScheduleRule, startDate: LocalDate, site: DropSite?, onChange: (ScheduleRule) -> Unit, onRemove: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth()
            .clip(LabelShape)
            .background(MaterialTheme.medColors.paperRaised)
            .border(1.dp, MaterialTheme.medColors.foil, LabelShape)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rule.describe(site), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(top = 8.dp))
            if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove ${rule.describe(site)}") }
        }
        Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleKind.entries.forEach { kind ->
                    FilterChip(
                        selected = rule.kind == kind,
                        onClick = { onChange(rule.withKind(kind, startDate)) },
                        label = { Text(kind.label) },
                    )
                }
            }
            when (val r = rule) {
                is ScheduleRule.DailyTimes -> TimesEditor(r.times) { onChange(r.copy(times = it)) }
                is ScheduleRule.Weekdays -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.values().forEach { day ->
                            FilterChip(
                                selected = day in r.days,
                                onClick = { onChange(r.copy(days = if (day in r.days) r.days - day else r.days + day)) },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) },
                            )
                        }
                    }
                    TimesEditor(r.times) { onChange(r.copy(times = it)) }
                }
                is ScheduleRule.EveryNHours -> {
                    NumberField("Every how many hours", r.intervalHours) { onChange(r.copy(intervalHours = it)) }
                    DaysField("How many doses a day", r.dosesPerDay, allowBlank = true) { onChange(r.copy(dosesPerDay = it)) }
                    TimeRow("First dose", r.anchor.toLocalTime()) { onChange(r.copy(anchor = startDate.atTime(it))) }
                    r.tooManyDosesError()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                }
                is ScheduleRule.AfterMeal -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MealType.entries.forEach { meal ->
                            FilterChip(
                                selected = r.meal == meal,
                                onClick = { onChange(r.withMeal(meal)) },
                                label = { Text(mealName(meal).replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                    NumberField("Minutes after the meal", r.delayMinutes) { onChange(r.copy(delayMinutes = it)) }
                    TimeRow("Remind anyway at", r.fallbackTime) { onChange(r.copy(fallbackTime = it)) }
                    Text(
                        "Reminds you after a meal logged in Health Connect, or at this time if no meal is logged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.medColors.inkMuted,
                    )
                }
            }
            if (site != null) SidePicker(site, rule.side) { onChange(rule.withSide(it)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimesEditor(times: List<LocalTime>, onChange: (List<LocalTime>) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        times.sorted().forEach { time ->
            InputChip(
                selected = false,
                onClick = { onChange(times - time) },
                label = { Text(time.format(TIME)) },
                trailingIcon = { Icon(Icons.Default.Close, "Remove ${time.format(TIME)}", Modifier.size(16.dp)) },
            )
        }
        AssistChip(
            onClick = { picking = true },
            label = { Text("Add time") },
            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
        )
    }
    Text("Or spread through the day (6 AM – 10 PM):", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.medColors.inkMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..6).forEach { n ->
            FilterChip(
                selected = times.sorted() == spreadTimes(n),
                onClick = { onChange(spreadTimes(n)) },
                label = { Text(if (n == 1) "Once" else "$n times") },
            )
        }
    }
    if (picking) {
        TimePickDialog(LocalTime.of(8, 0), onDismiss = { picking = false }) { time ->
            picking = false
            if (time !in times) onChange(times + time)
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit).take(3)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = fieldColors(),
        singleLine = true,
    )
}

@Composable
private fun TimeRow(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ")
        TextButton(onClick = { picking = true }) { Text(time.format(TIME), style = MaterialTheme.typography.titleMedium.tabular()) }
    }
    if (picking) TimePickDialog(time, onDismiss = { picking = false }) { picking = false; onChange(it) }
}

@Composable
private fun DateRow(label: String, date: LocalDate?, clearable: Boolean, onChange: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ")
        TextButton(onClick = { picking = true }) { Text(date?.format(DATE) ?: "None") }
        if (clearable && date != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
    }
    if (picking) DatePickDialog(date ?: LocalDate.now(), onDismiss = { picking = false }) { picking = false; onChange(it) }
}

/** Left / Right / Both for one block's doses; the chosen side is amber, like the badge on Today and the alarm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SidePicker(site: DropSite, side: Side?, onChange: (Side) -> Unit) {
    val colors = MaterialTheme.medColors
    Text(site.question(), style = MaterialTheme.typography.bodyLarge, color = colors.inkMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Side.entries.forEach { s ->
            FilterChip(
                selected = side == s,
                onClick = { onChange(s) },
                label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.amber, selectedLabelColor = colors.onAmber),
            )
        }
    }
}
