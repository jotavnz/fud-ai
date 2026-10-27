package com.apoorvdarshan.calorietracker.models

import androidx.annotation.StringRes
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.data.ExerciseItem

/** What the user trains for; drives the sets / reps / rest the machine coach prescribes. */
enum class TrainingGoal(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    HYPERTROPHY(R.string.machine_coach_goal_hypertrophy, R.string.machine_coach_goal_hypertrophy_desc),
    STRENGTH(R.string.machine_coach_goal_strength, R.string.machine_coach_goal_strength_desc),
    ENDURANCE(R.string.machine_coach_goal_endurance, R.string.machine_coach_goal_endurance_desc);

    companion object {
        /** Starting goal from the profile: building or keeping weight favors muscle growth. */
        fun from(weightGoal: WeightGoal?): TrainingGoal = when (weightGoal) {
            WeightGoal.LOSE -> ENDURANCE
            WeightGoal.GAIN, WeightGoal.MAINTAIN, null -> HYPERTROPHY
        }
    }
}

/** Where the user trains: the gym (machines first) or home (their own equipment). */
enum class TrainingPlace { GYM, HOME }

/** Training place and home equipment (library names: dumbbell, bands, kettlebells...). */
data class TrainingSetup(
    val place: TrainingPlace = TrainingPlace.GYM,
    val homeEquipment: Set<String> = emptySet(),
    /** When the user last told Coach where they train; null = never. */
    val confirmedAtMs: Long? = null
) {
    /** The equipment filter for the machine coach: null at the gym. */
    val homeFilter: Set<String>? get() = if (place == TrainingPlace.HOME) homeEquipment else null

    /** Lines for Coach's prompt. Model input, so it is not localized. */
    fun promptLines(now: java.time.Instant): List<String> = buildList {
        add("## Where the user trains")
        val equipment = if (homeEquipment.isEmpty()) "body weight only" else (homeEquipment + "body weight").joinToString(", ")
        add(if (place == TrainingPlace.HOME) "- At home with: $equipment. Recommend exercises without machines." else "- At the gym (machines available).")
        val days = confirmedAtMs?.let { java.time.Duration.between(java.time.Instant.ofEpochMilli(it), now).toDays() }
        add("- Confirmed by the user: ${days?.let { "$it days ago" } ?: "never"}")
        if (days == null || days > 14) {
            add("At a natural moment (not in the middle of logging), ask once whether they still train at the gym or at home and with what equipment, then call propose_goal_plan with training_place and home_equipment (keep the rest of their plan).")
        }
        add("When the user says they stopped going to the gym, train at home now, or are back at the gym, call propose_goal_plan with the updated training_place.")
    }
}

/** A planned dose for one exercise: working sets, rep range, and rest between sets. */
data class SetPrescription(
    val sets: Int,
    val minReps: Int,
    val maxReps: Int,
    val restSeconds: Int
)

/**
 * Muscle groups a lost gym-goer can pick from. [muscles] are free-exercise-db primary
 * muscle titles; [curatedIds] are the machines most gyms have, best first, so the first
 * three suggestions are recognizable equipment rather than rare variations.
 */
