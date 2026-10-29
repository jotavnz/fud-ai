package com.apoorvdarshan.calorietracker.models

/**
 * Rough check that a day's plan fits one gym visit: time from sets and rest, total sets, and
 * weights nobody lifts. Only a heads-up the user can dismiss, never a block.
 */
object WorkoutLoadCheck {
    /** A set takes about 40 s of work plus the rest after it. */
    private const val WORK_SECONDS_PER_SET = 40
    const val LONG_SESSION_MINUTES = 120
    const val MANY_SETS = 30
    /** Above any human lift (the deadlift record is about 500 kg). */
    const val UNREALISTIC_KG = 500.0

    data class Result(val minutes: Int, val sets: Int, val unrealisticWeight: Boolean) {
        val tooMuch: Boolean get() = minutes > LONG_SESSION_MINUTES || sets > MANY_SETS || unrealisticWeight
    }

    fun check(exercises: List<PlannedExercise>, restSeconds: Int): Result {
        var seconds = 0.0
        var sets = 0
        var unrealistic = false
        exercises.forEach { exercise ->
            val timer = exercise.timer?.savedSeconds ?: 0.0
            if (exercise.isCardio || timer > 0.0) {
                seconds += if (timer > 0.0) timer else 10 * 60.0
            } else {
                sets += exercise.sets.size
                seconds += exercise.sets.size * (WORK_SECONDS_PER_SET + restSeconds.coerceAtLeast(30))
                unrealistic = unrealistic || exercise.sets.any { set ->
                    val value = set.weight.replace(',', '.').toDoubleOrNull() ?: return@any false
                    val kg = if (set.weightUnit == WorkoutWeightUnit.LBS) value * 0.4536 else value
                    kg > UNREALISTIC_KG
                }
            }
        }
        return Result(minutes = (seconds / 60).toInt(), sets = sets, unrealisticWeight = unrealistic)
    }
}
