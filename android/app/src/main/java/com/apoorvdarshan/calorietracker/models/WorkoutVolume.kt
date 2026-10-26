package com.apoorvdarshan.calorietracker.models

/**
 * Lifting volume from logged strength sets (load in kg × reps), shared by the training
 * insights and the year-in-review summary. A set counts as performed once it has reps;
 * sets without a load (bodyweight, blank weight) count as performed but add no volume.
 */
object WorkoutVolume {
    private const val POUNDS_PER_KILOGRAM = 2.204_622_621_8

    /** Logged reps, or null for a set that was not performed (or holds an implausible value). */
    fun reps(set: PlannedSet): Int? = set.reps.trim().toIntOrNull()?.takeIf { it in 1..100 }

    /** The set's load in kilograms, honoring its own unit before the diary default. */
    fun loadKg(set: PlannedSet, defaultUnit: WorkoutWeightUnit): Double? {
        val value = set.weight.trim().replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 && it <= 1_500.0 }
            ?: return null
        val unit = set.weightUnit ?: defaultUnit
        return if (unit == WorkoutWeightUnit.KG) value else value / POUNDS_PER_KILOGRAM
    }

    fun setVolumeKg(set: PlannedSet, defaultUnit: WorkoutWeightUnit): Double {
        val reps = reps(set) ?: return 0.0
        val load = loadKg(set, defaultUnit) ?: return 0.0
        return load * reps
    }

    fun performedSets(exercise: PlannedExercise): List<PlannedSet> =
        exercise.sets.filter { reps(it) != null }

    /** Cardio and timed entries carry no lifting volume even if reps were noted. */
    fun exerciseVolumeKg(exercise: PlannedExercise, defaultUnit: WorkoutWeightUnit): Double =
        if (exercise.isCardio) 0.0 else exercise.sets.sumOf { setVolumeKg(it, defaultUnit) }

    fun dayVolumeKg(plan: WorkoutDayPlan, defaultUnit: WorkoutWeightUnit): Double =
        plan.exercises.sumOf { exerciseVolumeKg(it, defaultUnit) }

    /** Heaviest performed load for the exercise in kg, or null when none was logged. */
    fun topLoadKg(exercise: PlannedExercise, defaultUnit: WorkoutWeightUnit): Double? =
        performedSets(exercise).mapNotNull { loadKg(it, defaultUnit) }.maxOrNull()
}