enum class MachineMuscleGroup(
    @StringRes val titleRes: Int,
    /** free-exercise-db muscle whose glyph represents the group. */
    val glyphMuscle: String,
    val muscles: Set<String>,
    val curatedIds: List<String>
) {
    CHEST(
        R.string.machine_coach_group_chest, "Chest", setOf("Chest"),
        listOf(
            "Machine_Bench_Press", "Butterfly", "Cable_Crossover",
            "Leverage_Incline_Chest_Press", "Smith_Machine_Bench_Press", "Low_Cable_Crossover"
        )
    ),
    BACK(
        R.string.machine_coach_group_back, "Lats", setOf("Lats", "Middle Back"),
        listOf(
            "Wide-Grip_Lat_Pulldown", "Seated_Cable_Rows", "Leverage_High_Row",
            "V-Bar_Pulldown", "Straight-Arm_Pulldown", "Lying_T-Bar_Row"
        )
    ),
    SHOULDERS(
        R.string.machine_coach_group_shoulders, "Shoulders", setOf("Shoulders"),
        listOf(
            "Machine_Shoulder_Military_Press", "Cable_Seated_Lateral_Raise", "Reverse_Machine_Flyes",
            "Face_Pull", "Leverage_Shoulder_Press", "Front_Cable_Raise"
        )
    ),
    BICEPS(
        R.string.machine_coach_group_biceps, "Biceps", setOf("Biceps"),
        listOf(
            "Machine_Bicep_Curl", "Machine_Preacher_Curls", "Standing_Biceps_Cable_Curl",
            "Cable_Hammer_Curls_-_Rope_Attachment", "Cable_Preacher_Curl", "High_Cable_Curls"
        )
    ),
    TRICEPS(
        R.string.machine_coach_group_triceps, "Triceps", setOf("Triceps"),
        listOf(
            "Triceps_Pushdown_-_Rope_Attachment", "Machine_Triceps_Extension", "Dip_Machine",
            "Triceps_Pushdown", "Cable_Rope_Overhead_Triceps_Extension", "Reverse_Grip_Triceps_Pushdown"
        )
    ),
    LEGS(
        R.string.machine_coach_group_legs, "Quadriceps", setOf("Quadriceps"),
        listOf(
            "Leg_Press", "Leg_Extensions", "Hack_Squat",
            "Smith_Machine_Squat", "Narrow_Stance_Leg_Press", "Single-Leg_Leg_Extension"
        )
    ),
    HAMSTRINGS(
        R.string.machine_coach_group_hamstrings, "Hamstrings", setOf("Hamstrings"),
        listOf(
            "Seated_Leg_Curl", "Lying_Leg_Curls", "Standing_Leg_Curl",
            "Glute_Ham_Raise", "Smith_Machine_Stiff-Legged_Deadlift", "Reverse_Hyperextension"
        )
    ),
    GLUTES(
        R.string.machine_coach_group_glutes, "Glutes", setOf("Glutes", "Abductors"),
        listOf(
            "Barbell_Hip_Thrust", "Thigh_Abductor", "One-Legged_Cable_Kickback",
            "Pull_Through", "Barbell_Glute_Bridge", "Thigh_Adductor"
        )
    ),
    CALVES(
        R.string.machine_coach_group_calves, "Calves", setOf("Calves"),
        listOf(
            "Standing_Calf_Raises", "Seated_Calf_Raise", "Calf_Press_On_The_Leg_Press_Machine",
            "Smith_Machine_Calf_Raise", "Calf_Press"
        )
    ),
    ABS(
        R.string.machine_coach_group_abs, "Abdominals", setOf("Abdominals"),
        listOf(
            "Ab_Crunch_Machine", "Cable_Crunch", "Standing_Cable_Wood_Chop",
            "Pallof_Press", "Rope_Crunch", "Cable_Reverse_Crunch"
        )
    )
}

object MachineCoach {
    const val SUGGESTION_COUNT = 3

    /**
     * Every candidate for [group], curated machines first, then the rest of the library's
     * strength exercises for those muscles (machines and cables before free weights).
     * Exercises that also load [fatiguedMuscles] outside the group (e.g. dips after a chest
     * day when training triceps) move behind the rest, keeping their relative order.
     */
    fun candidates(
        group: MachineMuscleGroup,
        catalog: Map<String, ExerciseItem>,
        fatiguedMuscles: Set<String> = emptySet(),
        /** Training at home: only exercises this equipment (plus body weight) allows. Null = gym. */
        homeEquipment: Set<String>? = null
    ): List<ExerciseItem> {
        val ranked = if (homeEquipment != null) homeCandidates(group, catalog, homeEquipment) else rankedCandidates(group, catalog)
        val target = group.muscles.mapTo(mutableSetOf()) { it.lowercase() }
        val tired = fatiguedMuscles.map { it.lowercase() }.toSet() - target
        if (tired.isEmpty()) return ranked
        return ranked.sortedBy { item ->
            if ((item.primaryMuscles + item.secondaryMuscles).any { it.lowercase() in tired }) 1 else 0
        }
    }

