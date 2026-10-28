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
 * Muscle groups a lost gym-goer can pick from. [muscles] are catalog primary
 * muscle titles; [curatedIds] are the machines most gyms have, best first, so the first
 * three suggestions are recognizable equipment rather than rare variations. The ids are
 * exercises-dataset ones; scripts/exercise-id-migration.manual.json names the machine each replaced.
 */
enum class MachineMuscleGroup(
    @StringRes val titleRes: Int,
    /** Catalog muscle whose glyph represents the group. */
    val glyphMuscle: String,
    val muscles: Set<String>,
    val curatedIds: List<String>
) {
    CHEST(
        R.string.machine_coach_group_chest, "Chest", setOf("Chest"),
        listOf(
            "ed_0577", "ed_0596", "ed_0227",
            "ed_1299", "ed_0748", "ed_0179"
        )
    ),
    BACK(
        R.string.machine_coach_group_back, "Lats", setOf("Lats", "Middle Back"),
        listOf(
            "ed_2330", "ed_0861", "ed_0581",
            "ed_2616", "ed_0238", "ed_0606"
        )
    ),
    SHOULDERS(
        R.string.machine_coach_group_shoulders, "Shoulders", setOf("Shoulders"),
        listOf(
            "ed_0587", "ed_0178", "ed_0602",
            "ed_0203", "ed_0603", "ed_0162"
        )
    ),
    BICEPS(
        R.string.machine_coach_group_biceps, "Biceps", setOf("Biceps"),
        listOf(
            "ed_0575", "ed_0592", "ed_0868",
            "ed_0165", "ed_0195", "ed_1636"
        )
    ),
    TRICEPS(
        R.string.machine_coach_group_triceps, "Triceps", setOf("Triceps"),
        listOf(
            "ed_0200", "ed_0607", "ed_1451",
            "ed_0201", "ed_0194", "ed_0207"
        )
    ),
    LEGS(
        R.string.machine_coach_group_legs, "Quadriceps", setOf("Quadriceps"),
        listOf(
            "ed_0739", "ed_0585", "ed_0743",
            "ed_0770", "ed_0760", "ed_2611"
        )
    ),
    HAMSTRINGS(
        R.string.machine_coach_group_hamstrings, "Hamstrings", setOf("Hamstrings"),
        listOf(
            "ed_0599", "ed_0586", "ed_0582",
            "ed_3193", "ed_0752", "ed_0593"
        )
    ),
    GLUTES(
        R.string.machine_coach_group_glutes, "Glutes", setOf("Glutes", "Abductors"),
        listOf(
            "ed_3562", "ed_0597", "ed_0860",
            "ed_0196", "ed_1409", "ed_0598"
        )
    ),
    CALVES(
        R.string.machine_coach_group_calves, "Calves", setOf("Calves"),
        listOf(
            "ed_0605", "ed_0594", "ed_1391",
            "ed_0773", "ed_2289"
        )
    ),
    ABS(
        R.string.machine_coach_group_abs, "Abdominals", setOf("Abdominals"),
        listOf(
            "ed_1452", "ed_0175", "ed_0230",
            "ed_0979", "ed_0874", "ed_0873"
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
