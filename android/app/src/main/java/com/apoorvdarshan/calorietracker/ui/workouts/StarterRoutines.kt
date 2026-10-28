package com.apoorvdarshan.calorietracker.ui.workouts

import androidx.annotation.StringRes
import com.apoorvdarshan.calorietracker.R

/**
 * Ready-made first sessions offered on an empty day in simple logging: whole body, 2 sets
 * each, so someone without a routine can start in one tap. Catalog ids (machines from the
 * exercises-dataset catalog, the rest the app's own exercises in exercises_extra.json).
 */
internal enum class StarterRoutine(
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
    val exerciseIds: List<String>
) {
    GYM_MACHINES(
        R.string.wk_starter_gym_title,
        R.string.wk_starter_gym_detail,
        listOf("ed_0739", "ed_0577", "ed_0861", "ed_0603", "ed_0599", "ed_1452")
    ),
    HOME_NO_EQUIPMENT(
        R.string.wk_starter_home_title,
        R.string.wk_starter_home_detail,
        listOf("Bodyweight_Squat", "Fud_Knee_Push_Up", "ed_3013", "Fud_Bird_Dog", "Fud_Side_Plank_Knees")
    ),
    GENTLE(
        R.string.wk_starter_gentle_title,
        R.string.wk_starter_gentle_detail,
        listOf("Fud_Marching_In_Place", "Fud_Sit_To_Stand", "Fud_Wall_Push_Up", "Fud_Supported_Heel_Raises", "Fud_Single_Leg_Stand")
    );

    companion object {
        /** Sets per lift in a starter session. */
        const val SETS = 2
    }
}
