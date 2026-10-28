package com.apoorvdarshan.calorietracker.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.Gender
import com.apoorvdarshan.calorietracker.models.MachineMuscleGroup
import com.apoorvdarshan.calorietracker.models.MuscleReadiness
import com.apoorvdarshan.calorietracker.ui.components.FudGlassSurface
import com.apoorvdarshan.calorietracker.ui.components.InfoIconButton
import com.apoorvdarshan.calorietracker.ui.navigation.FudAIRoutes
import com.fittribe.musclemap.BodyGender
import com.fittribe.musclemap.BodySide
import com.fittribe.musclemap.BodyView
import com.fittribe.musclemap.BodyViewStyle
import com.fittribe.musclemap.Muscle
import com.fittribe.musclemap.MuscleHighlight
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Locked = Color(0xFFD9483B)
private val Recovering = Color(0xFFE0A100)
private val Ready = Color(0xFF2E9E5B)
private val Untrained = Color(0xFFC7C7C7)

/** Weekly sets that count as the full weekly target for a group (10–20 builds muscle). */
private const val WEEKLY_TARGET_SETS = 15

/** The figure's muscles for each group the app tracks; forearms, neck and feet stay neutral. */
private val groupMuscles: Map<MachineMuscleGroup, List<Muscle>> = mapOf(
    MachineMuscleGroup.CHEST to listOf(Muscle.Chest, Muscle.UpperChest, Muscle.LowerChest),
    MachineMuscleGroup.BACK to listOf(Muscle.UpperBack, Muscle.LowerBack, Muscle.Trapezius, Muscle.Rhomboids, Muscle.UpperTrapezius, Muscle.LowerTrapezius),
    MachineMuscleGroup.SHOULDERS to listOf(Muscle.Deltoids, Muscle.FrontDeltoid, Muscle.RearDeltoid, Muscle.RotatorCuff),
    MachineMuscleGroup.BICEPS to listOf(Muscle.Biceps),
    MachineMuscleGroup.TRICEPS to listOf(Muscle.Triceps),
    MachineMuscleGroup.LEGS to listOf(Muscle.Quadriceps, Muscle.InnerQuad, Muscle.OuterQuad, Muscle.Adductors, Muscle.HipFlexors),
    MachineMuscleGroup.HAMSTRINGS to listOf(Muscle.Hamstring),
    MachineMuscleGroup.GLUTES to listOf(Muscle.Gluteal),
    MachineMuscleGroup.CALVES to listOf(Muscle.Calves, Muscle.Tibialis),
    MachineMuscleGroup.ABS to listOf(Muscle.Abs, Muscle.Obliques, Muscle.Serratus, Muscle.UpperAbs, Muscle.LowerAbs)
)

private fun groupOf(muscle: Muscle): MachineMuscleGroup? =
    groupMuscles.entries.firstOrNull { muscle in it.value || muscle.parentGroup in it.value }?.key

private enum class BodyMapMode { RECOVERY, WEEK }

private fun recoveryColor(r: MuscleReadiness): Color = when {
    r.lockedUntil != null || r.percent < 50 -> Locked
    r.percent < 85 -> Recovering
    r.weeklySets == 0 -> Untrained
    else -> Ready
}

private fun weekColor(r: MuscleReadiness): Color {
    if (r.weeklySets == 0) return Untrained
    val share = (r.weeklySets / WEEKLY_TARGET_SETS.toFloat()).coerceIn(0f, 1.4f)
    return when {
        share > 1.3f -> Locked
        share >= 0.66f -> Ready
        else -> Recovering
    }
}

