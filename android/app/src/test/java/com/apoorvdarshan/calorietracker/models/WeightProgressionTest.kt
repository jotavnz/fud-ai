package com.apoorvdarshan.calorietracker.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightProgressionTest {
    private fun day(key: String, vararg sets: Pair<String, String>) =
        ExerciseLiftDay(key, sets.map { (w, r) -> ExerciseLiftSet(w, WorkoutWeightUnit.KG, r) })

    private val kg = WorkoutWeightUnit.KG

    @Test
    fun everySetAtTheTopOfTheRangeAddsWeight() {
        val advice = WeightProgression.advise(listOf(day("2026-10-27", "40" to "12", "40" to "12", "40" to "12")), kg)
        assertEquals(WeightProgression.Advice.Increase("42.5", kg), advice)
    }

    @Test
    fun lightDumbbellsGoUpByOneKiloAndStrengthTopsOutAtSix() {
        assertEquals(WeightProgression.Advice.Increase("11", kg), WeightProgression.advise(listOf(day("d", "10" to "12", "10" to "13")), kg))
        assertEquals(WeightProgression.Advice.Increase("102.5", kg), WeightProgression.advise(listOf(day("d", "100" to "6", "100" to "6")), kg))
    }

    @Test
    fun shortOfTheTopRepeatsTheWeight() {
        assertEquals(WeightProgression.Advice.Repeat("40", kg), WeightProgression.advise(listOf(day("d", "40" to "12", "40" to "10")), kg))
    }

    @Test
    fun threeSessionsWithoutProgressSuggestADeload() {
        val history = listOf(
            day("2026-10-27", "60" to "9", "60" to "8"),
            day("2026-10-24", "60" to "9", "60" to "9"),
            day("2026-10-21", "60" to "10", "60" to "9")
        )
        assertEquals(WeightProgression.Advice.Deload("55", kg), WeightProgression.advise(history, kg))
    }

    @Test
    fun bodyweightOnlyHasNoAdvice() {
        assertNull(WeightProgression.advise(listOf(day("d", "" to "15")), kg))
    }
}
