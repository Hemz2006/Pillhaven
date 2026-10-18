package com.medtrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medtrack.schedule.DoseSide
import com.medtrack.schedule.Side
import com.medtrack.ui.theme.LabelShape
import com.medtrack.ui.theme.medColors

/** "RIGHT EYE" in warning amber, so the side of a drops dose can't be missed. A label shape: capsules mean doses. */
@Composable
fun SideBadge(side: DoseSide, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.medColors
    Text(
        side.badge,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = colors.onAmber,
        modifier = modifier
            .background(colors.amber, LabelShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = side.label },
    )
}

/**
 * A Left / Right pair with the dosed side filled (both for Both), as the patient sees it: a caregiver facing the
 * patient would otherwise mirror it. Filled boxes use the content colour, with [onLit] for their labels.
 */
@Composable
fun SidePicture(side: DoseSide, onLit: Color, modifier: Modifier = Modifier) {
    val content = LocalContentColor.current
    Column(
        modifier.clearAndSetSemantics { contentDescription = side.patientLabel },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Side.LEFT to "Left", Side.RIGHT to "Right").forEach { (s, label) ->
                val lit = side.side == s || side.side == Side.BOTH
                Box(
                    Modifier.widthIn(min = 96.dp).heightIn(min = 48.dp)
                        .then(if (lit) Modifier.background(content, LabelShape) else Modifier.border(2.dp, content.copy(alpha = 0.5f), LabelShape))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
                        color = if (lit) onLit else content.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Text("As the patient sees it", style = MaterialTheme.typography.bodyMedium)
    }
}
