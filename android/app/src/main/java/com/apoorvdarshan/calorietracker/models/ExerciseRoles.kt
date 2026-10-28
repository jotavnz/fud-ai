package com.apoorvdarshan.calorietracker.models

/**
 * Catalog exercises grouped by what they are for outside the main lifts: dynamic warm-up,
 * cool-down, mobility, posture, deep core, hypopressives, balance for seniors and beginners, and
 * calisthenics progressions. Ids are free-exercise-db or Fud AI's own (`Fud_*`, see
 * exercises_extra.json), so every pick opens with its instructions and can be added to the diary.
 *
 * Doses are plain English for Coach, which translates them; they are conservative defaults.
 */
object ExerciseRoles {
    data class Pick(val id: String, val dose: String)

    enum class Kind { WARMUP, COOLDOWN, MOBILITY, POSTURE, DEEP_CORE, HYPOPRESSIVE, BALANCE, CALISTHENICS }

    private val LEGS = setOf("quadriceps", "hamstrings", "glutes", "calves", "adductors", "abductors")
    private val PUSH = setOf("chest", "shoulders", "triceps")
    private val PULL = setOf("lats", "middle back", "biceps", "traps", "forearms", "lower back", "neck")

    val fixed: Map<Kind, List<Pick>> = mapOf(
        Kind.MOBILITY to listOf(
            Pick("Fud_Cat_Cow", "8–10 slow reps"),
            Pick("Fud_Open_Book_Rotation", "8 per side"),
            Pick("Standing_Hip_Circles", "10 each direction"),
            Pick("Ankle_Circles", "10 each direction per foot"),
            Pick("Shoulder_Circles", "10 forward, 10 back"),
            Pick("Worlds_Greatest_Stretch", "5 per side")
        ),
        Kind.POSTURE to listOf(
            Pick("Fud_Chin_Tuck", "10 reps, 3–5 s hold; repeat during desk breaks"),
            Pick("Fud_Wall_Angel", "2 × 10"),
            Pick("Band_Pull_Apart", "2 × 15"),
            Pick("Face_Pull", "2–3 × 12–15, light"),
            Pick("Kneeling_Hip_Flexor", "30 s per side"),
            Pick("Fud_Open_Book_Rotation", "8 per side"),
            Pick("Superman", "2 × 10, 2 s hold")
        ),
        Kind.DEEP_CORE to listOf(
            Pick("Fud_Diaphragmatic_Breathing", "5–10 breaths"),
            Pick("Dead_Bug", "2 × 8 per side, slow"),
            Pick("Fud_Bird_Dog", "2 × 8 per side"),
            Pick("Fud_Side_Plank_Knees", "2 × 20 s per side"),
            Pick("Pallof_Press", "2 × 10 per side"),
            Pick("Fud_Hollow_Body_Hold", "3 × 15–30 s (knees bent to start)"),
            Pick("Fud_Pelvic_Floor_Activation", "8–10 × 3–5 s holds")
        ),
        Kind.HYPOPRESSIVE to listOf(
            Pick("Fud_Hypopressive_Lying", "3–5 apneas of 5–10 s; learn here first"),
            Pick("Fud_Hypopressive_Kneeling", "3–5 apneas of 5–10 s"),
            Pick("Fud_Hypopressive_Standing", "3–5 apneas of 5–10 s")
        ),
        Kind.BALANCE to listOf(
            Pick("Fud_Marching_In_Place", "2 min"),
            Pick("Fud_Sit_To_Stand", "2 × 8–12"),
            Pick("Fud_Wall_Push_Up", "2 × 10–15"),
            Pick("Fud_Supported_Heel_Raises", "2 × 10–15"),
            Pick("Fud_Single_Leg_Stand", "3 × up to 30 s per leg, with support"),
            Pick("Fud_Tandem_Walk", "2 × 10–20 steps"),
            Pick("Chair_Upper_Body_Stretch", "20–30 s")
        ),
        Kind.CALISTHENICS to listOf(
            Pick("Fud_Wall_Push_Up", "Push step 1: 3 × 15 before moving on"),
            Pick("Incline_Push-Up", "Push step 2: 3 × 12"),
            Pick("Fud_Knee_Push_Up", "Push step 3: 3 × 12–15"),
            Pick("Pushups", "Push step 4: 3 × 8+"),
            Pick("Fud_Dead_Hang", "Pull step 1: 3 × 20–30 s"),
            Pick("Scapular_Pull-Up", "Pull step 2: 3 × 8"),
            Pick("Inverted_Row", "Pull step 3: 3 × 8–12"),
            Pick("Band_Assisted_Pull-Up", "Pull step 4: 3 × 5–8"),
            Pick("Pullups", "Pull step 5: sets of 3–5"),
            Pick("Bodyweight_Squat", "Legs: 3 × 15, then single-leg work"),
            Pick("Fud_Hollow_Body_Hold", "Core base for L-sits and levers")
        )
    )

