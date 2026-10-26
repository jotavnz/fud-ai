package com.apoorvdarshan.calorietracker.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.data.ExerciseLocalization
import com.apoorvdarshan.calorietracker.models.GroupFatigue
import com.apoorvdarshan.calorietracker.models.MachineMuscleGroup
import com.apoorvdarshan.calorietracker.models.ProgressionHint
import com.apoorvdarshan.calorietracker.models.TrainingInsight
import com.apoorvdarshan.calorietracker.models.WorkoutWeightUnit
import java.text.NumberFormat

/** A kilogram load shown in the user's unit with locale digits ("42,5 kg", "95 lb"). */
@Composable
internal fun formatLoad(kg: Double, unit: WorkoutWeightUnit): String {
    val locale = LocalConfiguration.current.locales[0]
    val value = if (unit == WorkoutWeightUnit.KG) kg else kg * 2.204_622_621_8
    val number = NumberFormat.getInstance(locale).apply { maximumFractionDigits = 1 }.format(value)
    return if (unit == WorkoutWeightUnit.KG) {
        stringResource(R.string.insight_unit_kg, number)
    } else {
        stringResource(R.string.insight_unit_lb, number)
    }
}

/** Group name for use mid-sentence ("sin entrenar piernas", "no legs in…"). */
@Composable
private fun groupInSentence(group: MachineMuscleGroup): String =
    stringResource(group.titleRes).lowercase(LocalConfiguration.current.locales[0])

@Composable
internal fun insightTitle(insight: TrainingInsight): String = when (insight) {
    is TrainingInsight.Recovery -> stringResource(R.string.insight_recovery_title, stringResource(insight.group.titleRes))
    is TrainingInsight.Overuse -> stringResource(R.string.insight_overuse_title, stringResource(insight.group.titleRes))
    is TrainingInsight.Plateau -> stringResource(
        R.string.insight_plateau_title,
        ExerciseLocalization.name(insight.exerciseId, insight.exerciseName)
    )
    is TrainingInsight.Imbalance -> stringResource(R.string.insight_imbalance_title)
    is TrainingInsight.Neglected -> stringResource(R.string.insight_neglected_title, stringResource(insight.group.titleRes))
}

@Composable
internal fun insightBody(insight: TrainingInsight, unit: WorkoutWeightUnit): String = when (insight) {
    is TrainingInsight.Recovery -> stringResource(
        if (insight.daysAgo <= 1) R.string.insight_recovery_body_yesterday else R.string.insight_recovery_body_two_days,
        insight.sets
    )
    is TrainingInsight.Overuse -> stringResource(R.string.insight_overuse_body, insight.weeklySets, insight.trainedDays)
    is TrainingInsight.Plateau -> stringResource(R.string.insight_plateau_body, formatLoad(insight.loadKg, unit), insight.sessions)
    is TrainingInsight.Imbalance -> stringResource(
        if (insight.heavySide == MachineMuscleGroup.CHEST) R.string.insight_imbalance_body_push else R.string.insight_imbalance_body_pull,
        insight.heavySets,
        insight.lightSets
    )
    is TrainingInsight.Neglected -> insight.daysSince
        ?.let { stringResource(R.string.insight_neglected_body, groupInSentence(insight.group), it) }
        ?: stringResource(R.string.insight_neglected_body_never, groupInSentence(insight.group))
}

private fun insightIcon(insight: TrainingInsight): ImageVector = when (insight) {
    is TrainingInsight.Recovery -> Icons.Filled.Bedtime
    is TrainingInsight.Overuse -> Icons.Filled.Warning
    is TrainingInsight.Plateau -> Icons.AutoMirrored.Filled.TrendingFlat
    is TrainingInsight.Imbalance -> Icons.Filled.Balance
    is TrainingInsight.Neglected -> Icons.Filled.EventBusy
}

/** Diary card for one tip, with a shortcut to the machine coach for the suggested group. */
@Composable
internal fun TrainingInsightCard(
    insight: TrainingInsight,
    unit: WorkoutWeightUnit,
    onOpenGroup: (MachineMuscleGroup) -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = workoutsColors()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.accent.copy(alpha = 0.08f))
            .border(0.6.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(14.dp)
            .testTag("workouts.insight"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(insightIcon(insight), null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Text(
                insightTitle(insight),
                color = colors.charcoal,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.insight_dismiss),
                    tint = colors.mutedText,
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(5.dp)
                )
            }
        }
        Text(insightBody(insight, unit), color = colors.charcoal.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp)
        if (insight is TrainingInsight.Recovery) {
            GroupChoiceRow(groups = insight.restedGroups, onPick = onOpenGroup)
        } else {
            InsightAction(text = stringResource(R.string.insight_action_machines), onClick = { onOpenGroup(insight.group) })
        }
    }
}

/** Rested groups the user can jump to instead. */
@Composable
internal fun GroupChoiceRow(groups: List<MachineMuscleGroup>, onPick: (MachineMuscleGroup) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { group -> InsightAction(text = stringResource(group.titleRes), onClick = { onPick(group) }) }
    }
}

@Composable
private fun InsightAction(text: String, onClick: () -> Unit) {
    val colors = workoutsColors()
    Box(
        Modifier
            .clip(CircleShape)
            .background(colors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = colors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Builds the recovery tip for [group] when it is still recovering. */
internal fun recoveryInsight(
    fatigue: GroupFatigue?,
    restedGroups: List<MachineMuscleGroup>
): TrainingInsight.Recovery? = fatigue?.let {
    TrainingInsight.Recovery(it.group, it.daysAgo, it.sets, restedGroups)
}

/** "Last time: 40 kg × 10 · Try 42.5 kg" under a suggested machine. */
@Composable
internal fun ProgressionLine(hint: ProgressionHint, unit: WorkoutWeightUnit) {
    val colors = workoutsColors()
    val last = formatLoad(hint.lastLoadKg, unit)
    Text(
        if (hint.increase) {
            stringResource(R.string.insight_progression_up, last, hint.lastReps, formatLoad(hint.suggestedLoadKg, unit))
        } else {
            stringResource(R.string.insight_progression_same, last, hint.lastReps)
        },
        color = colors.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

/** Confirms adding a machine for a group the insights flag as tired, overused or stalled. */
@Composable
internal fun InsightWarningDialog(
    insight: TrainingInsight,
    unit: WorkoutWeightUnit,
    onAddAnyway: () -> Unit,
    onOtherOptions: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(insightIcon(insight), null) },
        title = { Text(insightTitle(insight)) },
        // The recovery body ends with "Today, try:" for the group chips, which the dialog does not show.
        text = {
            val body = insightBody(insight, unit)
            Text(if (insight is TrainingInsight.Recovery) body.substringBeforeLast(". ") + "." else body)
        },
        confirmButton = { TextButton(onClick = onOtherOptions) { Text(stringResource(R.string.insight_other_options)) } },
        dismissButton = { TextButton(onClick = onAddAnyway) { Text(stringResource(R.string.insight_add_anyway)) } }
    )
}