    /** Home training: the group's strength exercises the user's equipment allows, free weights first. */
    private fun homeCandidates(group: MachineMuscleGroup, catalog: Map<String, ExerciseItem>, equipment: Set<String>): List<ExerciseItem> {
        val allowed = (equipment.map { it.lowercase() } + "body only").toSet()
        val wanted = group.muscles.mapTo(mutableSetOf()) { it.lowercase() }
        return catalog.values
            .asSequence()
            .filter { it.category.lowercase() in strengthCategories }
            .filter { it.equipment.lowercase() in allowed }
            .filter { item -> item.primaryMuscles.any { it.lowercase() in wanted } }
            .sortedWith(
                compareBy<ExerciseItem>(
                    { homeEquipmentRank(it.equipment) },
                    { levelRank(it.level) },
                    { if (it.mechanic.equals("Compound", ignoreCase = true)) 0 else 1 },
                    { it.name.lowercase() }
                )
            )
            .toList()
    }

    private fun homeEquipmentRank(equipment: String): Int = when (equipment.lowercase()) {
        "dumbbell" -> 0
        "kettlebells" -> 1
        "bands" -> 2
        "body only" -> 3
        else -> 4
    }

    private fun rankedCandidates(group: MachineMuscleGroup, catalog: Map<String, ExerciseItem>): List<ExerciseItem> {
        val curated = group.curatedIds.mapNotNull(catalog::get)
        val curatedIds = curated.mapTo(mutableSetOf()) { it.id }
        val wanted = group.muscles.mapTo(mutableSetOf()) { it.lowercase() }
        val others = catalog.values
            .asSequence()
            .filter { it.id !in curatedIds }
            .filter { it.category.lowercase() in strengthCategories }
            .filter { item -> item.primaryMuscles.any { it.lowercase() in wanted } }
            .sortedWith(
                compareBy<ExerciseItem>(
                    { equipmentRank(it.equipment) },
                    { levelRank(it.level) },
                    { if (it.mechanic.equals("Compound", ignoreCase = true)) 0 else 1 },
                    { it.name.lowercase() }
                )
            )
            .toList()
        return curated + others
    }

    /** The [SUGGESTION_COUNT] suggestions on [page]; pages wrap around the candidate list. */
    fun suggestions(candidates: List<ExerciseItem>, page: Int): List<ExerciseItem> {
        if (candidates.size <= SUGGESTION_COUNT) return candidates
        val start = (page.coerceAtLeast(0) * SUGGESTION_COUNT) % candidates.size
        return List(SUGGESTION_COUNT) { candidates[(start + it) % candidates.size] }
    }

    /**
     * General-population guidance: multi-joint machines carry more sets and longer rests;
     * single-joint machines stay in safer, higher rep ranges even when training strength.
     */
    fun prescription(goal: TrainingGoal, item: ExerciseItem): SetPrescription {
        val compound = item.mechanic.equals("Compound", ignoreCase = true)
        return when (goal) {
            TrainingGoal.HYPERTROPHY ->
                if (compound) SetPrescription(4, 8, 12, 90) else SetPrescription(3, 10, 15, 60)
            TrainingGoal.STRENGTH ->
                if (compound) SetPrescription(5, 4, 6, 180) else SetPrescription(3, 8, 10, 90)
            TrainingGoal.ENDURANCE -> SetPrescription(3, 15, 20, 45)
        }
    }

    private val strengthCategories = setOf("strength", "powerlifting")

    private fun equipmentRank(equipment: String): Int = when (equipment.lowercase()) {
        "machine" -> 0
        "cable" -> 1
        "other" -> 2
        "dumbbell" -> 3
        "barbell", "e-z curl bar" -> 4
        "body only" -> 5
        else -> 6
    }

    private fun levelRank(level: String): Int = when (level.lowercase()) {
        "beginner" -> 0
        "intermediate" -> 1
        else -> 2
    }
}