    /**
     * RAMP-style warm-up for the day's muscles: raise the pulse, mobilise and activate what will
     * be trained, then the first exercise's lighter ramp-up sets. [gentle] (beginners, seniors,
     * higher body weight) keeps it low impact.
     */
    fun warmup(muscles: Collection<String>, gentle: Boolean): List<Pick> {
        val targets = muscles.map { it.lowercase() }.toSet()
        val picks = mutableListOf(
            if (gentle) Pick("Fud_Marching_In_Place", "3–5 min, able to talk")
            else Pick("Walking_Treadmill", "3–5 min easy walk or bike")
        )
        if (targets.any { it in LEGS } || targets.isEmpty()) {
            picks += Pick("Standing_Hip_Circles", "10 each direction")
            picks += Pick("Fud_Leg_Swings", "10–15 per leg")
            picks += if (gentle) Pick("Chair_Squat", "10 slow reps") else Pick("Bodyweight_Squat", "10–15 reps")
        }
        if (targets.any { it in PUSH }) {
            picks += Pick("Arm_Circles", "10 forward, 10 back")
            picks += Pick("Fud_Wall_Push_Up", "10 reps")
        }
        if (targets.any { it in PULL }) {
            picks += Pick("Fud_Cat_Cow", "8 slow reps")
            picks += Pick("Band_Pull_Apart", "15 light reps")
        }
        if (targets.isNotEmpty() && targets.none { it in LEGS || it in PUSH || it in PULL }) {
            picks += Pick("Fud_Cat_Cow", "8 slow reps")
            picks += Pick("Fud_Bird_Dog", "6 per side")
        }
        return picks.distinctBy { it.id }.take(6)
    }

    /**
     * Cool-down for the muscles just trained: an easy walk after legs (no sudden stop), short
     * holds for what worked, and slow breathing. Comfort and calming down, not injury prevention.
     */
    fun cooldown(muscles: Collection<String>, heavyLegs: Boolean): List<Pick> {
        val targets = muscles.map { it.lowercase() }.toSet()
        val picks = mutableListOf<Pick>()
        if (heavyLegs) picks += Pick("Walking_Treadmill", "5 min very easy walk, no sudden stop")
        val stretches = linkedMapOf(
            "quadriceps" to Pick("Quad_Stretch", "30 s per side"),
            "hamstrings" to Pick("Hamstring_Stretch", "30 s per side"),
            "glutes" to Pick("Lying_Glute", "30 s per side"),
            "calves" to Pick("Calf_Stretch_Hands_Against_Wall", "30 s per side"),
            "chest" to Pick("Chest_And_Front_Of_Shoulder_Stretch", "30 s"),
            "shoulders" to Pick("Chest_And_Front_Of_Shoulder_Stretch", "30 s"),
            "triceps" to Pick("Overhead_Triceps", "30 s per side"),
            "lats" to Pick("Childs_Pose", "45–60 s"),
            "middle back" to Pick("Childs_Pose", "45–60 s"),
            "lower back" to Pick("Chair_Lower_Back_Stretch", "30 s")
        )
        stretches.forEach { (muscle, pick) -> if (muscle in targets) picks += pick }
        if (picks.size < 2) picks += Pick("Childs_Pose", "45–60 s")
        picks += Pick("Fud_Diaphragmatic_Breathing", "5–10 slow breaths")
        return picks.distinctBy { it.id }.take(6)
    }

    fun picks(kind: Kind, muscles: Collection<String> = emptyList(), gentle: Boolean = false, heavyLegs: Boolean = false): List<Pick> =
        when (kind) {
            Kind.WARMUP -> warmup(muscles, gentle)
            Kind.COOLDOWN -> cooldown(muscles, heavyLegs)
            else -> fixed.getValue(kind)
        }

    fun parseKind(value: String?): Kind? = when (value?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_')) {
        "warmup", "warm_up" -> Kind.WARMUP
        "cooldown", "cool_down", "recovery" -> Kind.COOLDOWN
        "mobility", "joint_mobility" -> Kind.MOBILITY
        "posture" -> Kind.POSTURE
        "deep_core", "core" -> Kind.DEEP_CORE
        "hypopressive", "hypopressives" -> Kind.HYPOPRESSIVE
        "balance", "senior", "seniors", "joint_stability", "stability" -> Kind.BALANCE
        "calisthenics", "calisthenics_progression", "progression" -> Kind.CALISTHENICS
        else -> null
    }

    /** Every id this object can suggest (for the catalog check in tests). */
    val allIds: Set<String>
        get() = (fixed.values.flatten() +
            warmup(LEGS + PUSH + PULL, gentle = false) + warmup(LEGS + PUSH + PULL, gentle = true) +
            warmup(setOf("abdominals"), gentle = true) +
            cooldown(LEGS + PUSH + PULL, heavyLegs = true)).map { it.id }.toSet()
}
