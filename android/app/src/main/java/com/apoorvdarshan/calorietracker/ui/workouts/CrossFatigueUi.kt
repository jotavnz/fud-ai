package com.apoorvdarshan.calorietracker.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.MachineMuscleGroup

private val Caution = Color(0xFFE0A100)

@Composable
private fun hitsText(hits: List<Pair<MachineMuscleGroup, Int>>): String =
    hits.map { (group, percent) -> stringResource(R.string.cross_hit, stringResource(group.titleRes), percent) }.joinToString(", ")

/** "Also works Biceps (30% recovered)" above a suggestion. */
@Composable
internal fun CrossFatigueBadge(hits: List<Pair<MachineMuscleGroup, Int>>) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Caution.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = Caution, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.cross_badge, hitsText(hits)), fontSize = 12.sp, color = workoutsColors().charcoal)
    }
}

/** The chosen group is still recovering: rested groups to train today instead. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BetterTodayCard(
    group: MachineMuscleGroup,
    percent: Int,
    alternatives: List<MachineMuscleGroup>,
    onOpenGroup: (MachineMuscleGroup) -> Unit
) {
    val colors = workoutsColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Caution.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.cross_better_title, stringResource(group.titleRes), percent),
            fontWeight = FontWeight.SemiBold,
            color = colors.charcoal
        )
        Text(stringResource(R.string.cross_better_body), fontSize = 13.sp, color = colors.mutedText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            alternatives.forEach { other ->
                Text(
                    stringResource(other.titleRes),
                    color = colors.onAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent)
                        .clickable { onOpenGroup(other) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/** Asks before adding an exercise that also loads a group under half recovered. */
@Composable
internal fun CrossFatigueDialog(
    exerciseName: String,
    hits: List<Pair<MachineMuscleGroup, Int>>,
    onAddAnyway: () -> Unit,
    onOtherOptions: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Warning, null) },
        title = { Text(stringResource(R.string.cross_dialog_title)) },
        text = { Text(stringResource(R.string.cross_dialog_body, exerciseName, hitsText(hits))) },
        confirmButton = { TextButton(onClick = onOtherOptions) { Text(stringResource(R.string.insight_other_options)) } },
        dismissButton = { TextButton(onClick = onAddAnyway) { Text(stringResource(R.string.insight_add_anyway)) } }
    )
}
