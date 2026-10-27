package com.apoorvdarshan.calorietracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.CoachGoalPlan
import com.apoorvdarshan.calorietracker.ui.coach.GoalPlanDetails
import com.apoorvdarshan.calorietracker.ui.components.FudGlassPrimaryButton
import com.apoorvdarshan.calorietracker.ui.components.FudGlassSurface
import com.apoorvdarshan.calorietracker.ui.components.InfoIconButton

/**
 * Under the goal options in Settings: the plan agreed with Coach (when there is one) and the
 * button that opens Coach to explain or adjust the goal.
 */
@Composable
internal fun CoachGoalSettingsSection(plan: CoachGoalPlan?, weightMetric: Boolean, onTalk: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (plan != null) {
            FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.cg_settings_plan_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(plan.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    GoalPlanDetails(plan, weightMetric)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FudGlassPrimaryButton(
                text = stringResource(if (plan == null) R.string.cg_settings_explain else R.string.cg_settings_adjust),
                onClick = onTalk,
                modifier = Modifier.weight(1f),
                height = 46.dp
            )
            InfoIconButton(
                title = stringResource(R.string.cg_onboarding_option),
                body = stringResource(R.string.cg_info_option)
            )
        }
    }
}
