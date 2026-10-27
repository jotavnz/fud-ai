package com.apoorvdarshan.calorietracker.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.ui.components.InfoIconButton
import com.apoorvdarshan.calorietracker.data.ExerciseItem
import com.apoorvdarshan.calorietracker.data.ExerciseLocalization
import com.apoorvdarshan.calorietracker.data.ExerciseRepository
import com.apoorvdarshan.calorietracker.models.MachineCoach
import com.apoorvdarshan.calorietracker.models.MachineMuscleGroup
import com.apoorvdarshan.calorietracker.models.ProgressionHint
import com.apoorvdarshan.calorietracker.models.SetPrescription
import com.apoorvdarshan.calorietracker.models.TrainingGoal
import com.apoorvdarshan.calorietracker.models.TrainingPlace
import com.apoorvdarshan.calorietracker.models.TrainingInsight
import com.apoorvdarshan.calorietracker.models.WorkoutWeightUnit
import com.apoorvdarshan.calorietracker.ui.navigation.BottomNavScrollPadding

/** "What do you want to train today?" — one tap per muscle group opens the machine coach. */
@Composable
internal fun MachineCoachEntry(
    onPick: (MachineMuscleGroup) -> Unit,
    modifier: Modifier = Modifier,
    /** Groups the smart suggestions mark as still recovering. */
    recovering: Set<MachineMuscleGroup> = emptySet()
) {
    val colors = workoutsColors()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .border(0.5.dp, colors.hairline.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .padding(vertical = 16.dp)
            .testTag("workouts.machineCoach.entry"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(R.string.machine_coach_title),
                color = colors.charcoal,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.machine_coach_subtitle),
                color = colors.mutedText,
                fontSize = 13.sp
            )
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MachineMuscleGroup.entries.forEach { group ->
                MuscleGroupChip(group = group, recovering = group in recovering, onClick = { onPick(group) })
            }
        }
    }
}

