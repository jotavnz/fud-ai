package com.apoorvdarshan.calorietracker.ui.workouts

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.data.ExerciseLocalization
import com.apoorvdarshan.calorietracker.models.WorkoutRpeScale
import com.apoorvdarshan.calorietracker.models.WorkoutSplit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Display-only labels for workout enums and split groups. The English `title` values on the
 * models are persisted keys and AI-prompt text, so they are translated here, never there.
 */
@Composable
fun workoutSplitLabel(split: WorkoutSplit): String = stringResource(
    when (split) {
        WorkoutSplit.PUSH_PULL_LEGS -> R.string.wk_split_push_pull_legs
        WorkoutSplit.UPPER_LOWER -> R.string.wk_split_upper_lower
        WorkoutSplit.BODY_PART -> R.string.wk_split_body_part
        WorkoutSplit.ARNOLD -> R.string.wk_split_arnold
        WorkoutSplit.PUSH_PULL -> R.string.wk_split_push_pull
        WorkoutSplit.ANTAGONIST -> R.string.wk_split_antagonist
        WorkoutSplit.HYBRID -> R.string.wk_split_hybrid
        WorkoutSplit.FULL_BODY -> R.string.wk_split_full_body
        WorkoutSplit.CUSTOM -> R.string.wk_split_custom
    }
)

@Composable
fun workoutRpeScaleLabel(scale: WorkoutRpeScale): String = stringResource(
    when (scale) {
        WorkoutRpeScale.STRENGTH -> R.string.wk_rpe_strength
        WorkoutRpeScale.CR10 -> R.string.wk_rpe_cr10
        WorkoutRpeScale.BORG -> R.string.wk_rpe_borg
    }
)

/** Maps a persisted [com.apoorvdarshan.calorietracker.models.WorkoutSplitGroup] title to its display text. */
@Composable
fun workoutSplitGroupLabel(title: String): String {
    val res = when (title) {
        "Push" -> R.string.wk_group_push
        "Pull" -> R.string.wk_group_pull
        "Legs" -> R.string.wk_group_legs
        "Core" -> R.string.wk_group_core
        "Upper" -> R.string.wk_group_upper
        "Lower" -> R.string.wk_group_lower
        "Chest" -> R.string.wk_group_chest
        "Back" -> R.string.wk_group_back
        "Shoulders" -> R.string.wk_group_shoulders
        "Arms" -> R.string.wk_group_arms
        "Chest + Back" -> R.string.wk_group_chest_back
        "Shoulders + Arms" -> R.string.wk_group_shoulders_arms
        "Accessory/Core" -> R.string.wk_group_accessory_core
        "Biceps + Triceps" -> R.string.wk_group_biceps_triceps
        "Quads + Hamstrings/Glutes" -> R.string.wk_group_quads_hams_glutes
        "Shoulders + Lats/Traps" -> R.string.wk_group_shoulders_lats_traps
        "Core/Accessory" -> R.string.wk_group_core_accessory
        "Strength/Compound" -> R.string.wk_group_strength_compound
        "Accessory/Hypertrophy" -> R.string.wk_group_accessory_hypertrophy
        else -> null
    }
    return if (res != null) stringResource(res) else ExerciseLocalization.label(title)
}

/** Today / Tomorrow / Yesterday, otherwise a weekday + month + day in the current locale. */
@Composable
fun workoutDateTitle(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.wk_today)
        today.plusDays(1) -> stringResource(R.string.wk_tomorrow)
        today.minusDays(1) -> stringResource(R.string.wk_yesterday)
        else -> {
            val locale = LocalConfiguration.current.locales[0]
            date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMd"), locale))
        }
    }
}
