package com.medtrack.ui.today

import com.medtrack.ui.common.SideBadge
import com.medtrack.ui.common.FormIcon
import com.medtrack.data.doseLineOn
import com.medtrack.data.title
import com.medtrack.data.coverPhoto
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medtrack.data.DoseStatus
import com.medtrack.data.displayName
import com.medtrack.ui.common.PhotoThumb
import com.medtrack.ui.common.TIME
import com.medtrack.ui.theme.medColors
import com.medtrack.ui.theme.tabular
import java.time.Instant
import java.time.ZoneId

/** The amber 4dp rule down the left edge of the group that is due now. */
@Composable
fun Modifier.dueRule(active: Boolean): Modifier {
    if (!active) return this
    val amber = MaterialTheme.medColors.amber
    return drawBehind { drawRect(amber, size = Size(4.dp.toPx(), size.height)) }
}

/** Header of a time group: the time (or meal hint) in title size with tabular figures. */
@Composable
fun GroupHeader(group: TodayGroup, dueNow: Boolean) {
    Text(
        group.label,
        style = MaterialTheme.typography.titleSmall.tabular(),
        color = MaterialTheme.medColors.ink,
        modifier = Modifier.fillMaxWidth().dueRule(dueNow).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DoseRow(
    item: TodayItem,
    now: Instant,
    zone: ZoneId,
    dueNow: Boolean,
    onTick: (Boolean) -> Unit,
    onMore: () -> Unit,
    enabled: Boolean = true,
) {
    // At large font sizes there is no room beside the name, so the status/actions go underneath it.
    val stacked = LocalDensity.current.fontScale >= 1.5f
    Row(
        Modifier.fillMaxWidth()
            .dueRule(dueNow)
            .combinedClickable(
                enabled = enabled,
                onClick = onMore,
                onClickLabel = "More actions",
                onLongClick = onMore,
                onLongClickLabel = "More actions",
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoThumb(item.medication.coverPhoto, item.medication.title, 40.dp, form = item.medication.form)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.medication.displayName(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                item.side?.let { SideBadge(it) }
            }
            detailLine(item, zone)?.let {
                Row(verticalAlignment = Alignment.Top) {
                    FormIcon(item.medication.form, Modifier.padding(top = 1.dp, end = 6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.medColors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (stacked) {
                Spacer(Modifier.height(4.dp))
                DoseCheck(item, now, zone, onTick, enabled = enabled)
            }
        }
        if (!stacked) {
            Spacer(Modifier.width(12.dp))
            DoseCheck(item, now, zone, onTick, stacked = true, enabled = enabled)
        }
    }
}

/** Dose text plus a short excerpt of the user's notes, and a snooze if there is one. */
private fun detailLine(item: TodayItem, zone: ZoneId): String? {
    val med = item.medication
    val note = med.notes?.trim()?.takeIf { it.isNotEmpty() }?.lineSequence()?.first()?.let {
        if (it.length > 40) it.take(39).trimEnd() + "…" else it
    }
    val snoozed = item.snoozedUntil?.takeIf { item.status == DoseStatus.PENDING }?.let { "Snoozed to ${TIME.format(it.atZone(zone))}" }
    return listOfNotNull(med.doseLineOn(item.dueAt.atZone(zone).toLocalDate()), note, snoozed).joinToString(" · ").ifEmpty { null }
}
