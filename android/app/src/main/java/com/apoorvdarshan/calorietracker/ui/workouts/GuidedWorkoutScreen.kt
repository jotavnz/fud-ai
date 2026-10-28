package com.apoorvdarshan.calorietracker.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.data.ExerciseVisual
import com.apoorvdarshan.calorietracker.models.PlannedExercise
import com.apoorvdarshan.calorietracker.models.WorkoutRestTimer
import com.apoorvdarshan.calorietracker.models.WorkoutSetInput
import com.apoorvdarshan.calorietracker.models.WorkoutRpeScale
import com.apoorvdarshan.calorietracker.models.WorkoutSetFeel
import com.apoorvdarshan.calorietracker.models.WorkoutWeightUnit
import com.apoorvdarshan.calorietracker.ui.components.FudGlassPrimaryButton
import com.apoorvdarshan.calorietracker.ui.components.FudGlassTextButton
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * "Train step by step": one exercise and one set at a time, with its picture and how-to,
 * big weight/reps fields and Easy / Good / Hard to finish the set (which starts the rest).
 * Everything is written to the same diary entries as the regular log.
 */
@Composable
internal fun GuidedWorkoutScreen(
    exercises: List<PlannedExercise>,
    weightUnit: WorkoutWeightUnit,
    rpeScale: WorkoutRpeScale,
    restTimer: WorkoutRestTimer?,
    visualFor: (PlannedExercise) -> ExerciseVisual,
    onWeight: (exerciseId: UUID, setId: UUID, value: String) -> Unit,
    onReps: (exerciseId: UUID, setId: UUID, value: String) -> Unit,
    onRpe: (exerciseId: UUID, setId: UUID, value: String) -> Unit,
    onStartRest: ((exerciseId: UUID, setId: UUID) -> Unit)?,
    onSkipRest: () -> Unit,
    onDismiss: () -> Unit
) {
    var exerciseIndex by remember { mutableIntStateOf(exercises.indexOfFirst { e -> e.sets.any { it.reps.isBlank() } }.coerceAtLeast(0)) }
    val exercise = exercises.getOrNull(exerciseIndex)
    var setIndex by remember(exerciseIndex) {
        mutableIntStateOf(exercise?.sets?.indexOfFirst { it.reps.isBlank() }?.takeIf { it >= 0 } ?: 0)
    }
    var showHowTo by remember(exerciseIndex) { mutableStateOf(false) }

    fun next() {
        val sets = exercise?.sets.orEmpty()
        if (setIndex + 1 < sets.size) setIndex++ else if (exerciseIndex + 1 < exercises.size) exerciseIndex++ else onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (exercise == null) {
                LaunchedEffect(Unit) { onDismiss() }
                return@Box
            }
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.wk_guided_progress, exerciseIndex + 1, exercises.size),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pantry_close)) }
                }
                Text(exercise.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                AnimatedExerciseImage(
                    visualFor(exercise),
                    Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)),
                    fallbackLabel = exercise.primaryMuscles.firstOrNull()
                )
                if (exercise.instructions.isNotEmpty()) {
                    FudGlassTextButton(
                        text = stringResource(if (showHowTo) R.string.wk_guided_hide_howto else R.string.wk_guided_howto),
                        onClick = { showHowTo = !showHowTo }
                    )
                    if (showHowTo) {
                        exercise.instructions.forEachIndexed { i, step ->
                            Text("${i + 1}. $step", fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }
                }

                val resting = restTimer?.takeIf { it.exerciseId == exercise.id }
                if (resting != null) {
                    var remaining by remember(resting) { mutableStateOf(resting.remainingSeconds()) }
                    LaunchedEffect(resting) {
                        while (remaining > 0) {
                            delay(500)
                            remaining = resting.remainingSeconds()
                        }
                    }
                    Text(stringResource(R.string.wk_guided_rest), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(
                        String.format(java.util.Locale.US, "%d:%02d", remaining / 60, remaining % 60),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Calorie,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    FudGlassPrimaryButton(text = stringResource(R.string.wk_guided_skip_rest), onClick = onSkipRest)
                } else if (exercise.isCardio || exercise.sets.isEmpty()) {
                    Text(stringResource(R.string.wk_guided_cardio), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    FudGlassPrimaryButton(text = stringResource(R.string.wk_guided_next), onClick = { next() })
                } else {
                    val set = exercise.sets.getOrNull(setIndex) ?: exercise.sets.last()
                    Text(
                        stringResource(R.string.wk_guided_set, setIndex + 1, exercise.sets.size),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Calorie
                    )
                    var weight by remember(set.id) { mutableStateOf(set.displayWeight(weightUnit)) }
                    var reps by remember(set.id) { mutableStateOf(set.reps) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = WorkoutSetInput.weight(it) },
                            label = { Text(stringResource(R.string.wk_header_weight, weightUnit.storageValue)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reps,
                            onValueChange = { reps = WorkoutSetInput.reps(it) },
                            label = { Text(stringResource(R.string.wk_header_reps)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(stringResource(R.string.wk_guided_weight_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    Text(stringResource(R.string.wk_feel_question), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    WorkoutSetFeel.entries.forEach { feel ->
                        val color = when (feel) {
                            WorkoutSetFeel.EASY -> Color(0xFF2E7D32)
                            WorkoutSetFeel.GOOD -> AppColors.Calorie
                            WorkoutSetFeel.HARD -> Color(0xFFD32F2F)
                        }
                        FeelButton(
                            title = stringResource(feel.guidedLabelRes),
                            detail = stringResource(feel.guidedDetailRes),
                            color = color,
                            enabled = reps.isNotBlank()
                        ) {
                            onWeight(exercise.id, set.id, weight)
                            onReps(exercise.id, set.id, reps)
                            onRpe(exercise.id, set.id, feel.rpe(set.rpeScale ?: rpeScale))
                            val last = setIndex + 1 >= exercise.sets.size
                            if (!last) onStartRest?.invoke(exercise.id, set.id)
                            next()
                        }
                    }
                }
                FudGlassTextButton(text = stringResource(R.string.wk_guided_finish), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FeelButton(title: String, detail: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = if (enabled) 0.18f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color.copy(alpha = if (enabled) 1f else 0.4f))
        Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.3f))
    }
}

private val WorkoutSetFeel.guidedLabelRes: Int
    get() = when (this) {
        WorkoutSetFeel.EASY -> R.string.wk_feel_easy
        WorkoutSetFeel.GOOD -> R.string.wk_feel_good
        WorkoutSetFeel.HARD -> R.string.wk_feel_hard
    }

private val WorkoutSetFeel.guidedDetailRes: Int
    get() = when (this) {
        WorkoutSetFeel.EASY -> R.string.wk_feel_easy_detail
        WorkoutSetFeel.GOOD -> R.string.wk_feel_good_detail
        WorkoutSetFeel.HARD -> R.string.wk_feel_hard_detail
    }
