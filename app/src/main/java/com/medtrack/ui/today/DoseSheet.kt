package com.medtrack.ui.today

import com.medtrack.ui.common.SideBadge
import com.medtrack.data.doseLineOn
import com.medtrack.data.title
import com.medtrack.data.coverPhoto
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medtrack.data.DoseStatus
import com.medtrack.data.displayName
import com.medtrack.ui.common.PhotoThumb
import com.medtrack.ui.common.TIME
import com.medtrack.ui.theme.medColors
import com.medtrack.ui.theme.tabular
import java.time.ZoneId

/** Everything about one of today's doses, with Take, Skip and Open details. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DoseSheet(
    item: TodayItem,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val med = item.medication
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row {
                PhotoThumb(med.coverPhoto, med.title, 96.dp, form = med.form)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(med.displayName(), style = MaterialTheme.typography.titleLarge)
                    item.side?.let { SideBadge(it) }
                    med.doseLineOn(item.dueAt.atZone(zone).toLocalDate())?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.medColors.inkMuted)
                    }
                    val time = TIME.format(item.dueAt.atZone(zone))
                    Text(
                        item.mealHint?.let { "$it, by $time" } ?: "Due $time",
                        style = MaterialTheme.typography.titleMedium.tabular(),
                    )
                }
            }
            med.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.status != DoseStatus.TAKEN) {
                    Button(onClick = onTake, modifier = Modifier.heightIn(min = 48.dp)) { Text("Take") }
                }
                if (item.status != DoseStatus.SKIPPED) {
                    OutlinedButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip") }
                }
                TextButton(onClick = onOpenDetails, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open details") }
            }
        }
    }
}