/**
 * Progress › My body: the whole body as a heat map, front and back. In Recovery mode each muscle
 * is red (just worked or locked because still sore), amber (recovering), green (ready) or grey
 * (not trained this week); in Week mode it shows this week's sets against the 10–20 target.
 * Tapping a muscle explains it, offers "still sore" (+24 h locked) and talking to Coach.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BodyMapScreen(container: AppContainer) {
    val profile by container.profileRepository.profile.collectAsState(initial = null)
    val diary by container.workoutRepository.dayPlans.collectAsState(initial = emptyMap())
    val soreness by container.prefs.sorenessReports.collectAsState(initial = emptyList())
    var readiness by remember { mutableStateOf<List<MuscleReadiness>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(diary, soreness, refresh) {
        readiness = runCatching { container.trainingLoad.readiness() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(emptyList())
    }
    var mode by remember { mutableStateOf(BodyMapMode.RECOVERY) }
    var selected by remember { mutableStateOf<MuscleReadiness?>(null) }
    val scope = rememberCoroutineScope()
    val gender = if (profile?.gender == Gender.FEMALE) BodyGender.Female else BodyGender.Male
    val byGroup = readiness.associateBy { it.group }
    val highlights = remember(readiness, mode) {
        readiness.flatMap { r ->
            val color = if (mode == BodyMapMode.RECOVERY) recoveryColor(r) else weekColor(r)
            groupMuscles[r.group].orEmpty().map { it to MuscleHighlight.solid(it, color) }
        }.toMap()
    }
    val onMuscle: (Muscle, com.fittribe.musclemap.MuscleSide) -> Unit = { muscle, _ ->
        groupOf(muscle)?.let { group -> byGroup[group]?.let { selected = it } }
    }
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "mode") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BodyMapMode.entries.forEach { m ->
                        val isSelected = m == mode
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { mode = m }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(if (m == BodyMapMode.RECOVERY) R.string.body_mode_recovery else R.string.body_mode_week),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                InfoIconButton(title = stringResource(R.string.body_destination), body = stringResource(R.string.body_info))
            }
        }

        item(key = "figure") {
            FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().height(360.dp)) {
                        BodyView(
                            gender = gender,
                            side = BodySide.Front,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            highlights = highlights,
                            style = BodyViewStyle.Default,
                            onMuscleSelected = onMuscle
                        )
                        BodyView(
                            gender = gender,
                            side = BodySide.Back,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            highlights = highlights,
                            style = BodyViewStyle.Default,
                            onMuscleSelected = onMuscle
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Legend(mode)
                    Text(stringResource(R.string.body_tap_hint), fontSize = 12.sp, color = muted)
                }
            }
        }

        item(key = "week") {
            FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.body_week_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    readiness.sortedByDescending { it.weeklySets }.filter { it.weeklySets > 0 }.forEach { r ->
                        val share = (r.weeklySets * 100 / WEEKLY_TARGET_SETS)
                        Row(Modifier.fillMaxWidth().clickable { selected = r }, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(r.group.titleRes), modifier = Modifier.width(110.dp), fontSize = 14.sp)
                            LinearProgressIndicator(
                                progress = { (share / 100f).coerceIn(0f, 1f) },
                                color = weekColor(r),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(stringResource(R.string.body_week_row, r.weeklySets, share), fontSize = 12.sp, color = muted)
                        }
                    }
                    val untrained = readiness.filter { it.weeklySets == 0 }
                    if (untrained.isNotEmpty()) {
                        Text(
                            stringResource(R.string.body_week_untrained, untrained.joinToString(", ") { r -> container.appContext.getString(r.group.titleRes) }),
                            fontSize = 13.sp,
                            color = muted
                        )
                    }
                }
            }
        }

        item(key = "sleep") {
            // Only the latest nights here; the full history opens on demand so this tab stays short.
            var showAllSleep by remember { mutableStateOf(false) }
            if (showAllSleep) com.apoorvdarshan.calorietracker.ui.home.SleepHistoryDialog(container) { showAllSleep = false }
            FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 14.dp) {
                com.apoorvdarshan.calorietracker.ui.home.SleepHistorySection(container, limit = 5, onShowAll = { showAllSleep = true })
            }
        }

        val locked = readiness.filter { it.lockedUntil != null || it.percent < 50 }
        if (locked.isNotEmpty()) {
            item(key = "locked") {
                FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.body_locked_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            locked.forEach { r ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Locked.copy(alpha = 0.12f))
                                        .clickable { selected = r }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Locked))
                                    Text(stringResource(r.group.titleRes), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.recovery_hours, r.readyInHours), fontSize = 12.sp, color = muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { r ->
        val clock = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(stringResource(r.group.titleRes)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.recovery_percent, r.percent), fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { r.percent / 100f }, color = recoveryColor(r), modifier = Modifier.fillMaxWidth())
                    when {
                        r.lockedUntil != null -> Text(stringResource(R.string.body_locked_until, clock.format(r.lockedUntil)))
                        r.ready -> Text(stringResource(R.string.recovery_ready_long))
                        else -> Text(stringResource(R.string.recovery_ready_in, r.readyInHours))
                    }
                    Text(
                        stringResource(R.string.body_week_row_long, r.weeklySets, r.weeklySets * 100 / WEEKLY_TARGET_SETS),
                        color = muted
                    )
                    Text(stringResource(tipFor(r.group)))
                    Text(stringResource(R.string.body_soreness_care), color = muted, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val group = r.group
                    selected = null
                    scope.launch {
                        container.trainingLoad.reportSoreness(group)
                        refresh++
                    }
                }) { Text(stringResource(R.string.body_still_sore)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    selected = null
                    container.navigator.showTab(FudAIRoutes.COACH)
                }) { Text(stringResource(R.string.body_ask_coach)) }
            }
        )
    }
}

@Composable
private fun Legend(mode: BodyMapMode) {
    val items = if (mode == BodyMapMode.RECOVERY) {
        listOf(Locked to R.string.body_legend_locked, Recovering to R.string.body_legend_recovering, Ready to R.string.body_legend_ready, Untrained to R.string.body_legend_untrained)
    } else {
        listOf(Recovering to R.string.body_legend_low, Ready to R.string.body_legend_target, Locked to R.string.body_legend_high, Untrained to R.string.body_legend_untrained)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                Text(stringResource(label), fontSize = 11.sp)
            }
        }
    }
}

private fun tipFor(group: MachineMuscleGroup): Int = when (group) {
    MachineMuscleGroup.LEGS, MachineMuscleGroup.HAMSTRINGS, MachineMuscleGroup.GLUTES -> R.string.recovery_tip_legs
    MachineMuscleGroup.CHEST, MachineMuscleGroup.BACK, MachineMuscleGroup.SHOULDERS -> R.string.recovery_tip_upper
    MachineMuscleGroup.ABS -> R.string.recovery_tip_core
    else -> R.string.recovery_tip_small
}
