package com.apoorvdarshan.calorietracker.models

import org.junit.Assert.assertEquals
import org.junit.Test

class WarmupPlatesTest {
    private val kg = WorkoutWeightUnit.KG

    @Test
    fun rampsTo100KgOnABarbell() {
        val ramp = WarmupPlates.warmup(100.0, kg, barbell = true)
        assertEquals(listOf(40.0, 60.0, 80.0), ramp.map { it.weight })
        assertEquals(listOf(10, 5, 3), ramp.map { it.reps })
    }

    @Test
    fun lightBarbellWorkStartsWithTheEmptyBar() {
        assertEquals(listOf(20.0, 25.0), WarmupPlates.warmup(30.0, kg, barbell = true).map { it.weight })
    }

    @Test
    fun platesPerSide() {
        assertEquals(listOf(25.0, 15.0, 2.5), WarmupPlates.plates(105.0, kg).perSide)
        assertEquals(listOf(1.25), WarmupPlates.plates(22.5, kg).perSide)
        assertEquals(1.0, WarmupPlates.plates(23.5, kg).let { it.leftover }, 1e-9)
        assertEquals(emptyList<Double>(), WarmupPlates.plates(20.0, kg).perSide)
    }
}
