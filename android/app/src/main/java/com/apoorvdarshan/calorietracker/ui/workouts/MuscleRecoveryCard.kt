package com.apoorvdarshan.calorietracker.ui.workouts

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.MachineMuscleGroup
import com.apoorvdarshan.calorietracker.models.MuscleReadiness
import com.apoorvdarshan.calorietracker.ui.components.InfoIconButton

private val Ready = Color(0xFF2E9E5B)
private val Partial = Color(0xFFE0A100)
private val Tired = Color(0xFFD9483B)

private fun colorFor(r: MuscleReadiness): Color = when {
    r.percent >= 85 -> Ready
    r.percent >= 50 -> Partial
    else -> Tired
}

/**
 * Training's recovery map: every muscle group colored by how recovered it is, with the hours
 * left. Tapping a group explains what to do while it recovers and can open its exercises.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MuscleRecoveryCard(readiness: List<MuscleReadiness>, onOpenGroup: (MachineMuscleGroup) -> Unit) {
    val colors = workoutsColors()
    var selected by remember { mutableStateOf<MuscleReadiness?>(null) }
    var showAll by remember { mutableStateOf(false) }
    // The two most recently worked (least recovered) groups; the rest behind "See all".
    val sorted = readiness.sortedBy { it.percent }
    val shown = sorted.take(CompactCount)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.panel.copy(alpha = 0.45f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.recovery_title).uppercase(),
                color = colors.mutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            InfoIconButton(
                title = stringResource(R.string.recovery_title),
                body = stringResource(R.string.recovery_info),
                tint = colors.mutedText
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { r -> RecoveryChip(r) { selected = r } }
            if (sorted.size > shown.size) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.panel.copy(alpha = 0.8f))
                        .clickable { showAll = true }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.recovery_see_all, sorted.size),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = com.apoorvdarshan.calorietracker.ui.theme.AppColors.Calorie
                    )
                }
            }
        }
    }

    if (showAll) {
        AlertDialog(
            onDismissRequest = { showAll = false },
            title = { Text(stringResource(R.string.recovery_title)) },
            text = {
                Column(
                    Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.recovery_info), fontSize = 13.sp, color = colors.mutedText)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sorted.forEach { r ->
                            RecoveryChip(r) {
                                showAll = false
                                selected = r
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAll = false }) { Text(stringResource(R.string.info_button_ok)) } }
        )
    }

    selected?.let { r ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(stringResource(r.group.titleRes)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.recovery_percent, r.percent), fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { r.percent / 100f },
                        color = colorFor(r),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (r.ready) stringResource(R.string.recovery_ready_long)
                        else stringResource(R.string.recovery_ready_in, r.readyInHours)
                    )
                    Text(stringResource(R.string.recovery_weekly_sets, r.weeklySets), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text(stringResource(tipFor(r.group)))
                    Text(stringResource(R.string.recovery_tip_general), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    onOpenGroup(r.group)
                }) { Text(stringResource(R.string.insight_action_machines)) }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.info_button_ok)) } }
        )
    }
}

private const val CompactCount = 2

@Composable
private fun RecoveryChip(r: MuscleReadiness, onClick: () -> Unit) {
    val colors = workoutsColors()
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorFor(r).copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colorFor(r)))
        Text(stringResource(r.group.titleRes), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.charcoal)
        Text(
            if (r.ready) stringResource(R.string.recovery_ready) else stringResource(R.string.recovery_hours, r.readyInHours),
            fontSize = 12.sp,
            color = colors.mutedText
        )
    }
}

private fun tipFor(group: MachineMuscleGroup): Int = when (group) {
    MachineMuscleGroup.LEGS, MachineMuscleGroup.HAMSTRINGS, MachineMuscleGroup.GLUTES -> R.string.recovery_tip_legs
    MachineMuscleGroup.CHEST, MachineMuscleGroup.BACK, MachineMuscleGroup.SHOULDERS -> R.string.recovery_tip_upper
    MachineMuscleGroup.ABS -> R.string.recovery_tip_core
    else -> R.string.recovery_tip_small
}
