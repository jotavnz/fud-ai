package com.apoorvdarshan.calorietracker.models

/**
 * Warm-up ramp to a working weight and the plates per side of a barbell. Standard gym
 * plates; anything that can't be made exactly is reported as the leftover.
 */
object WarmupPlates {
    data class WarmupSet(val percent: Int, val weight: Double, val reps: Int)

    data class Loading(val perSide: List<Double>, val leftover: Double)

    private val kgPlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    private val lbPlates = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    fun barWeight(unit: WorkoutWeightUnit): Double = if (unit == WorkoutWeightUnit.KG) 20.0 else 45.0

    /** 40 % × 10, 60 % × 5, 80 % × 3 (the empty bar first when that is lighter), rounded to what can be loaded. */
    fun warmup(target: Double, unit: WorkoutWeightUnit, barbell: Boolean): List<WarmupSet> {
        if (target <= 0) return emptyList()
        val step = if (unit == WorkoutWeightUnit.KG) 2.5 else 5.0
        val floor = if (barbell) barWeight(unit) else 0.0
        return listOf(40 to 10, 60 to 5, 80 to 3).map { (pct, reps) ->
            val raw = target * pct / 100
            val rounded = (Math.round(raw / step) * step).coerceAtLeast(floor)
            WarmupSet(pct, rounded, reps)
        }.filter { it.weight < target }.distinctBy { it.weight }
    }

    /** Plates for one side of the bar, heaviest first. */
    fun plates(target: Double, unit: WorkoutWeightUnit, bar: Double = barWeight(unit)): Loading {
        var side = (target - bar) / 2
        if (side <= 0) return Loading(emptyList(), 0.0)
        val out = mutableListOf<Double>()
        for (plate in if (unit == WorkoutWeightUnit.KG) kgPlates else lbPlates) {
            while (side + 1e-9 >= plate) {
                out += plate
                side -= plate
            }
        }
        return Loading(out, (side * 2).let { if (it < 1e-6) 0.0 else it })
    }
}
