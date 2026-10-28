package com.apoorvdarshan.calorietracker.models

/**
 * Bodyweight ladders from easiest to hardest, so the exercise detail can point to the step
 * before and after ("wall push-up → incline → knees → full"). Move on once the current step
 * feels easy for about 3 × 12–15 (or 3 × 30 s for holds). Catalog ids: the app's own
 * exercises (Fud_*, kept free-exercise-db ids) and the exercises-dataset catalog (ed_*).
 */
object ExerciseProgressions {
    val ladders: List<List<String>> = listOf(
        // Push: wall, incline, knees, full, diamond, archer.
        listOf("Fud_Wall_Push_Up", "ed_0493", "Fud_Knee_Push_Up", "ed_0662", "ed_0283", "ed_3294"),
        // Pull: hang, scapular pull-up, inverted row, band-assisted pull-up, pull-up.
        listOf("Fud_Dead_Hang", "ed_0688", "ed_0499", "ed_0970", "ed_0652"),
        // Squat: chair, bodyweight, split squat, one leg, pistol.
        listOf("Fud_Sit_To_Stand", "Bodyweight_Squat", "ed_0410", "ed_1476", "ed_1759"),
        // Front core: bird dog, dead bug, hollow hold.
        listOf("Fud_Bird_Dog", "ed_0276", "Fud_Hollow_Body_Hold"),
        // Side core: knees, incline, full side plank.
        listOf("Fud_Side_Plank_Knees", "ed_3544", "ed_0705"),
        // Hips: glute bridge, single-leg bridge.
        listOf("ed_3013", "ed_3645"),
        // Calves: supported, free standing, one leg.
        listOf("Fud_Supported_Heel_Raises", "ed_1373", "ed_1387")
    )

    data class Steps(val easier: String?, val harder: String?)

    /** The step before and after [id], or null when it is on no ladder. */
    fun around(id: String): Steps? {
        val ladder = ladders.firstOrNull { id in it } ?: return null
        val index = ladder.indexOf(id)
        return Steps(ladder.getOrNull(index - 1), ladder.getOrNull(index + 1))
    }
}
