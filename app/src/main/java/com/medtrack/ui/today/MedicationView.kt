package com.medtrack.ui.today

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medtrack.data.DoseStatus
import com.medtrack.data.coverPhoto
import com.medtrack.data.displayName
import com.medtrack.data.doseLineOn
import com.medtrack.ui.common.FormIcon
import com.medtrack.ui.common.PhotoThumb
import com.medtrack.ui.common.TIME
import com.medtrack.ui.theme.LabelShape
import com.medtrack.ui.theme.medColors
import com.medtrack.ui.theme.tabular
import java.time.Instant
import java.time.ZoneId

/**
 * "By medication": one block per medicine — photo, name and dose — with the day's doses underneath as
 * time tiles. Tap a tile to tick it off (tap again to untick); press and hold for more (skip, details).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationDayCard(
    day: MedDay,
    now: Instant,
    zone: ZoneId,
    onTick: (TodayItem, Boolean) -> Unit,
    onMore: (TodayItem) -> Unit,
    enabled: Boolean = true,
) {
    val med = day.medication
    val date = day.items.first().dueAt.atZone(zone).toLocalDate()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoThumb(med.coverPhoto, med.displayName(), 40.dp, form = med.form)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(med.displayName(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                med.doseLineOn(date)?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FormIcon(med.form, Modifier.padding(end = 6.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.medColors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            day.items.forEach { item ->
                DoseTile(item, now, zone, onTick = { onTick(item, it) }, onMore = { onMore(item) }, enabled = enabled)
            }
        }
    }
}

/** One dose as a rectangle: its time, filled green with a tick once taken. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DoseTile(item: TodayItem, now: Instant, zone: ZoneId, onTick: (Boolean) -> Unit, onMore: () -> Unit, enabled: Boolean) {
    val colors = MaterialTheme.medColors
    val taken = item.status == DoseStatus.TAKEN
    val time = TIME.format(item.dueAt.atZone(zone))
    val (note, edge: Color) = when {
        taken -> null to colors.taken
        item.status == DoseStatus.MISSED -> "Missed" to colors.missed
        item.status == DoseStatus.SKIPPED -> "Skipped" to colors.foil
        item.isDueNow(now) -> "Due now" to colors.amber
        else -> null to colors.foil
    }
    val spoken = when {
        taken -> "taken"
        note != null -> note.lowercase()
        else -> "not taken yet"
    }
    Column(
        Modifier
            .widthIn(min = 76.dp)
            .heightIn(min = 44.dp)
            .clip(LabelShape)
            .then(if (taken) Modifier.background(colors.taken) else Modifier.border(BorderStroke(if (note == null) 1.dp else 2.dp, edge), LabelShape))
            .combinedClickable(enabled = enabled, onClick = { onTick(!taken) }, onLongClick = onMore)
            .clearAndSetSemantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(taken)
                contentDescription = listOfNotNull(item.medication.displayName(), item.side?.label, time).joinToString(" ")
                stateDescription = spoken
                if (enabled) onLongClick("More actions") { onMore(); true } else disabled()
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (taken) {
                Icon(Icons.Default.Check, contentDescription = null, tint = colors.paper, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(
                time,
                style = MaterialTheme.typography.titleSmall.tabular(),
                color = when {
                    taken -> colors.paper
                    item.status == DoseStatus.SKIPPED -> colors.inkMuted
                    else -> colors.ink
                },
            )
        }
        item.side?.let {
            Text(it.short, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (taken) colors.paper else colors.amber)
        }
        val sub = if (taken) item.actedAt?.let { "at " + TIME.format(it.atZone(zone)) } else note
        sub?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    taken -> colors.paper
                    item.status == DoseStatus.MISSED -> colors.missed
                    else -> colors.inkMuted
                },
            )
        }
    }
}
