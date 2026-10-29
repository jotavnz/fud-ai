package com.apoorvdarshan.calorietracker.models

import java.util.Locale

/**
 * Double progression from the user's own history: reach the top of the rep range on every set,
 * then add a little weight; three sessions stuck at the same weight suggest a lighter week.
 */
object WeightProgression {
    sealed interface Advice {
        /** All sets hit the top of the range: go up to [weight]. */
        data class Increase(val weight: String, val unit: WorkoutWeightUnit) : Advice
        /** Same weight, aim for one more rep. */
        data class Repeat(val weight: String, val unit: WorkoutWeightUnit) : Advice
        /** Three sessions without progress: about 10 % lighter this time. */
        data class Deload(val weight: String, val unit: WorkoutWeightUnit) : Advice
    }

    private data class Day(val weight: Double, val reps: List<Int>)

    /** [history] newest first, as [ExerciseLiftHistory.history] returns it. */
    fun advise(history: List<ExerciseLiftDay>, unit: WorkoutWeightUnit): Advice? {
        val days = history.mapNotNull { day -> topWeightDay(day, unit) }
        val last = days.firstOrNull() ?: return null
        // Strength work (low reps) tops out at 6, hypertrophy at 12.
        val top = if (last.reps.average() <= 6.5) 6 else 12
        if (last.reps.all { it >= top }) return Advice.Increase(format(last.weight + step(last.weight, unit)), unit)
        val sameWeight = days.take(3).takeIf { it.size == 3 && it.all { d -> d.weight == last.weight } }
        if (sameWeight != null) {
            val (newest, middle, oldest) = sameWeight.map { it.reps.sum() }
            if (newest <= middle && middle <= oldest) return Advice.Deload(format(roundTo(last.weight * 0.9, step(last.weight, unit))), unit)
        }
        return Advice.Repeat(format(last.weight), unit)
    }

    /** The day's heaviest weight in [unit] and the reps done with it; null for bodyweight-only days. */
    private fun topWeightDay(day: ExerciseLiftDay, unit: WorkoutWeightUnit): Day? {
        val sets = day.sets.mapNotNull { set ->
            val reps = set.reps.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val weight = PlannedSet(weight = set.weight, weightUnit = set.weightUnit, reps = set.reps)
                .displayWeight(unit).replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            weight to reps
        }
        val top = sets.maxOfOrNull { it.first } ?: return null
        return Day(top, sets.filter { it.first == top }.map { it.second })
    }

    /** 2.5 kg (1 kg under 20 kg, for small dumbbells), or 5 lbs (2.5 under 40). */
    private fun step(weight: Double, unit: WorkoutWeightUnit): Double = when (unit) {
        WorkoutWeightUnit.KG -> if (weight < 20) 1.0 else 2.5
        WorkoutWeightUnit.LBS -> if (weight < 40) 2.5 else 5.0
    }

    private fun roundTo(value: Double, step: Double): Double = Math.round(value / step) * step

    private fun format(value: Double): String =
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}