@Composable
private fun MuscleGroupChip(group: MachineMuscleGroup, recovering: Boolean, onClick: () -> Unit) {
    val colors = workoutsColors()
    Column(
        Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.panel.copy(alpha = 0.45f))
            .border(0.5.dp, colors.hairline.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp)
            .testTag("workouts.machineCoach.group.${group.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AsyncImage(
            model = muscleGlyphAsset(group.glyphMuscle),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (recovering) colors.mutedText else colors.accent),
            modifier = Modifier.size(34.dp)
        )
        Text(
            stringResource(group.titleRes),
            color = colors.charcoal,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (recovering) {
            Text(
                stringResource(R.string.insight_recovering_badge),
                color = colors.mutedText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Three machines for [group] with sets, reps and rest for the chosen [goal]. */
@Composable
internal fun MachineCoachScreen(
    group: MachineMuscleGroup,
    repo: ExerciseRepository,
    vm: WorkoutsViewModel,
    goal: TrainingGoal,
    modifier: Modifier = Modifier,
    /** Smart suggestions on: recovery/overuse banners, cross-fatigue ranking, load hints, warnings. */
    smart: Boolean = true
) {
    val colors = workoutsColors()
    val state = vm.diaryUiState
    val fatiguedMuscles = if (smart) state.fatiguedMuscles else emptySet()
    val homeFilter = vm.trainingSetup.homeFilter
    val candidates = remember(group, repo, fatiguedMuscles, homeFilter) {
        MachineCoach.candidates(group, repo.byId, fatiguedMuscles, homeFilter)
    }
    val candidateIds = remember(candidates) { candidates.map { it.id } }
    LaunchedEffect(candidateIds) { vm.syncMachineSelection(candidateIds) }
    val suggestions = vm.machineSelection
        .ifEmpty { candidateIds.take(MachineCoach.SUGGESTION_COUNT) }
        .mapNotNull { repo.byId[it] }
    val todayIds = state.todayItemIds
    val unit = state.weightUnit
    // Group tips: recovering since yesterday (not today's own session), or overused this week.
    val recovery = if (smart) recoveryInsight(state.fatigue[group], vm.restedAlternatives(group)) else null
    val overuse = if (smart) remember(group, state) { vm.overuseFor(group) } else null
    var pendingAdd by remember { mutableStateOf<Pair<ExerciseItem, TrainingInsight>?>(null) }

    pendingAdd?.let { (item, warning) ->
        InsightWarningDialog(
            insight = warning,
            unit = unit,
            onAddAnyway = {
                vm.addSuggestedExercise(item, MachineCoach.prescription(goal, item).sets)
                pendingAdd = null
            },
            onOtherOptions = {
                pendingAdd = null
                if (warning is TrainingInsight.Plateau) {
                    vm.swapMachine(vm.machineSelection.indexOf(item.id), candidateIds)
                } else {
                    vm.closeMachineCoach()
                }
            },
            onDismiss = { pendingAdd = null }
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .testTag("workouts.machineCoach")
    ) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = colors.accent,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = vm::closeMachineCoach)
                    .padding(8.dp)
            )
            Text(
                stringResource(group.titleRes),
                color = colors.charcoal,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = BottomNavScrollPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "goal") {
                GoalSelector(selected = goal, onSelect = { vm.trainingGoal = it })
                PlaceSelector(selected = vm.trainingSetup.place, onSelect = vm::setTrainingPlace)
                if (homeFilter != null && candidates.isEmpty()) {
                    Text(stringResource(R.string.place_home_empty), color = workoutsColors().mutedText, fontSize = 13.sp)
                }
            }
            listOfNotNull(recovery, overuse).forEach { tip ->
                item(key = "tip-${tip::class.simpleName}") {
                    TrainingInsightCard(
                        insight = tip,
                        unit = unit,
                        onOpenGroup = { if (it != group) vm.openMachineCoach(it) },
                        onDismiss = null
                    )
                }
            }
            if (suggestions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.machine_coach_empty),
                        color = colors.mutedText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                    )
                }
            }
            itemsIndexed(suggestions, key = { _, item -> item.id }) { index, item ->
                val prescription = MachineCoach.prescription(goal, item)
                SuggestionCard(
                    item = item,
                    repo = repo,
                    vm = vm,
                    prescription = prescription,
                    hint = if (smart) vm.progressionHint(item.id, prescription) else null,
                    unit = unit,
                    isAdded = item.id in todayIds,
                    onSwap = if (candidateIds.size > suggestions.size) {
                        { vm.swapMachine(index, candidateIds) }
                    } else {
                        null
                    },
                    onHowTo = { vm.openExerciseId = item.id },
                    onAdd = {
                        val warning = if (smart) recovery ?: overuse ?: vm.plateauFor(item.id) else null
                        if (warning != null) pendingAdd = item to warning
                        else vm.addSuggestedExercise(item, prescription.sets)
                    }
                )
            }
            if (candidates.size > MachineCoach.SUGGESTION_COUNT || vm.canUndoMachines) {
                item(key = "more") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlineAction(
                            text = stringResource(R.string.machine_coach_previous),
                            icon = Icons.AutoMirrored.Filled.Undo,
                            enabled = vm.canUndoMachines,
                            onClick = vm::undoMachines,
                            modifier = Modifier.weight(1f).testTag("workouts.machineCoach.undo")
                        )
                        OutlineAction(
                            text = stringResource(R.string.machine_coach_more_options),
                            icon = Icons.Filled.Refresh,
                            enabled = candidates.size > MachineCoach.SUGGESTION_COUNT,
                            onClick = { vm.showOtherMachines(candidateIds) },
                            modifier = Modifier.weight(1f).testTag("workouts.machineCoach.more")
                        )
                    }
                }
            }
            item(key = "disclaimer") {
                Text(
                    stringResource(R.string.machine_coach_disclaimer),
                    color = colors.mutedText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaceSelector(selected: TrainingPlace, onSelect: (TrainingPlace) -> Unit) {
    val colors = workoutsColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.place_label).uppercase(),
                color = colors.mutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            InfoIconButton(
                title = stringResource(R.string.place_label),
                body = stringResource(R.string.place_info),
                tint = colors.mutedText
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panel.copy(alpha = 0.45f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TrainingPlace.entries.forEach { place ->
                val isSelected = place == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) colors.accent else colors.panel.copy(alpha = 0f))
                        .clickable { onSelect(place) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(if (place == TrainingPlace.GYM) R.string.place_gym else R.string.place_home),
                        color = if (isSelected) colors.onAccent else colors.charcoal,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalSelector(selected: TrainingGoal, onSelect: (TrainingGoal) -> Unit) {
    val colors = workoutsColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.machine_coach_goal_label).uppercase(),
                color = colors.mutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            InfoIconButton(
                title = stringResource(R.string.machine_coach_goal_label),
                body = stringResource(R.string.training_goal_info),
                tint = colors.mutedText
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.panel.copy(alpha = 0.45f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TrainingGoal.entries.forEach { goal ->
                val isSelected = goal == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) colors.accent else colors.panel.copy(alpha = 0f))
                        .clickable { onSelect(goal) }
                        .padding(vertical = 10.dp)
                        .testTag("workouts.machineCoach.goal.${goal.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(goal.titleRes),
                        color = if (isSelected) colors.onAccent else colors.charcoal,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            stringResource(selected.descriptionRes),
            color = colors.mutedText,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SuggestionCard(
    item: ExerciseItem,
    repo: ExerciseRepository,
    vm: WorkoutsViewModel,
    prescription: SetPrescription,
    hint: ProgressionHint?,
    unit: WorkoutWeightUnit,
    isAdded: Boolean,
    /** Replaces only this suggestion; null when there is nothing else to offer. */
    onSwap: (() -> Unit)?,
    onHowTo: () -> Unit,
    onAdd: () -> Unit
) {
    val colors = workoutsColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .border(0.5.dp, colors.hairline.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .testTag("workouts.machineCoach.card.${item.id}")
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(colors.panel.copy(alpha = 0.3f))
                .clickable(onClick = onHowTo)
        ) {
            AnimatedExerciseImage(
                repo.visualFor(item, vm.diaryUiState.visualGender),
                Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                fallbackLabel = item.name
            )
            if (onSwap != null) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(colors.background.copy(alpha = 0.85f))
                        .border(0.5.dp, colors.hairline.copy(alpha = 0.5f), CircleShape)
                        .clickable(onClick = onSwap)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("workouts.machineCoach.swap.${item.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Filled.SwapHoriz, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.machine_coach_swap_one),
                        color = colors.charcoal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                item.name,
                color = colors.charcoal,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.FitnessCenter, null, tint = colors.mutedText, modifier = Modifier.size(14.dp))
                Text(
                    ExerciseLocalization.label(item.equipment),
                    color = colors.mutedText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accent.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(
                        R.string.machine_coach_prescription,
                        prescription.sets,
                        prescription.minReps,
                        prescription.maxReps
                    ),
                    color = colors.charcoal,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Timer, null, tint = colors.secondaryAccent, modifier = Modifier.size(14.dp))
                    Text(
                        restLabel(prescription.restSeconds),
                        color = colors.secondaryAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                hint?.let { ProgressionLine(it, unit) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoachButton(
                    text = stringResource(R.string.machine_coach_how_to),
                    icon = Icons.Filled.PlayCircle,
                    filled = false,
                    enabled = true,
                    onClick = onHowTo,
                    modifier = Modifier.weight(1f)
                )
                CoachButton(
                    text = stringResource(if (isAdded) R.string.machine_coach_added_today else R.string.machine_coach_add_today),
                    icon = if (isAdded) Icons.Filled.Check else Icons.Filled.Add,
                    filled = !isAdded,
                    enabled = !isAdded,
                    onClick = onAdd,
                    modifier = Modifier.weight(1f).testTag("workouts.machineCoach.add.${item.id}")
                )
            }
        }
    }
}

@Composable
private fun OutlineAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = workoutsColors()
    val tint = if (enabled) colors.accent else colors.mutedText
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .border(0.8.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun restLabel(seconds: Int): String =
    if (seconds >= 120 && seconds % 60 == 0) {
        stringResource(R.string.machine_coach_rest_minutes, seconds / 60)
    } else {
        stringResource(R.string.machine_coach_rest_seconds, seconds)
    }

@Composable
private fun CoachButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = workoutsColors()
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(if (filled) colors.accent else colors.panel.copy(alpha = 0.4f))
            .border(0.5.dp, if (filled) colors.accent else colors.hairline.copy(alpha = 0.5f), shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (filled) colors.onAccent else colors.accent
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = if (filled) colors.onAccent else colors.charcoal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}
