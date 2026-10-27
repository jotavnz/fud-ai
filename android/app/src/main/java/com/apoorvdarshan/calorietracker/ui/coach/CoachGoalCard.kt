package com.apoorvdarshan.calorietracker.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.CoachGoalPlan
import com.apoorvdarshan.calorietracker.models.CoachGoalProposal
import com.apoorvdarshan.calorietracker.models.TrainingPlace
import java.util.Locale

/**
 * The card under a Coach reply that proposed a goal plan. Nothing changes until the user taps
 * Apply, which sets the weight goal, the training goal and the plan Coach remembers.
 */
@Composable
internal fun CoachGoalCard(
    proposal: CoachGoalProposal,
    busy: Boolean,
    weightMetric: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val plan = proposal.plan

    Column(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .background(accent.copy(alpha = 0.05f))
            .border(0.6.dp, accent.copy(alpha = 0.22f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Flag, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.cg_card_title), color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(plan.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        GoalPlanDetails(plan, weightMetric)

        when (proposal.status) {
            CoachGoalProposal.Status.PENDING -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onApply, enabled = !busy) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.cg_action_apply))
                }
                OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cg_action_dismiss)) }
            }
            CoachGoalProposal.Status.APPLIED -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.cg_status_applied), color = onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            CoachGoalProposal.Status.DISMISSED ->
                Text(stringResource(R.string.cg_status_dismissed), color = onSurface.copy(alpha = 0.55f), fontSize = 13.sp)
        }
    }
}

/** The plan's settings as label / value rows; Settings shows the same rows for the saved plan. */
@Composable
internal fun GoalPlanDetails(plan: CoachGoalPlan, weightMetric: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val weight = stringResource(plan.weightGoal.displayNameRes) + (plan.weeklyChangeKg?.let { " · " + paceText(it, weightMetric) } ?: "")
        DetailRow(stringResource(R.string.cg_label_weight_goal), weight)
        DetailRow(stringResource(R.string.cg_label_training_goal), stringResource(plan.training.titleRes))
        if (plan.focusAreas.isNotEmpty()) DetailRow(stringResource(R.string.cg_label_focus), plan.focusAreas.joinToString(", "))
        plan.trainingDaysPerWeek?.let { DetailRow(stringResource(R.string.cg_label_days), stringResource(R.string.cg_days_format, it)) }
        if (plan.notes.isNotEmpty()) DetailRow(stringResource(R.string.cg_label_notes), plan.notes.joinToString("\n") { "• $it" })
        plan.place?.let { place ->
            val where = stringResource(if (place == TrainingPlace.GYM) R.string.place_gym else R.string.place_home)
            DetailRow(stringResource(R.string.place_label), if (plan.homeEquipment.isEmpty()) where else "$where (${plan.homeEquipment.joinToString(", ")})")
        }
    }
}

@Composable
private fun paceText(weeklyKg: Double, weightMetric: Boolean): String =
    if (weightMetric) stringResource(R.string.cg_pace_kg, String.format(Locale.getDefault(), "%.2f", weeklyKg))
    else stringResource(R.string.cg_pace_lb, String.format(Locale.getDefault(), "%.1f", weeklyKg * 2.20462))

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
